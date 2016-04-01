package mm4s.examples.consulbot

import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.stream.ActorMaterializer
import consulq.ConsulQuery
import mm4s.api.{Post, Posted}
import mm4s.bots.api.{Bot, BotID, Ready}
import net.codingwell.scalaguice.ScalaModule


/**
 * Bot that interacts with a the Consul service
 */
class ConsulBot extends Actor with Bot with ActorLogging {
  import context.dispatcher
  implicit val mat: ActorMaterializer = ActorMaterializer()


  def receive: Receive = {
    case Ready(api, id) => context.become(ready(api, id))
  }

  def ready(api: ActorRef, id: BotID): Receive = {
    import context.system
    log.debug("ConsulBot [{}] ready", id.username)
    api ! Post("ConsulBot ready")

    {
      case Posted(t) if t.startsWith("@consul") =>
        log.debug("{} received {}", self.path.name, t)

        ConsulQuery().services().map { s =>
          s.foldLeft(
            """
              || name | address | port |
              ||------|--------|--------|""".stripMargin) {
            (acc, s) => acc ++ s"\n|${s.name}|${s.address}|${s.port}|"
          }
        }.foreach(api ! Post(_))
    }
  }

  override def preStart(): Unit = {
    log.debug("ConsulBot starting")
  }
}


class ConsulBotModule extends ScalaModule {
  def configure(): Unit = {
    bind[Bot].to[ConsulBot]
  }
}
