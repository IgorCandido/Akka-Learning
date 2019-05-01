package Example

import akka.actor.ActorSystem
import akka.testkit.TestProbe
import org.scalatest.{BeforeAndAfterAll, FeatureSpec}
import concurrent.duration._


class IoTAppSpec extends FeatureSpec with BeforeAndAfterAll{
  implicit val system = ActorSystem("testSystem")

  Feature("Register Device"){
    Scenario("Check that device registered returns valid device"){
      val probe = TestProbe()
      val deviceActor = system.actorOf(Device.props("group", "device"))

      deviceActor.tell(DeviceManager.RequestTrackDevice("group", "device"), probe.ref)
      val res = probe.expectMsg(DeviceManager.DeviceRegistered)
      assert(deviceActor === probe.lastSender)
    }

    Scenario("Ignore the requests with wronge group and device"){
      val probe = TestProbe()
      val deviceActor = system.actorOf(Device.props("group", "device"))

      deviceActor.tell(DeviceManager.RequestTrackDevice("ssss", "device"), probe.ref)
      probe.expectNoMessage(500 millis)

      deviceActor.tell(DeviceManager.RequestTrackDevice("group", "ssss"), probe.ref)
      probe.expectNoMessage(500 millis)
    }
  }

  Feature("Read temperature")
  {
    Scenario("reply with empty reading if no temperature is known"){
      val probe = TestProbe()
      val deviceActor = system.actorOf(Device.props("group", "device"))

      deviceActor.tell(Device.ReadTemperature(requestId = 42), probe.ref)
      val response = probe.expectMsgType[Device.RespondTemperature]
      assert(response.requestId == 42)
      assert(response.value === None)
    }

    Scenario("write and read temperature"){
      val probe = TestProbe()
      val deviceActor = system.actorOf(Device.props("group", "device"))

      deviceActor.tell(Device.RecordTemperature(20, 40), probe.ref)

      val temperatureRecorded = probe.expectMsgType[Device.TemperatureRecorded]
      assert(temperatureRecorded.requestId == 20)

      deviceActor.tell((Device.ReadTemperature(42)), probe.ref)

      val temperatureRead = probe.expectMsgType[Device.RespondTemperature]
      assert(temperatureRead.requestId == 42)
      assert(temperatureRead.value contains  40)
    }
  }
}
