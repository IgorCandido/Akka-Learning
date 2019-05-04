package Example

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Terminated}

object DeviceManager {
  def props = Props(new DeviceManager)

  final case class RequestTrackDevice(groupId: String, deviceId: String)
  final case object DeviceRegistered

  final case class RequestListOfDeviceGroups(requestId: Int)
  final case class ListOfDeviceGroups(requestId: Int, groupIds: Set[String])

  final case class RequestAllTemperatures(requestId: Long)
  final case class RespondAllTemperatures(
                                           requestId: Long,
                                           temperatures: Map[String, TemperatureReading]
                                         )

  sealed trait TemperatureReading
  final case class Temperature(value: Double) extends TemperatureReading
  case object TemperatureNotAvailable extends TemperatureReading
  case object DeviceNotAvailable extends TemperatureReading
  case object DeviceTimedOut extends TemperatureReading
}

class DeviceManager extends Actor with ActorLogging {
  import DeviceManager._

  var deviceGroupToActor = Map.empty[String, ActorRef]
  var actorToDeviceGroup = Map.empty[ActorRef, String]

  override def preStart(): Unit = log.info("Device Manager started")
  override def postStop(): Unit = log.info("Device Manager stopped")

  override def receive: Receive = {
    case message @ RequestTrackDevice(groupId, deviceId) =>
      deviceGroupToActor
        .get(groupId)
        .fold {
          val deviceGroup = context.actorOf(DeviceGroup.props(groupId))
          context.watch(deviceGroup)
          deviceGroupToActor += groupId -> deviceGroup
          actorToDeviceGroup += deviceGroup -> groupId
          deviceGroup.forward(message)
        } { actorRef =>
          actorRef.forward(message)
        }
    case RequestListOfDeviceGroups(requestId) =>
      sender() ! ListOfDeviceGroups(requestId, deviceGroupToActor.keySet)
    case Terminated(actorRef) =>
      actorToDeviceGroup
        .get(actorRef)
        .fold(
          log.warning(
            "Received a terminated for a deviceGroup actor that is not being tracked"
          )
        ) { deviceId =>
          log.info("DeviceGroup deleted {}", deviceId)
          deviceGroupToActor -= deviceId
          actorToDeviceGroup -= actorRef
        }
  }
}
