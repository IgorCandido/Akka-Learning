package Peristence

import Peristence.AkkaPersistent.Cmd
import akka.actor.{ActorSystem, Props}
import akka.persistence.{PersistentActor, SnapshotOffer}

import scala.io.StdIn

object AkkaPersistent{
  def props = Props(new AkkaPersistent)

  case class Cmd(data: String)
  case class Evt(data: String)

  case class State(events: List[String] = Nil){
    def updated(evt: Evt): State = State(evt.data :: events)
    def size: Int = events.length

    override def toString: String = events.reverse.toString
  }
}

class AkkaPersistent extends PersistentActor{
  import AkkaPersistent._
  var state: State = State()

  override def persistenceId: String = "persistenceExample-id-1"

  def updateState(event: Evt): Unit =
    state = state.updated(event)

  def numEvents =
    state.size

  override def receiveRecover: Receive = {
    case evt: Evt => updateState(evt)
    case SnapshotOffer(_, snapshot: State) => state = snapshot
  }

  val snapShotInterval = 1000
  override def receiveCommand: Receive = {
    case Cmd(data) =>
      persist(Evt(s"${data}-${numEvents}")) { event =>
        updateState(event)
        context.system.eventStream.publish(event)
        if(lastSequenceNr % snapShotInterval == 0 && lastSequenceNr != 0)
          saveSnapshot(state)
      }

      defer(Evt(s"${data}-${numEvents} In Memory")) {event =>
        println(s"${event} is being handled deferred in memory")
      }
    case "print" => println(state)
  }
}

object example extends App{
  val system = ActorSystem("persistenceTest")

  val persistent = system.actorOf(AkkaPersistent.props, "persistent")

  persistent ! Cmd("test")
  persistent ! Cmd("test 1")
  persistent ! "print"

  StdIn.readLine()
  system.terminate()
}

// todo At-Least-Once Example
