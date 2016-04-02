package mm4s.examples.twitter

import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger

import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.stream.scaladsl._
import akka.stream.{ActorMaterializer, OverflowStrategy}
import akka.util.ByteString
import mm4s.api.{Post, Posted}
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
    import TweetProtocol._

    log.debug("GetTweetsBot [{}] ready", id.username)
    api ! Post("GetTweetsBot ready")

    {
      case Posted(t) if t.startsWith("@tweets") =>
        log.debug("{} received {}", self.path.name, t)

        val limit = t match {
          case s if s.contains("limit") => s match {
            case rlimit(c) => c.toInt
            case _ =>
              log.warning("failed to parse limit string from [{}]", t)
              0
          }
          case _ => defaultLimit
        }

        val file = Files.createTempFile("tweets", "bot").toFile
        val sink = FileIO.toFile(file)
        log.debug("logging {} tweets to {}", limit, file.getPath)

        val queue = Source.queue[Option[Status]](100, OverflowStrategy.dropTail)
                    .takeWhile(_.isDefined)
                    .map(_.get)
                    .map(Tweet(_).toJson.compactPrint)
                    .via(Flow[String].map(s => ByteString(s"$s\n")))
                    .to(sink)
                    .run()

        val stream = factory.getInstance()
        stream.addListener(new StatusForwarder(stream, queue, limit))
        stream.filter(new FilterQuery("espn"))
    }
  }

  override def preStart() = log.debug("GetTweetsBot starting")
}

object GetTweetsBot {
  type TweetQueue = SourceQueue[Option[Status]]

  val defaultLimit = 10
  val rlimit = """limit\s*?(\d+)""".r.unanchored
  val factory = new TwitterStreamFactory()
}

class GetTweetsBotModule extends ScalaModule {
  def configure() = bind[Bot].to[GetTweetsBot]
}

case class Tweet(text: String, time: Long)
object Tweet {
  def apply(status: Status): Tweet = {
    Tweet(status.getText, status.getCreatedAt.getTime)
  }
}
object TweetProtocol extends DefaultJsonProtocol {
  implicit val tweetFormat: RootJsonFormat[Tweet] = jsonFormat2(Tweet.apply)
}

/**
 *
 *
 */
class StatusForwarder(stream: TwitterStream, queue: TweetQueue, limit: Int) extends StatusAdapter {
  val counter = new AtomicInteger

  override def onStatus(status: Status) = {
    val count = counter.getAndIncrement()
    if (count < limit) queue.offer(Option(status))
    else {
      queue.offer(None)
      stream.removeListener(this)
      stream.cleanUp()
    }
  }
}