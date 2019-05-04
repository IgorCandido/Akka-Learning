package Supervisor

import Supervisor.BackoffSupervisorPattern.{EchoActor, system}
import akka.actor.{Actor, ActorLogging, ActorSystem, PoisonPill, Props, SupervisorStrategy}
import akka.pattern.{BackoffOpts, BackoffSupervisor}

import scala.concurrent.duration._
import scala.io.StdIn

object BackoffSupervisorPattern {
  val system = ActorSystem("testBackoffSupervisor")

  object EchoActor {
    def props = Props(new EchoActor)
  }

  class EchoActor extends Actor with ActorLogging {
    override def preStart(): Unit = {
      log.info("Starting")
      super.preStart()
    }

    override def preRestart(reason: Throwable, message: Option[Any]): Unit = {
      log.info("Restarting")
      super.preRestart(reason, message)
    }

    override def postStop(): Unit = {
      log.info("Stopping")
      super.postStop()
    }

    override def receive: Receive = {
      case "T" => throw new Exception("Dummy")
    }
  }
}

object Main extends App {
  val supervisor = BackoffSupervisor.props(
    BackoffOpts.onStop(
      EchoActor.props,
      childName = "Echo",
      minBackoff = 3 seconds,
      maxBackoff = 30 seconds,
      randomFactor = 0.2
    )
  )

  val echo = system.actorOf(supervisor, "echoSupervisor")

  println("Waiting...")
  StdIn.readLine()
  echo ! "T"
  println("Sending poison Pill")
  StdIn.readLine()
  echo ! "T"
  println("Sending poison Pill")
  StdIn.readLine()
  echo ! "T"
  println("Sending poison Pill")
  StdIn.readLine()
  system.terminate()
}
