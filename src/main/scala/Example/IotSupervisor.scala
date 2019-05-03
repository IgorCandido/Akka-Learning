package Example

import Example.DeviceManager.RequestTrackDevice
import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, PoisonPill, Props, Terminated}

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

object Device {

  def props(groupId: String, deviceId: String): Props =
    Props(new Device(groupId, deviceId))

  final case class ReadTemperature(requestId: Long)
  final case class RespondTemperature(requestId: Long, value: Option[Double])
  final case class RecordTemperature(requestId: Long, value: Double)
  final case class TemperatureRecorded(requestId: Long)
}

class Device(groupId: String, deviceId: String)
    extends Actor
    with ActorLogging {
  import Device._

  var lastTemperatureReading: Option[Double] = None

  override def preStart(): Unit =
    log.info(s"Device actor {} - {} started", groupId, deviceId)
  override def postStop(): Unit =
    log.info(s"Device actor {} - {} stopped", groupId, deviceId)

  override def receive: Receive = {
    case DeviceManager.RequestTrackDevice(`groupId`, `deviceId`) =>
      sender() ! DeviceManager.DeviceRegistered
    case ReadTemperature(requestId) =>
      sender() ! RespondTemperature(requestId, lastTemperatureReading)
    case RecordTemperature(requestId, value) =>
      lastTemperatureReading = Some(value)
      log.info(s"Received temperature ${value}")
      sender() ! TemperatureRecorded(requestId)
  }
}

object DeviceManager {
  def props = Props(new DeviceManager)

  final case class RequestTrackDevice(groupId: String, deviceId: String)
  final case object DeviceRegistered

  final case class RequestListOfDeviceGroups(requestId: Int)
  final case class ListOfDeviceGroups(requestId: Int, groupIds: Set[String])
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

object DeviceGroup {
  def props(groupId: String): Props = Props(new DeviceGroup(groupId))

  final case class RequestDeviceList(requestId: Int)
  final case class ReplyDevices(requestId: Int, deviceIds: Set[String])
}

class DeviceGroup(groupId: String) extends Actor with ActorLogging {
  import DeviceGroup._
  var deviceIdToActor = Map.empty[String, ActorRef]
  var actorToDeviceId = Map.empty[ActorRef, String]

  override def preStart(): Unit = log.info("Device Group {} started", groupId)
  override def postStop(): Unit = log.info("Device Group {} stopped", groupId)

  override def receive: Receive = {
    case trackMsg @ RequestTrackDevice(`groupId`, deviceId) =>
      deviceIdToActor.get(deviceId) match {
        case Some(deviceActor) =>
          deviceActor.forward(trackMsg)
        case None =>
          log.info("Creating a Device to track {} - {}", groupId, deviceId)
          val deviceActor = context.actorOf(Device.props(groupId, deviceId))
          context.watch(deviceActor)
          deviceIdToActor += deviceId -> deviceActor
          actorToDeviceId += deviceActor -> deviceId
          deviceActor.forward(trackMsg)
      }
    case RequestTrackDevice(wrongGroupId, _) =>
      log.warning(
        "Ignoring TrackDevice for {}. This actor belongs to other groupId {}",
        wrongGroupId,
        groupId
      )
    case RequestDeviceList(requestId) =>
      sender() ! ReplyDevices(requestId, deviceIdToActor.keySet)
    case Terminated(actorRef) =>
      actorToDeviceId
        .get(actorRef)
        .fold(
          log.warning("Device stopped whilst wasn't being tracked by the group")
        ) { deviceId =>
          log.info("Stopped Device with id {}", deviceId)
          deviceIdToActor -= deviceId
          actorToDeviceId -= actorRef

          if (deviceIdToActor.isEmpty) self ! PoisonPill
        }
  }
}
