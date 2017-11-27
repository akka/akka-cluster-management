/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package akka.discovery

import akka.actor.ActorSystem
import akka.event.Logging
import akka.io.AsyncDnsResolver.SrvResolved
import akka.io.{ Dns, IO }
import akka.pattern.ask
import com.typesafe.config.Config

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

/**
 * Looks for SRV records, or A records.
 */
class DnsSrvServiceDiscovery(system: ActorSystem, effectiveConfig: Config) extends ServiceDiscovery {
  private val log = Logging(system, getClass)
  private val dns = IO(Dns)(system)
  import system.dispatcher

  val config = new DnsSrvServiceDiscoverySettings(effectiveConfig)

  override def lookup(name: String, resolveTimeout: FiniteDuration): Future[ServiceDiscovery.Resolved] = {
    def cleanIpString(ipString: String): String =
      if (ipString.startsWith("/")) ipString.tail else ipString

    dns.ask(Dns.Resolve(name))(resolveTimeout) map {
      case srv: SrvResolved =>
        log.debug("Resolved Srv.Resolved: {}", srv)
        val addresses = srv.srv.map { entry ⇒
          ServiceDiscovery.ResolvedTarget(cleanIpString(entry.target), Some(entry.port))
        }
        ServiceDiscovery.Resolved(name, addresses)

      case resolved: Dns.Resolved =>
        log.debug("Resolved Dns.Resolved: {}", resolved)
        val addresses = resolved.ipv4.map { entry ⇒
          ServiceDiscovery.ResolvedTarget(cleanIpString(entry.getHostAddress), None)
        }
        ServiceDiscovery.Resolved(name, addresses)

      case resolved ⇒
        log.warning("Resolved UNEXPECTED (resolving to Nil): {}", resolved.getClass)
        ServiceDiscovery.Resolved(name, Nil)
    }
  }
}

class DnsSrvServiceDiscoverySettings(config: Config) {}
