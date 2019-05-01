package Example

import akka.actor.{Actor, ActorRef, ActorSystem, Props}

import scala.io.StdIn
import scala.util.{Failure, Try}

object SecondActor {
  def props: Props = Props(new SecondActor)
}

class SecondActor extends Actor {
  override def receive: Receive = {
    case "print" =>
      println(self)
    case "fail" =>
      println("failing")
      throw new Exception("Failure")
  }

  override def postStop(): Unit = println("second stopped")
  override def preStart(): Unit = println("second started")
}

object PrintRefActor {
  def props : Props = Props(new PrintRefActor)
}


class PrintRefActor extends Actor{
  var secondRef: Option[ActorRef] = None

  override def receive: Receive = {
    case "printit" => {
      secondRef = Some(context.actorOf(SecondActor.props, "second-actor"))
      println(s"Second: $secondRef")
      secondRef.foreach(_  ! "print")
    }
    case "stop" =>
      println("Stopping self")
      context.stop(self)

    case "failSon" =>
      println("failing son")
      secondRef.foreach(_ ! "fail")
  }

  override def postStop(): Unit = println("first stopped")

  override def preStart(): Unit = println("first started")
}

object AkkaCreations extends App{
  val system = ActorSystem("testSystem")

  val firstRef = system.actorOf(PrintRefActor.props)
  println(s"First: $firstRef")

  firstRef ! "printit"

  println(">>> Press ENTER to exit <<<")
  Try{
    StdIn.readLine()
    firstRef ! "failSon"
    StdIn.readLine()
  } match {
    case _ => system.terminate()
  }
}
