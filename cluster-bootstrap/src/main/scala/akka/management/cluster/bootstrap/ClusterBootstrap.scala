/*
 * Copyright (C) 2017 Lightbend Inc. <http://www.lightbend.com>
 */
package akka.management.cluster.bootstrap

import java.util.concurrent.atomic.AtomicReference

import scala.concurrent.duration._
import scala.concurrent.Future
import scala.concurrent.Promise

import akka.actor.ActorSystem
import akka.actor.ExtendedActorSystem
import akka.actor.Extension
import akka.actor.ExtensionId
import akka.actor.ExtensionIdProvider
import akka.annotation.InternalApi
import akka.cluster.Cluster
import akka.discovery.ServiceDiscovery
import akka.discovery.SimpleServiceDiscovery
import akka.event.Logging
import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.server.Route
import akka.management.cluster.bootstrap.contactpoint.HttpClusterBootstrapRoutes
import akka.management.cluster.bootstrap.internal.BootstrapCoordinator
import akka.management.http.ManagementRouteProvider
import akka.management.http.ManagementRouteProviderSettings
import akka.pattern.ask
import akka.util.Timeout

final class ClusterBootstrap(implicit system: ExtendedActorSystem) extends Extension with ManagementRouteProvider {

  import ClusterBootstrap._
  import system.dispatcher

  private val log = Logging(system, classOf[ClusterBootstrap])

  private final val bootstrapStep = new AtomicReference[BootstrapStep](NotRunning)

  val settings = ClusterBootstrapSettings(system.settings.config)

  // used for initial discovery of contact points
  val discovery: SimpleServiceDiscovery =
    settings.contactPointDiscovery.discoveryMethod match {
      case "akka.discovery" ⇒
        val discovery = ServiceDiscovery(system).discovery
        log.info("Bootstrap using default `akka.discovery` mechanism: {}", Logging.simpleName(discovery))
        discovery

      case otherDiscoveryMechanism ⇒
        log.info("Bootstrap using `akka.discovery` mechanism: {}", otherDiscoveryMechanism)
        ServiceDiscovery(system).loadServiceDiscovery(otherDiscoveryMechanism)
    }

  private val joinDecider: JoinDecider = {
    system.dynamicAccess
      .createInstanceFor[JoinDecider](settings.joinDecider.implClass,
        List((classOf[ActorSystem], system), (classOf[ClusterBootstrapSettings], settings)))
      .get
  }

  private[this] val _selfContactPointUri: Promise[Uri] = Promise()

  override def routes(routeProviderSettings: ManagementRouteProviderSettings): Route = {
    log.info(s"Got self contact point address: ${routeProviderSettings.selfBaseUri}")
    this.setSelfContactPoint(routeProviderSettings.selfBaseUri)

    new HttpClusterBootstrapRoutes(settings).routes
  }

  def start(): Unit =
    if (Cluster(system).settings.SeedNodes.nonEmpty) {
      log.warning(
          "Application is configured with specific `akka.cluster.seed-nodes`: {}, bailing out of the bootstrap process! " +
          "If you want to use the automatic bootstrap mechanism, make sure to NOT set explicit seed nodes in the configuration. " +
          "This node will attempt to join the configured seed nodes.",
          Cluster(system).settings.SeedNodes.mkString("[", ", ", "]"))
    } else if (bootstrapStep.compareAndSet(NotRunning, Initializing)) {
      log.info("Initiating bootstrap procedure using {} method...", settings.contactPointDiscovery.discoveryMethod)

      val bootstrapProps = BootstrapCoordinator.props(discovery, joinDecider, settings)
      val bootstrap = system.systemActorOf(bootstrapProps, "bootstrapCoordinator")

      // the boot timeout not really meant to be exceeded
      implicit val bootTimeout: Timeout = Timeout(1.day)
      val bootstrapCompleted = (bootstrap ? BootstrapCoordinator.Protocol.InitiateBootstrapping).mapTo[
          BootstrapCoordinator.Protocol.BootstrappingCompleted]

      bootstrapCompleted.failed.foreach { _ =>
        log.warning("Failed to complete bootstrap within {}!", bootTimeout)
      }
    } else log.warning("Bootstrap already initiated, yet start() method was called again. Ignoring.")

  /**
   * INTERNAL API
   *
   * Must be invoked by whoever starts the HTTP server with the `HttpClusterBootstrapRoutes`.
   * This allows us to "reverse lookup" from a lowest-address sorted contact point list,
   * that we discover via discovery, if a given contact point corresponds to our remoting address,
   * and if so, we may opt to join ourselves using the address.
   *
   * @return true if successfully set, false otherwise (i.e. was set already)
   */
  @InternalApi
  private[akka] def setSelfContactPoint(baseUri: Uri): Unit =
    _selfContactPointUri.success(baseUri)

  /** INTERNAL API */
  @InternalApi private[akka] def selfContactPoints: Future[Set[(String, Int)]] =
    _selfContactPointUri.future.map { uri =>
      if (system.settings.config.hasPath("akka.discovery.kubernetes-api.pod-domain") && uri.authority.host.isIPv4()) {
        val podNamespace = system.settings.config.getString("akka.discovery.kubernetes-api.pod-namespace")
        val podDomain = system.settings.config.getString("akka.discovery.kubernetes-api.pod-domain")
        val derivedHost = s"${uri.authority.host.toString.replace('.', '-')}.${podNamespace}.pod.${podDomain}"
        log.info(s"Derived self contact point $derivedHost:${uri.authority.port}")
        Set(
          (uri.authority.host.toString, uri.authority.port),
          (derivedHost, uri.authority.port)
        )
      } else {
        Set((uri.authority.host.toString, uri.authority.port))
      }
    }

}

object ClusterBootstrap extends ExtensionId[ClusterBootstrap] with ExtensionIdProvider {

  override def lookup: ClusterBootstrap.type = ClusterBootstrap

  override def get(system: ActorSystem): ClusterBootstrap = super.get(system)

  override def createExtension(system: ExtendedActorSystem): ClusterBootstrap = new ClusterBootstrap()(system)

  private[bootstrap] sealed trait BootstrapStep
  private[bootstrap] case object NotRunning extends BootstrapStep
  private[bootstrap] case object Initializing extends BootstrapStep
  // TODO get the Initialized state once done
  private[bootstrap] case object Initialized extends BootstrapStep

}
