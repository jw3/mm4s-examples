package mm4s.examples.status

import java.util.UUID

import akka.actor.{Actor, ActorLogging, ActorRef}
import mm4s.api.{Post, Posted}
import mm4s.bots.api.{Bot, BotID, Ready}
import mm4s.examples.status.StatusBot._
import net.codingwell.scalaguice.ScalaModule

import scala.collection.mutable


class StatusBot extends Actor with Bot with ActorLogging {
  def receive: Receive = {
    case Ready(api, id) => context.become(ready(api, id))
  }

  def ready(api: ActorRef, id: BotID): Receive = {
    val jobs = mutable.Map[String, Job]()

    log.debug("StatusBot [{}] ready", id.username)
    api ! Post("StatusBot ready!")

    {
      case Posted(t) if t.startsWith("@status") =>
        log.debug("{} received {}", self.path.name, t)

        t match {
          case rmock(t) => context.self ! JobRequest(t.toLong * 1000)

          case rcheck(uid) =>
            jobs.get(uid) match {
              case Some(j) => api ! Post(s"Job `$uid` is ${jobPct(j)}% complete")
              case None => api ! Post(s"unknown job `$uid`")
            }

          case rdone(uid) =>
            jobs.get(uid) match {
              case Some(j) => api ! Post(s"`${jobDone(j)}`")
              case None => api ! Post(s"unknown job `$uid`")
            }

          case rlist() =>
            val t = System.currentTimeMillis()
            val status = jobs.foldLeft(
              """
                || ID | Status |
                ||------|------|""".stripMargin) {
              (acc, e) =>
                acc ++ s"\n|${e._1}|${jobPct(e._2, t)}%|"
            }
            api ! Post(status)

          case _ => api ! Post(s"Sorry I don't understand `$t`, try `help`")
        }

      case JobRequest(l) =>
        val id = jobId()
        val start = System.currentTimeMillis()
        jobs(id) = Job(id, l, start + l)
        api ! Post(s"job started, `$id`")
    }
  }
}

object StatusBot {
  val rmock = """mock\s*?(\d+)""".r.unanchored
  val rcheck = """check\s*?(\w+)""".r.unanchored
  val rdone = """isdone\s*?(\w+)""".r.unanchored
  val rlist = """@status list$""".r.anchored

  case class JobRequest(t: Long)
  case class Job(id: String, length: Long, stop: Long)

  def jobId() = UUID.randomUUID().toString.take(5)
  def jobDone(j: Job) = j.stop < System.currentTimeMillis()
  def jobPct(j: Job, curr: Long = System.currentTimeMillis()) = {
    if (jobDone(j)) 100
    else 100 - ((Math.abs(curr - j.stop) / j.length.toDouble) * 100).toInt
  }
}

class StatusBotModule extends ScalaModule {
  def configure() = bind[Bot].to[StatusBot]
}

object StatusBotBoot4dev extends App {
  mm4s.bots.Boot.main(Array.empty)
}
