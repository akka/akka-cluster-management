/*
 * Copyright (C) 2017-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.management.cluster.bootstrap.contactpoint

import akka.cluster.{ Cluster, ClusterEvent }
import akka.event.NoLogging
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.management.cluster.bootstrap.ClusterBootstrapSettings
import akka.testkit.{ SocketUtil, TestProbe }
import org.scalatest.{ Matchers, WordSpecLike }

class HttpContactPointRoutesSpec
    extends WordSpecLike
    with Matchers
    with ScalatestRouteTest
    with HttpBootstrapJsonProtocol {

  override def testConfigSource =
    s"""
    akka {
      remote {
        netty.tcp {
          hostname = "127.0.0.1"
          port = ${SocketUtil.temporaryServerAddress("127.0.0.1").getPort}
        }
      }
    }
    """.stripMargin

  "Http Bootstrap routes" should {

    val settings = ClusterBootstrapSettings(system.settings.config, NoLogging)
    val httpBootstrap = new HttpClusterBootstrapRoutes(settings)

    "empty list if node is not part of a cluster" in {
      ClusterBootstrapRequests.bootstrapSeedNodes("") ~> httpBootstrap.routes ~> check {
        responseAs[String] should include(""""seedNodes":[]""")
      }
    }

    "include seed nodes when part of a cluster" in {
      val cluster = Cluster(system)
      cluster.join(cluster.selfAddress)

      val p = TestProbe()
      cluster.subscribe(p.ref, ClusterEvent.InitialStateAsEvents, classOf[ClusterEvent.MemberUp])
      val up = p.expectMsgType[ClusterEvent.MemberUp]
      up.member should ===(cluster.selfMember)

      ClusterBootstrapRequests.bootstrapSeedNodes("") ~> httpBootstrap.routes ~> check {
        val response = responseAs[HttpBootstrapJsonProtocol.SeedNodes]
        response.seedNodes should !==(Nil)
        response.seedNodes.map(_.node) should contain(cluster.selfAddress)
      }
    }
  }

}
