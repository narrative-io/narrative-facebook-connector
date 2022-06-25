package io.narrative.connectors.facebook

import cats.effect.{IO, IOApp, Resource}
import com.typesafe.scalalogging.LazyLogging
import org.http4s.blaze.server.BlazeServerBuilder

object Server extends IOApp.Simple with LazyLogging {
  override def run: IO[Unit] = {
    val server = for {
      config <- Resource.eval(Config())
      resources <- Resources(config)
      server <- BlazeServerBuilder[IO](resources.serverEC)
        .bindHttp(8080, "0.0.0.0")
        .withHttpApp(???)
        .resource
    } yield server

    server.use(_ => IO.never).void
  }
}
