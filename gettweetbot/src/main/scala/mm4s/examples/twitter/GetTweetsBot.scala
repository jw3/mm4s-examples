package mm4s.examples.twitter

import java.io.File
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger

import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.stream.scaladsl._
import akka.stream.{ActorMaterializer, OverflowStrategy}
import akka.util.ByteString
import mm4s.api.{Post, PostWithAttachment, Posted}
import mm4s.bots.api.{Bot, BotID, Ready}
import mm4s.examples.twitter.GetTweetsBot.{TweetQueue, _}
import net.codingwell.scalaguice.ScalaModule
import spray.json.{DefaultJsonProtocol, RootJsonFormat, _}
import twitter4j._

/**
 * Bot that can perform some basic tweet fetching
 */
class GetTweetsBot extends Actor with Bot with ActorLogging {
  implicit val mat: ActorMaterializer = ActorMaterializer()


  def receive: Receive = {
    case Ready(api, id) => context.become(ready(api, id))
  }

  def ready(api: ActorRef, id: BotID): Receive = {
    import GetTweetsBot._
    import TweetProtocol._


    log.debug("GetTweetsBot [{}] ready", id.username)
    api ! Post("GetTweetsBot ready")

    {
      case Posted(t) if t.startsWith("@tweets") =>
        log.debug("{} received {}", self.path.name, t)

        val file = Files.createTempFile("tweets", "bot").toFile
        val sink = FileIO.toFile(file)

        val limit = parseLimit(t)
        val terms = parseTerms(t)
        log.debug("logging {} tweets about {} to {}", limit, terms, file.getPath)

        val queue = Source.queue[Option[Status]](100, OverflowStrategy.dropTail)
                    .takeWhile(_.isDefined)
                    .map(_.get)
                    .map(Tweet(_).toJson.compactPrint)
                    .map(s => ByteString(s"$s\n"))
                    .to(sink)
                    .run()

        val stream = factory.getInstance()
        stream.addListener(new StatusForwarder(context.self, file, stream, queue, limit))
        if (terms.isEmpty) stream.sample() else stream.filter(new FilterQuery(terms: _*))

      case Upload(f) if f.exists() =>
        log.debug("uploading {}", f)
        api ! PostWithAttachment("GetTweetsBot ready", f.toPath)
    }
  }

  override def preStart() = log.debug("GetTweetsBot starting")
}

object GetTweetsBot {
  type TweetQueue = SourceQueue[Option[Status]]

  val defaultLimit = 10
  val rlimit = """limit\s*?(\d+)""".r.unanchored
  val rterms = """terms "([\w]+[\s*,\s*\w]*)"""".r.unanchored
  val factory = new TwitterStreamFactory()

  case class Upload(file: File)

  def parseLimit(t: String) = t match {
    case s if s.contains("limit") => s match {
      case rlimit(c) => c.toInt
      case _ => 0
    }
    case _ => defaultLimit
  }

  def parseTerms(t: String) = t match {
    case s if s.contains("terms") => s match {
      case rterms(terms) => terms.split(",").map(_.trim).toSeq
      case _ => Seq.empty
    }
    case _ => Seq.empty
  }
}

class GetTweetsBotModule extends ScalaModule {
  def configure() = bind[Bot].to[GetTweetsBot]
}

case class Tweet(id: Long, text: String, time: Long, geo: Option[Location])
object Tweet {
  def apply(status: Status): Tweet = {
    Tweet(
      status.getId,
      status.getText,
      status.getCreatedAt.getTime,
      Location(status.getGeoLocation)
    )
  }
}
object TweetProtocol extends DefaultJsonProtocol {
  implicit val geoFormat: RootJsonFormat[Location] = jsonFormat2(Location.apply)
  implicit val tweetFormat: RootJsonFormat[Tweet] = jsonFormat4(Tweet.apply)
}

case class Location(lat: Double, lon: Double)
object Location {
  def apply(geo: GeoLocation): Option[Location] = {
    Option(geo).map(g => Location(g.getLatitude, g.getLongitude))
  }
}
/**
 *
 *
 */
class StatusForwarder(ref: ActorRef, file: File, stream: TwitterStream, queue: TweetQueue, limit: Int) extends StatusAdapter {
  val counter = new AtomicInteger

  override def onStatus(status: Status) = {
    val count = counter.getAndIncrement()
    if (count < limit) queue.offer(Option(status))
    else {
      queue.offer(None)
      stream.removeListener(this)
      stream.cleanUp()
      ref ! Upload(file)
    }
  }
}
