package Example

import akka.actor.{Actor, ActorLogging, Props}

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
