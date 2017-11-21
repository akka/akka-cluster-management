/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package akka.cluster.bootstrap.http

import akka.actor.{ Address, AddressFromURIString }
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.{ DefaultJsonProtocol, JsString, JsValue, RootJsonFormat }

trait HttpBootstrapJsonProtocol extends SprayJsonSupport with DefaultJsonProtocol {
  import HttpBootstrapJsonProtocol._

  implicit object AddressFormat extends RootJsonFormat[Address] {
    override def read(json: JsValue): Address = json match {
      case JsString(s) ⇒ AddressFromURIString.parse(s)
      case invalid ⇒ throw new IllegalArgumentException(s"Illegal address value! Was [$invalid]")
    }

    override def write(obj: Address): JsValue = JsString(obj.toString)
  }
  implicit val SeedNodeFormat = jsonFormat1(SeedNode)
  implicit val ClusterMemberFormat = jsonFormat4(ClusterMember)
  implicit val ClusterMembersFormat = jsonFormat3(SeedNodes)
}

object HttpBootstrapJsonProtocol extends DefaultJsonProtocol {

  final case class SeedNode(address: Address)

  // TODO this very similar to cluster-http
  // we use Address since we want to know which protocol is being used (tcp, artery, artery-tcp etc)
  final case class ClusterMember(node: Address, nodeUid: Long, status: String, roles: Set[String])

  final case class SeedNodes(selfNode: Address, seedNodes: Set[ClusterMember], oldest: Option[Address])

}
