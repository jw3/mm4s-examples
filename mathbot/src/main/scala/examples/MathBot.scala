package examples

import akka.actor.{Actor, ActorLogging, ActorRef}
import mm4s.api.{Post, Posted}
import mm4s.bots.api.{Bot, BotID, Ready}
import net.codingwell.scalaguice.ScalaModule


/**
 * Basic math performing bot
 */
class MathBot extends Actor with Bot with ActorLogging {
  import MathBot._

  def receive: Receive = {
    case Ready(api, id) => context.become(ready(api, id))
  }

  def ready(api: ActorRef, id: BotID): Receive = {
    log.debug("MathBot [{}] ready", id.username)
    api ! Post("Im ready!")

    {
      case Posted(t) if t.startsWith("@math") =>
        log.debug("{} received {}", self.path.name, t)
        t match {
          case add(lhs, rhs) =>
            val r = lhs.toInt + rhs.toInt
            api ! Post(s"sum is $r")
          case sub(lhs, rhs) =>
            val r = lhs.toInt - rhs.toInt
            api ! Post(s"difference is $r")
          case mul(lhs, rhs) =>
            val r = lhs.toInt * rhs.toInt
            api ! Post(s"product is $r")
          case div(lhs, rhs) =>
            val r = lhs.toInt / rhs.toInt
            api ! Post(s"quotient is $r")
          case help() => api ! Post(helpmsg)
          case _ => api ! Post("Sorry I don't know what to say to that, try `help`")
        }
    }
  }

  override def preStart(): Unit = {
    log.debug("MathBot starting")
  }
}


object MathBot {
  def op(s: String) = s"""\\(\\s*?(\\d+)\\s*?\\$s\\s*?(\\d+)\\s*?\\)""".r.unanchored

  val add = op("+")
  val sub = op("-")
  val mul = op("*")
  val div = op("/")
  val help = """help$""".r.unanchored

  val helpmsg =
    """
      |Command format `(lhs operation rhs)`
      |Four supported operations
      |- `+` addition
      |- `-` subtraction
      |- `*` multiplication
      |- `/` division
    """.stripMargin
}


class MathBotModule extends ScalaModule {
  def configure(): Unit = {
    bind[Bot].to[MathBot]
  }
}

object MathBotBoot4dev extends App {
  mm4s.bots.Boot.main(Array.empty)
}
