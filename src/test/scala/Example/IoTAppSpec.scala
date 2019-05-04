package Example

import Example.Device.{
  ReadTemperature,
  RecordTemperature,
  RespondTemperature,
  TemperatureRecorded
}
import Example.DeviceGroup.RequestTemperaturesFromAllDevices
import Example.DeviceManager.{
  DeviceNotAvailable,
  DeviceRegistered,
  DeviceTimedOut,
  RequestTrackDevice,
  RespondAllTemperatures,
  Temperature,
  TemperatureNotAvailable
}
import akka.actor.{ActorSystem, PoisonPill}
import akka.testkit.TestProbe
import org.scalatest.{BeforeAndAfterAll, FeatureSpec}
import cats.implicits._

import concurrent.duration._

class IoTAppSpec extends FeatureSpec with BeforeAndAfterAll {
  implicit val system = ActorSystem("testSystem")

  Feature("Register Device") {
    Scenario("Check that device registered returns valid device") {
      val probe = TestProbe()
      val deviceActor = system.actorOf(Device.props("group", "device"))

      deviceActor.tell(
        DeviceManager.RequestTrackDevice("group", "device"),
        probe.ref
      )
      val res = probe.expectMsg(DeviceManager.DeviceRegistered)
      assert(deviceActor === probe.lastSender)
    }

    Scenario("Ignore the requests with wronge group and device") {
      val probe = TestProbe()
      val deviceActor = system.actorOf(Device.props("group", "device"))

      deviceActor.tell(
        DeviceManager.RequestTrackDevice("ssss", "device"),
        probe.ref
      )
      probe.expectNoMessage(500 millis)

      deviceActor.tell(
        DeviceManager.RequestTrackDevice("group", "ssss"),
        probe.ref
      )
      probe.expectNoMessage(500 millis)
    }
  }

  Feature("Read temperature") {
    Scenario("reply with empty reading if no temperature is known") {
      val probe = TestProbe()
      val deviceActor = system.actorOf(Device.props("group", "device"))

      deviceActor.tell(Device.ReadTemperature(requestId = 42), probe.ref)
      val response = probe.expectMsgType[Device.RespondTemperature]
      assert(response.requestId == 42)
      assert(response.value == None)
    }

    Scenario("write and read temperature") {
      val probe = TestProbe()
      val deviceActor = system.actorOf(Device.props("group", "device"))

      deviceActor.tell(Device.RecordTemperature(20, 40), probe.ref)

      val temperatureRecorded = probe.expectMsgType[Device.TemperatureRecorded]
      assert(temperatureRecorded.requestId == 20)

      deviceActor.tell((Device.ReadTemperature(42)), probe.ref)

      val temperatureRead = probe.expectMsgType[Device.RespondTemperature]
      assert(temperatureRead.requestId == 42)
      assert(temperatureRead.value contains 40)
    }
  }

  Feature("Track device in a group") {
    Scenario("Success tracking a new device") {
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

    Scenario("wrong groupId") {
      val probe = TestProbe()
      val deviceGroup = system.actorOf(DeviceGroup.props("4"))

      deviceGroup.tell(RequestTrackDevice("2", "1"), probe.ref)
      probe.expectNoMessage(500 millis)
    }

    Scenario("track an existing device") {
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

    Scenario("list of devices when no device is registered") {
      val groupId = "1"
      val probe = TestProbe()
      val groupActor = system.actorOf(DeviceGroup.props(groupId))

      groupActor.tell(DeviceGroup.RequestDeviceList(1), probe.ref)
      probe.expectMsg(DeviceGroup.ReplyDevices(1, Set.empty[String]))
    }

    Scenario("list of devices when devices are registered") {
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

    Scenario("list of devices after death of a device") {
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

      probe.awaitAssert {
        groupActor.tell(DeviceGroup.RequestDeviceList(1), probe.ref)
        probe.expectMsg(DeviceGroup.ReplyDevices(1, Set("1")))
      }
    }

    Scenario("Collect temperatures of all devices") {
      val probe = TestProbe()
      val deviceGroupActor = system.actorOf(DeviceGroup.props("1"))

      deviceGroupActor.tell(RequestTrackDevice("1", "1"), probe.ref)
      probe.expectMsg(DeviceRegistered)
      val device1 = probe.lastSender

      deviceGroupActor.tell(RequestTrackDevice("1", "2"), probe.ref)
      probe.expectMsg(DeviceRegistered)
      val device2 = probe.lastSender

      deviceGroupActor.tell(RequestTrackDevice("1", "3"), probe.ref)
      probe.expectMsg(DeviceRegistered)
      val device3 = probe.lastSender

      device1.tell(RecordTemperature(1, 200), probe.ref)
      probe.expectMsg(TemperatureRecorded(1))

      device2.tell(RecordTemperature(2, 400), probe.ref)
      probe.expectMsg(TemperatureRecorded(2))

      deviceGroupActor.tell(
        RequestTemperaturesFromAllDevices(20, 2 seconds),
        probe.ref
      )

      probe.awaitAssert {
        probe.expectMsg(
          RespondAllTemperatures(
            20,
            Map(
              ("1" -> Temperature(200)),
              ("2" -> Temperature(400)),
              ("3" -> TemperatureNotAvailable)
            )
          )
        )
      }
    }
  }

  Feature("DeviceManager") {
    Scenario("Create Device in new group") {
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

    Scenario(
      "List existing Device Groups after deleting a the only device of a group"
    ) {
      val groupId = "1"
      val probe = TestProbe()
      val deviceManager = system.actorOf(DeviceManager.props)

      deviceManager.tell(RequestTrackDevice(groupId, "1"), probe.ref)
      probe.expectMsg(DeviceManager.DeviceRegistered)
      val deviceActor = probe.lastSender

      probe.watch(deviceActor)
      deviceActor ! PoisonPill
      probe.expectTerminated(deviceActor)

      probe.awaitAssert {
        deviceManager.tell(
          DeviceManager.RequestListOfDeviceGroups(1),
          probe.ref
        )
        probe.expectMsg(DeviceManager.ListOfDeviceGroups(1, Set.empty[String]))
      }
    }
  }

  Feature("DeviceGroup Query") {
    Scenario("Get temperatures from working devices") {
      val requester = TestProbe()

      val device1 = TestProbe()
      val device2 = TestProbe()

      val queryActor = system.actorOf(
        DeviceGroupQuery.props(
          Map((device1.ref -> "1"), (device2.ref -> "2")),
          1,
          requester.ref,
          500 seconds
        )
      )

      device1.expectMsg(ReadTemperature(0))
      device2.expectMsg(ReadTemperature(0))

      val query1 = device1.lastSender
      query1.tell(RespondTemperature(0, Some(200)), device1.ref)

      val query2 = device2.lastSender
      query2.tell(RespondTemperature(0, Some(400)), device2.ref)

      requester.expectMsg(
        RespondAllTemperatures(
          1,
          Map(("1" -> Temperature(200)), ("2" -> Temperature(400)))
        )
      )
    }

    Scenario("One devices doesn't have temperature available") {
      val requester = TestProbe()

      val device1 = TestProbe()
      val device2 = TestProbe()

      val queryActor = system.actorOf(
        DeviceGroupQuery.props(
          Map((device1.ref -> "1"), (device2.ref -> "2")),
          1,
          requester.ref,
          500 seconds
        )
      )

      device1.expectMsg(ReadTemperature(0))
      device2.expectMsg(ReadTemperature(0))

      val query1 = device1.lastSender
      query1.tell(RespondTemperature(0, Some(200)), device1.ref)

      val query2 = device2.lastSender
      query2.tell(RespondTemperature(0, None), device2.ref)

      requester.expectMsg(
        RespondAllTemperatures(
          1,
          Map(("1" -> Temperature(200)), ("2" -> TemperatureNotAvailable))
        )
      )
    }

    Scenario("One device is not able to provide temperature") {
      val requester = TestProbe()

      val device1 = TestProbe()
      val device2 = TestProbe()

      val queryActor = system.actorOf(
        DeviceGroupQuery.props(
          Map((device1.ref -> "1"), (device2.ref -> "2")),
          1,
          requester.ref,
          1 seconds
        )
      )

      device1.expectMsg(ReadTemperature(0))
      device2.expectMsg(ReadTemperature(0))

      val query1 = device1.lastSender
      query1.tell(RespondTemperature(0, Some(200)), device1.ref)

      requester.awaitAssert(
        requester.expectMsg(
          RespondAllTemperatures(
            1,
            Map(("1" -> Temperature(200)), ("2" -> DeviceTimedOut))
          )
        ),
        2 seconds
      )
    }

    Scenario("One device terminates before it's able to provide temperature") {
      val requester = TestProbe()

      val device1 = TestProbe()
      val device2 = TestProbe()

      val queryActor = system.actorOf(
        DeviceGroupQuery.props(
          Map((device1.ref -> "1"), (device2.ref -> "2")),
          1,
          requester.ref,
          1 seconds
        )
      )

      device1.expectMsg(ReadTemperature(0))
      device2.expectMsg(ReadTemperature(0))

      val query1 = device1.lastSender
      query1.tell(RespondTemperature(0, Some(200)), device1.ref)

      val query2 = device2.lastSender
      device2.ref ! PoisonPill

      requester.awaitAssert {
        requester.expectMsg(
          RespondAllTemperatures(
            1,
            Map(("1" -> Temperature(200)), ("2" -> DeviceNotAvailable))
          )
        )
      }
    }

    Scenario("One device terminates after it's able to provide temperature") {
      val requester = TestProbe()

      val device1 = TestProbe()
      val device2 = TestProbe()

      val queryActor = system.actorOf(
        DeviceGroupQuery.props(
          Map((device1.ref -> "1"), (device2.ref -> "2")),
          1,
          requester.ref,
          1 seconds
        )
      )

      device1.expectMsg(ReadTemperature(0))
      device2.expectMsg(ReadTemperature(0))

      val query2 = device2.lastSender
      query2.tell(RespondTemperature(0, Some(400)), device2.ref)
      device2.ref ! PoisonPill

      val query1 = device1.lastSender
      query1.tell(RespondTemperature(0, Some(200)), device1.ref)

      requester.awaitAssert {
        requester.expectMsg(
          RespondAllTemperatures(
            1,
            Map(("1" -> Temperature(200)), ("2" -> Temperature(400)))
          )
        )
      }
    }
  }
}
