package akka.management.http.scaladsl
import akka.actor.ExtendedActorSystem
import akka.management.http.{HealthCheckSettings, HealthChecksImpl}

import scala.concurrent.Future

/**
* Loads health checks from configuration
  */
object HealthChecks {
  def apply(system: ExtendedActorSystem,
            settings: HealthCheckSettings): HealthChecks =
    new HealthChecksImpl(system, settings)

  type HealthCheck = () => Future[Boolean]

}

abstract class HealthChecks {
  /**
   * Returns Future(true) if the system is ready to receive user traffic
   */
  def ready(): Future[Boolean]

  /**
   * Returns Future(true) to indicate that the process is alive but does not
    * mean that it is ready to receive traffic e.g. is has not joined the cluster
    * or is loading initial state from a database
   */
  def alive(): Future[Boolean]
}
