package Example

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}

import scala.io.StdIn

object IotSupervisor {
  def props: Props = Props(new IotSupervisor)
}

class IotSupervisor extends Actor with ActorLogging{
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

object Device{

  def props(groupId: String, deviceId: String): Props = Props(new Device(groupId, deviceId))

  final case class ReadTemperature(requestId: Long)
  final case class RespondTemperature(requestId: Long, value: Option[Double])
  final case class RecordTemperature(requestId: Long, value: Double)
  final case class TemperatureRecorded(requestId: Long)
}

class Device(groupId: String, deviceId: String) extends Actor with ActorLogging {
  import Device._

  var lastTemperatureReading: Option[Double] = None

  override def preStart(): Unit = log.info(s"Device actor $groupId-$deviceId started")
  override def postStop(): Unit = log.info(s"Device actor $groupId-$deviceId stopped")

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


  final case class RequestTrackDevice(groupsId: String, deviceId: String)
  final case object DeviceRegistered
}

object DeviceGroup {
  def props(groupId: String): Props = Props(new DeviceGroup(groupId))
}

class DeviceGroup(groupId: String) extends Actor{
  override def receive: Receive = ???
}