package mm4s.examples.proxy

import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.http.scaladsl.server.Directives._
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.{Flow, Sink, Source}
import mm4s.api.{Post, Posted}
import mm4s.bots.api.{Bot, BotID, Ready}
import net.codingwell.scalaguice.ScalaModule
import rxthings.webhooks.ActorWebApi
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

/**
 * Example of a bot acting as a pass-through for a third party ws consumer
 */
class ProxyBot extends Actor with Bot with ActorWebApi with ActorLogging {
  override def config() = Option(actorSystem.settings.config)

  def receive: Receive = {
    case Ready(api, id) => context.become(ready(api, id))
  }

  def ready(api: ActorRef, bid: BotID): Receive = {
    log.debug("ProxyBot [{}] ready, start HTTP", bid.username)
    webstart(routes(self))

    {
      case WsConnected(id, ws) => context.become(connected(api, ws, id))
    }
  }

  def connected(api: ActorRef, ws: ActorRef, id: String): Receive = {
    log.debug("ProxyBot [{}] connected", id)

    {
      // incoming from http client, out to mm
      case m: PostingDetails =>
        println(s"${m.message} <<from client to mm>>")
        api ! Post(m.message)

      // incoming from http client ws, out to mm
      case TextMessage.Strict(t) =>
        println(s"$t <<from http ws>>")
        api ! Post(t)

      // incoming from mm, out to proxy client
      case Posted(t) =>
        ws ! TextMessage(s"$t <<from mm ws to proxy client>>")

      case WsDisconnected(x) =>
        println(s"proxy-client disconnected $x")
        api ! Post(s"proxy-client disconnected $x")
    }
  }


  def ws(id: String) = {
    val source = Source.actorRef[Message](bufferSize = 5, OverflowStrategy.fail)
                 .mapMaterializedValue(a => context.self ! WsConnected(id, a))
    val sink = Sink.actorRef(context.self, WsDisconnected(id))

    Flow.fromSinkAndSource(sink, source)
  }

  case class WsConnected(id: String, ref: ActorRef)
  case class WsDisconnected(id: String)

  case class PostingDetails(username: String, channel: String, message: String)
  object PostingDetailsProtocol extends DefaultJsonProtocol {
    implicit val postingFormat: RootJsonFormat[PostingDetails] = jsonFormat3(PostingDetails)
  }

  def routes(a: ActorRef) = {
    // the proxy client connects to
    (get & pathPrefix("ws" / Segment)) { id =>
      handleWebSocketMessages(ws(id))
    } ~
    // the proxy client can post restful messages to default channel
    (post & path("/msg" / Segment)) { s =>
      a ! s
      complete(StatusCodes.OK)
    } ~
    // the proxy client can post restful messages to alternate channels
    (post & path("/msg")) {
      import PostingDetailsProtocol._
      import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._

      entity(as[PostingDetails]) { m =>
        a ! m
        complete(StatusCodes.OK)
      }
    }
  }
}

class ProxyBotModule extends ScalaModule {
  def configure() = bind[Bot].to[ProxyBot]
}

object ProxyBotBoot4dev extends App {
  mm4s.bots.Boot.main(Array.empty)
}
