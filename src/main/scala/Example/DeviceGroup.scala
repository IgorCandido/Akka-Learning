package Example

import Example.DeviceManager.RequestTrackDevice
import akka.actor.{Actor, ActorLogging, ActorRef, PoisonPill, Props, Terminated}

import scala.concurrent.duration.{Duration, FiniteDuration}

object DeviceGroup {
  def props(groupId: String): Props = Props(new DeviceGroup(groupId))

  final case class RequestDeviceList(requestId: Int)
  final case class ReplyDevices(requestId: Int, deviceIds: Set[String])
  final case class RequestTemperaturesFromAllDevices(requestId: Int, timeout: FiniteDuration)
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
    case RequestTemperaturesFromAllDevices(requestId, timeout) =>{
      context.actorOf(DeviceGroupQuery.props(actorToDeviceId, requestId, sender(), timeout))
    }
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
