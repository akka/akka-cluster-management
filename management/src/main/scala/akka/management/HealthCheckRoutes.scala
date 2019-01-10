/*
 * Copyright (C) 2017-2018 Lightbend Inc. <http://www.lightbend.com>
 */

package akka.management

import akka.actor.ExtendedActorSystem
import akka.annotation.InternalApi
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{ PathMatchers, Route }
import akka.management.scaladsl.{ HealthChecks, ManagementRouteProvider, ManagementRouteProviderSettings }

import scala.util.{ Failure, Success, Try }

/**
 * INTERNAL API
 *
 * We could make this public so users can add it to their own server, not sure
 * for ManagementRouteProviders
 */
@InternalApi
private[akka] class HealthCheckRoutes(aes: ExtendedActorSystem) extends ManagementRouteProvider {

  private val settings: HealthCheckSettings = HealthCheckSettings(
    aes.settings.config.getConfig("akka.management.http.health-checks")
  )

  // exposed for testing
  protected val healthChecks = HealthChecks(aes, settings)

  private val healthCheckResponse: Try[Boolean] => Route = {
    case Success(true) => complete(StatusCodes.OK)
    case Success(false) =>
      complete(StatusCodes.InternalServerError -> "Not Healthy")
    case Failure(t) =>
      complete(
        StatusCodes.InternalServerError -> s"Health Check Failed: ${t.getMessage}"
      )
  }

  override def routes(mrps: ManagementRouteProviderSettings): Route = {
    concat(
        path(PathMatchers.separateOnSlashes(settings.readinessPath)) {
          get {
            onComplete(healthChecks.ready())(healthCheckResponse)
          }
        },
        path(PathMatchers.separateOnSlashes(settings.livenessPath)) {
          get {
            onComplete(healthChecks.alive())(healthCheckResponse)
          }
        })
  }
}
