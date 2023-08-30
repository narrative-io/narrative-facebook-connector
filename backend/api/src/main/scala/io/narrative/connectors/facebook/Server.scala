package io.narrative.connectors.facebook

import cats.effect.{IO, IOApp, Resource}
import com.comcast.ip4s.{Host, Port}
import com.typesafe.scalalogging.LazyLogging
import io.narrative.connectors.facebook.routes.Logging
import io.narrative.connectors.facebook.routes.profiles.ProfileRoutes
import io.narrative.connectors.facebook.routes.tokens.TokenRoutes
import io.narrative.connectors.facebook.services.{
  ApiClient,
  FacebookApp,
  FacebookClient,
  KmsKeyId,
  ProfileService,
  TokenEncryptionService
}
import io.narrative.connectors.facebook.stores.ProfileStore
import org.http4s.{HttpApp, Uri}
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.Router
import org.http4s.server.middleware.CORS

object Server extends IOApp.Simple with LazyLogging {
  override def run: IO[Unit] = {
    val server = for {
      config <- Resource.eval(Config())
      resources <- Resources(config)
      routes <- Resource.eval(router(config, resources))
      server <- EmberServerBuilder
        .default[IO]
        .withHost(
          Host
            .fromString("0.0.0.0")
            .getOrElse(throw new RuntimeException("Programming error: host 0.0.0.0 is not valid"))
        )
        .withPort(
          Port
            .fromString(config.server.port.value)
            .getOrElse(throw new RuntimeException(s"Programming error: port ${config.server.port.value} is not valid"))
        )
        .withHttpApp(routes)
        .build
    } yield server

    server.use(_ => IO.never).void
  }

  def router(config: Config, resources: Resources): IO[HttpApp[IO]] =
    for {
      appId <- resources.resolve(config.facebook.appId).map(FacebookApp.Id.apply)
      appSecret <- resources.resolve(config.facebook.appSecret).map(FacebookApp.Secret.apply)
      kmsKeyId <- resources.resolve(config.kms.tokenEncryptionKeyId).map(KmsKeyId.apply)
      apiBaseUri <- resources.resolve(config.narrativeApi.baseUri).map(Uri.unsafeFromString)
      apiClient = new ApiClient(baseUri = apiBaseUri, client = resources.client)
      fbClient = new FacebookClient(FacebookApp(appId, appSecret))
      profileService = new ProfileService(
        apiClient,
        fbClient,
        new TokenEncryptionService(kmsKeyId, resources.kms),
        ProfileStore(resources.xa)
      )
      http = CORS.policy.withAllowCredentials(false) {
        Logging {
          Router[IO](
            "/profiles" -> new ProfileRoutes(profileService).routes,
            "/tokens" -> new TokenRoutes(profileService).routes
          ).orNotFound
        }
      }
    } yield http
}
