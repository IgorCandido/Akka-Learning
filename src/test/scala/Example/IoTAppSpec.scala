package Example

import Example.DeviceManager.RequestTrackDevice
import akka.actor.{ActorSystem, PoisonPill}
import akka.testkit.TestProbe
import org.scalatest.{BeforeAndAfterAll, FeatureSpec}
import cats.implicits._

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
      assert(response.value == None)
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

  Feature("Track device in a group"){
    Scenario("Success tracking a new device"){
      val groupId = "1"
      val probe = TestProbe()
      val group = system.actorOf(DeviceGroup.props(groupId))

      group.tell(RequestTrackDevice(groupId, "20"), probe.ref)
      probe.expectMsg(DeviceManager.DeviceRegistered)
      val deviceActor1 = probe.lastSender

      group.tell(RequestTrackDevice(groupId, "22"), probe.ref)
      probe.expectMsg(DeviceManager.DeviceRegistered)
      val deviceActor2 = probe.lastSender

      deviceActor1.tell(Device.RecordTemperature(1, 200), probe.ref)
      probe.expectMsg(Device.TemperatureRecorded(1))
      deviceActor2.tell(Device.RecordTemperature(2, 400), probe.ref)
      probe.expectMsg(Device.TemperatureRecorded(2))

      deviceActor1.tell(Device.ReadTemperature(4), probe.ref)
      probe.expectMsg(Device.RespondTemperature(4, 200d.some))

      deviceActor2.tell(Device.ReadTemperature(5), probe.ref)
      probe.expectMsg(Device.RespondTemperature(5, 400d.some))
    }

    Scenario("wrong groupId"){
      val probe = TestProbe()
      val deviceGroup = system.actorOf(DeviceGroup.props("4"))

      deviceGroup.tell(RequestTrackDevice("2", "1"), probe.ref)
      probe.expectNoMessage(500 millis)
    }

    Scenario("track an existing device"){
      val groupId = "1"
      val probe = TestProbe()
      val groupActor = system.actorOf(DeviceGroup.props(groupId))

      groupActor.tell(RequestTrackDevice(groupId, "1"), probe.ref)
      probe.expectMsg(DeviceManager.DeviceRegistered)
      val actorRef = probe.lastSender

      groupActor.tell(RequestTrackDevice(groupId, "1"), probe.ref)
      probe.expectMsg(DeviceManager.DeviceRegistered)
      val actorRef2 = probe.lastSender

      assert(actorRef === actorRef2)
    }

    Scenario("list of devices when no device is registered"){
      val groupId = "1"
      val probe = TestProbe()
      val groupActor = system.actorOf(DeviceGroup.props(groupId))

      groupActor.tell(DeviceGroup.RequestDeviceList(1), probe.ref)
      probe.expectMsg(DeviceGroup.ReplyDevices(1, Set.empty[String]))
    }

    Scenario("list of devices when devices are registered"){
      val groupId = "1"
      val probe = TestProbe()
      val groupActor = system.actorOf(DeviceGroup.props(groupId))

      groupActor.tell(RequestTrackDevice(groupId, "1"), probe.ref)
      probe.expectMsg(DeviceManager.DeviceRegistered)
      val deviceActor1 = probe.lastSender

      groupActor.tell(RequestTrackDevice(groupId, "2"), probe.ref)
      probe.expectMsg(DeviceManager.DeviceRegistered)
      val deviceActor2 = probe.lastSender

      groupActor.tell(DeviceGroup.RequestDeviceList(1), probe.ref)
      probe.expectMsg(DeviceGroup.ReplyDevices(1, Set("1", "2")))
    }

    Scenario("list of devices after death of a device"){
      val groupId = "1"
      val probe = TestProbe()
      val groupActor = system.actorOf(DeviceGroup.props(groupId))

      groupActor.tell(RequestTrackDevice(groupId, "1"), probe.ref)
      probe.expectMsg(DeviceManager.DeviceRegistered)
      val deviceActor1 = probe.lastSender

      groupActor.tell(RequestTrackDevice(groupId, "2"), probe.ref)
      probe.expectMsg(DeviceManager.DeviceRegistered)
      val deviceActor2 = probe.lastSender

      probe.watch(deviceActor2)
      deviceActor2 ! PoisonPill
      probe.expectTerminated(deviceActor2)

      probe.awaitAssert{
        groupActor.tell(DeviceGroup.RequestDeviceList(1), probe.ref)
        probe.expectMsg(DeviceGroup.ReplyDevices(1, Set("1")))
      }
    }
  }

  Feature("DeviceManager"){
    Scenario("Create Device in new group"){
      val groupId = "1"
      val probe = TestProbe()
      val deviceManager = system.actorOf(DeviceManager.props)

      deviceManager.tell(RequestTrackDevice(groupId, "1"), probe.ref)
      probe.expectMsg(DeviceManager.DeviceRegistered)
    }

    Scenario("List existing Device Groups") {
      val groupId = "1"
      val probe = TestProbe()
      val deviceManager = system.actorOf(DeviceManager.props)

      deviceManager.tell(RequestTrackDevice(groupId, "1"), probe.ref)
      probe.expectMsg(DeviceManager.DeviceRegistered)

      deviceManager.tell(DeviceManager.RequestListOfDeviceGroups(1), probe.ref)
      probe.expectMsg(DeviceManager.ListOfDeviceGroups(1, Set(groupId)))
    }

    Scenario("List existing Device Groups after deleting a the only device of a group") {
      val groupId = "1"
      val probe = TestProbe()
      val deviceManager = system.actorOf(DeviceManager.props)

      deviceManager.tell(RequestTrackDevice(groupId, "1"), probe.ref)
      probe.expectMsg(DeviceManager.DeviceRegistered)
      val deviceActor = probe.lastSender

      probe.watch(deviceActor)
      deviceActor ! PoisonPill
      probe.expectTerminated(deviceActor)

      probe.awaitAssert{
        deviceManager.tell(DeviceManager.RequestListOfDeviceGroups(1), probe.ref)
        probe.expectMsg(DeviceManager.ListOfDeviceGroups(1, Set.empty[String]))
      }
    }
  }
}
