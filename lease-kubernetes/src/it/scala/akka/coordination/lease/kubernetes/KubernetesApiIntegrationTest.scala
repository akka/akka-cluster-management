package akka.coordination.lease.kubernetes

import akka.Done
import akka.actor.ActorSystem
import akka.coordination.lease.kubernetes.internal.KubernetesApiImpl
import akka.testkit.TestKit
import com.typesafe.config.ConfigFactory
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import org.scalatest.concurrent.ScalaFutures

import scala.concurrent.duration._

/**
  * This test requires an API server available on localhost:8080, the lease CRD created and a namespace called lease
  *
  * One way of doing this is to have a kubectl proxy open:
  *
  * `kubectl proxy --port=8080`
  *
  * TODO run a minikube in CI and run kubectl proxy or run inside a container in open shift
  */
class KubernetesApiIntegrationTest extends TestKit(ActorSystem("KubernetesApiIntegrationSpec", ConfigFactory.parseString(
  """
    |akka.loglevel = DEBUG
    |""".stripMargin)))
  with WordSpecLike with Matchers
  with ScalaFutures with BeforeAndAfterAll {

  implicit val patience: PatienceConfig = PatienceConfig(testKitSettings.DefaultTimeout.duration)

  val settings = new KubernetesSettings(
    "",
    "",
    "localhost",
    8080,
    namespace = Some("lease"),
    "",
    apiServerRequestTimeout = 1.second,
    false
  )

  val underTest = new KubernetesApiImpl(system, settings)
  val leaseName = "lease-1"
  val client1 = "client-1"
  val client2 = "client-2"
  var currentVersion = ""


  override protected def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  "Kubernetes lease resource" should {
    "be able to be created" in {
      underTest.removeLease(leaseName).futureValue shouldEqual Done
      val leaseRecord = underTest.readOrCreateLeaseResource(leaseName).futureValue
      leaseRecord.owner shouldEqual None
      leaseRecord.version shouldNot equal("")
      leaseRecord.version shouldNot equal(null)
      currentVersion = leaseRecord.version
    }

    "be able to read back with same version" in {
      val leaseRecord = underTest.readOrCreateLeaseResource(leaseName).futureValue
      leaseRecord.version shouldEqual currentVersion
    }

    "be able to take a lease with no owner" in {
      val leaseRecord = underTest.updateLeaseResource(leaseName, client1, currentVersion).futureValue
      val success: LeaseResource = leaseRecord match {
        case Right(lr) => lr
      }
      success.version shouldNot equal(currentVersion)
      currentVersion = success.version
      success.owner shouldEqual Some(client1)
    }

    "be able to update a lease if resource version is correct" in {
      val timeUpdate = System.currentTimeMillis()
      val leaseRecord = underTest.updateLeaseResource(leaseName, client1, currentVersion, time = timeUpdate).futureValue
      val success: LeaseResource = leaseRecord match {
        case Right(lr) => lr
      }
      success.version shouldNot equal(currentVersion)
      currentVersion = success.version
      success.owner shouldEqual Some(client1)
      success.time shouldEqual timeUpdate
    }

    "not be able to update a lease if resource version is correct" in {
      val timeUpdate = System.currentTimeMillis()
      val leaseRecord = underTest.updateLeaseResource(leaseName, client1, "10", time = timeUpdate).futureValue
      val failure: LeaseResource = leaseRecord match {
        case Left(lr) => lr
      }
      failure.version shouldEqual currentVersion
      currentVersion = failure.version
      failure.owner shouldEqual Some(client1)
      failure.time shouldNot equal(timeUpdate)
    }

    "be able to remove ownership" in {
      val leaseRecord = underTest.updateLeaseResource(leaseName, "", currentVersion).futureValue
      val success: LeaseResource = leaseRecord match {
        case Right(lr) => lr
      }
      success.version shouldNot equal(currentVersion)
      currentVersion = success.version
      success.owner shouldEqual None
    }

    "be able to get lease once other client has removed" in {
      val leaseRecord = underTest.updateLeaseResource(leaseName, client2, currentVersion).futureValue
      val success: LeaseResource = leaseRecord match {
        case Right(lr) => lr
      }
      success.version shouldNot equal(currentVersion)
      currentVersion = success.version
      success.owner shouldEqual Some(client2)
    }
  }
}
