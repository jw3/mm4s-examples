package mm4s.examples.multichan

import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.pattern.ask
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink
import akka.util.Timeout
import mm4s.api.ChannelModels.{Channel, ChannelListing}
import mm4s.api.ChannelProtocols._
import mm4s.api.Streams._
import mm4s.api._
import mm4s.bots.api.{Bot, BotID, ConnectionRequest, Ready}
import net.codingwell.scalaguice.ScalaModule

import scala.concurrent.duration.DurationInt


class MultiChanBot extends Actor with Bot with ActorLogging {
  import MultiChanBot._

  def receive: Receive = {
    case Ready(api, id) => context.become(connecting(api, id))
  }

  def connecting(api: ActorRef, id: BotID): Receive = {
    import context.dispatcher
    implicit val mat = ActorMaterializer()
    implicit val timeout = Timeout(10.seconds)

    val f = api ? ConnectionRequest()
    f.mapTo[Connection]
    .flatMap { conn =>
      import context.system

      Channels.__list
      .via(conn)
      .via(response[ChannelListing])
      .map(_.channels)
      .runWith(Sink.head)
    }.foreach(c => context.become(ready(api, id, c)))

    {
      case _ =>
        log.debug("MultiChanBot [{}] not ready, still connecting", id.username)
    }
  }

  def ready(api: ActorRef, botid: BotID, chs: Seq[Channel]): Receive = {
    log.debug("MultiChanBot [{}] ready", botid.username)
    api ! PostWithChannel("MultiChanBot ready!", chs.head.id)
    api ! PostWithChannel(listChannels(chs), chs.head.id)

    {
      case Posted(t) if t.startsWith("@msg") =>
        log.debug("{} received {}", self.path.name, t)

        t match {
          case msg2chan(msg, ch) =>
            chs.collectFirst { case c if c.name == ch | c.id == ch => c.id } match {
              case Some(id) =>
                api ! PostWithChannel(msg, id)
              case None =>
                api ! PostWithChannel(s"Invalid channel [$ch]", chs.head.id)
            }

          case _ =>
            log.debug("couldnt match [{}]", t)
        }
    }
  }
}

object MultiChanBot {
  val msg2chan = """@msg\s*?\"(.+?)\"\s*?->\s*?\"(.+?)\"\s*?""".r.unanchored

  def listChannels(channels: Seq[Channel]) = {
    channels.foldLeft(
      """
        || ID | Name | Display |
        ||------|------|------|""".stripMargin) {
      (acc, c) =>
        acc ++ s"\n|${c.id}|${c.name}|${c.displayName}|"
    }
  }
}

class MultiChanBotModule extends ScalaModule {
  def configure() = bind[Bot].to[MultiChanBot]
}

object MultiChanBotBoot4dev extends App {
  mm4s.bots.Boot.main(Array.empty)
}
