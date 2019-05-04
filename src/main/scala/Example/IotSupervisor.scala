package Example

import Example.DeviceGroupQuery.CollectionTimeout
import Example.DeviceManager.{DeviceNotAvailable, RequestTrackDevice, Temperature, TemperatureNotAvailable, TemperatureReading}
import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, PoisonPill, Props, Terminated}

import scala.concurrent.duration.FiniteDuration
import scala.io.StdIn

object IotSupervisor {
  def props: Props = Props(new IotSupervisor)
}

class IotSupervisor extends Actor with ActorLogging {
  override def preStart(): Unit = log.info("IoT Application Starting")
  override def postStop(): Unit = log.info("IoT Application Stopped")

  override def receive: Receive = Actor.emptyBehavior
}

object IoTApp {

  def main(args: Array[String]): Unit = {
    val system = ActorSystem("IoT-System")

    try {
      val supervisor = system.actorOf(IotSupervisor.props, "iot-supervisor")
      StdIn.readLine()
    } finally {
      system.terminate()
    }
  }

}

object DeviceGroupQuery {
  def props(actorToDeviceId: Map[ActorRef, String],
            requestId: Long,
            requester: ActorRef,
            timeout: FiniteDuration) =
    Props(new DeviceGroupQuery(actorToDeviceId, requestId, requester, timeout))

  case object CollectionTimeout
}

class DeviceGroupQuery(actorToDeviceId: Map[ActorRef, String],
                       requestId: Long,
                       requester: ActorRef,
                       timeout: FiniteDuration)
    extends Actor
    with ActorLogging {
  import DeviceGroupQuery._
  import context.dispatcher

  val queryTimeoutTimer =
    context.system.scheduler.scheduleOnce(timeout, self, CollectionTimeout)

  override def preStart(): Unit = {
    actorToDeviceId.keysIterator.foreach({ deviceActor =>
      context.watch(deviceActor)
      deviceActor ! Device.ReadTemperature(0)
    })
  }

  override def postStop(): Unit = {
    queryTimeoutTimer.cancel()
  }

  def receivedResponse(
    deviceActor: ActorRef,
    reading: DeviceManager.TemperatureReading,
    stillWaiting: Set[ActorRef],
    repliesSoFar: Map[String, DeviceManager.TemperatureReading]
  ) = {
    context.unwatch(deviceActor)
    val deviceId = actorToDeviceId(deviceActor)
    val newStilWaiting = stillWaiting - deviceActor

    val newRepliesSoFar = repliesSoFar + (deviceId -> reading)
    if(newStilWaiting.isEmpty){
      requester ! DeviceManager.RespondAllTemperatures(requestId, newRepliesSoFar)
      context.stop(self)
    } else{
      context.become(waitingForReplies(newRepliesSoFar, newStilWaiting))
    }
  }

  def waitingForReplies(repliesSoFar: Map[String, TemperatureReading],
                        stillWaiting: Set[ActorRef]): Receive = {
    case Device.RespondTemperature(0, valueOption) =>
      val deviceActor = sender()
      val reading = valueOption match {
        case Some(value) => Temperature(value)
        case None        => TemperatureNotAvailable
      }
      receivedResponse(deviceActor, reading, stillWaiting, repliesSoFar)

    case Terminated(deviceActor) =>
      receivedResponse(
        deviceActor,
        DeviceNotAvailable,
        stillWaiting,
        repliesSoFar
      )

    case CollectionTimeout =>
      val timedOutReplies =
        stillWaiting.map { deviceActor =>
          val deviceId = actorToDeviceId(deviceActor)
          deviceId -> DeviceManager.DeviceTimedOut
        }
      requester ! DeviceManager.RespondAllTemperatures(
        requestId,
        repliesSoFar ++ timedOutReplies
      )
      context.stop(self)
  }

  override def receive: Receive =
    waitingForReplies(Map.empty, actorToDeviceId.keySet)
}
