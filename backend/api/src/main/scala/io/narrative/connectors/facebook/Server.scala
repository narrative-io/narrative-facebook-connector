package io.narrative.connectors.facebook

import cats.effect.{IO, IOApp, Resource}
import com.typesafe.scalalogging.LazyLogging
import io.narrative.connectors.facebook.routes.Logging
import io.narrative.connectors.facebook.routes.profiles.ProfileRoutes
import io.narrative.connectors.facebook.routes.tokens.TokenRoutes
import io.narrative.connectors.facebook.service.FacebookClient
import io.narrative.connectors.facebook.services.{ApiClient, KmsKeyId, ProfileService, TokenEncryptionService}
import io.narrative.connectors.facebook.stores.ProfileStore
import org.http4s.{HttpApp, Uri}
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.server.Router
import org.http4s.server.middleware.CORS

object Server extends IOApp.Simple with LazyLogging {
  override def run: IO[Unit] = {
    val server = for {
      config <- Resource.eval(Config())
      resources <- Resources(config)
      routes <- Resource.eval(router(config, resources))
      server <- BlazeServerBuilder[IO](resources.serverEC)
        .bindHttp(9998, "0.0.0.0")
        .withHttpApp(routes)
        .resource
    } yield server

    server.use(_ => IO.never).void
  }

  def router(config: Config, resources: Resources): IO[HttpApp[IO]] =
    for {
      appId <- resources.resolve(config.facebook.appId)
      appSecret <- resources.resolve(config.facebook.appSecret)
      kmsKeyId <- resources.resolve(config.kms.tokenEncryptionKeyId).map(KmsKeyId.apply)
      apiBaseUri <- resources.resolve(config.narrativeApi.baseUri).map(Uri.unsafeFromString)
      apiClient = new ApiClient(resources.client, apiBaseUri)
      fbClient = new FacebookClient(appId = appId, appSecret = appSecret, blocker = resources.blocker)
      profileService = new ProfileService(
        apiClient,
        fbClient,
        new TokenEncryptionService(resources.blocker, kmsKeyId, resources.kms),
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
