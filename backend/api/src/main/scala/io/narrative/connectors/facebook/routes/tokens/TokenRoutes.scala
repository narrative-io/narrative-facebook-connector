package io.narrative.connectors.facebook.routes.tokens

import cats.effect.{ContextShift, IO}
import com.typesafe.scalalogging.LazyLogging
import io.narrative.connectors.facebook.routes.Auth
import io.narrative.connectors.facebook.services.{ProfileService, TokenMetaRequest}
import org.http4s._
import org.http4s.circe.CirceEntityCodec._
import org.http4s.dsl.io._

class TokenRoutes(service: ProfileService.Ops[IO])(implicit contextShift: ContextShift[IO]) extends LazyLogging {

  val routes: HttpRoutes[IO] = Auth.noauth { case req @ POST -> Root / "metadata" =>
    tokenMeta(req)
  }

  private def tokenMeta(req: Request[IO]): IO[Response[IO]] =
    for {
      metaReq <- req.as[TokenMetaRequest]
      meta <- service.meta(metaReq.token)
      resp <- Ok(meta)
    } yield resp
}
