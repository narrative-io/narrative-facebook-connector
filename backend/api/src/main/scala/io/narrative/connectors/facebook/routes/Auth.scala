package io.narrative.connectors.facebook.routes

import cats.data.{Kleisli, OptionT}
import cats.effect.IO
import com.typesafe.scalalogging.LazyLogging
import io.narrative.connectors.facebook.services.BearerToken
import org.http4s.client.UnexpectedStatus
import org.http4s.headers.Authorization
import org.http4s.{
  AuthScheme,
  ContextRequest,
  ContextRoutes,
  Credentials,
  HttpRoutes,
  HttpVersion,
  InvalidMessageBodyFailure,
  Request,
  Response,
  Status
}
import org.http4s.implicits._
import org.http4s.server.{ContextMiddleware, HttpMiddleware}

object Auth extends LazyLogging {
  def auth: PartialFunction[ContextRequest[IO, BearerToken], IO[Response[IO]]] => HttpRoutes[IO] =
    route => handleErrors(extractToken(ContextRoutes.of[BearerToken, IO](route)))

  def noauth: PartialFunction[Request[IO], IO[Response[IO]]] => HttpRoutes[IO] =
    route => handleErrors(HttpRoutes.of[IO](route))

  private def extractToken: ContextMiddleware[IO, BearerToken] = (routes: ContextRoutes[BearerToken, IO]) =>
    Kleisli { req: Request[IO] =>
      val parseTokenResult = for {
        auth <- req.headers.get[Authorization].toRight(())
        token <- Authorization.parse(auth.value) match {
          case Right(Authorization(Credentials.Token(AuthScheme.Bearer, token))) =>
            Right(BearerToken(token))
          case _ =>
            Left(())
        }
      } yield token

      parseTokenResult match {
        case Left(_) =>
          OptionT.some(Response[IO](Status.Forbidden))
        case Right(token) =>
          OptionT(
            routes
              .run(ContextRequest(token, req))
              .value
              .recoverWith { case UnexpectedStatus(status, _, _) =>
                IO(Some(Response[IO](status)))
              }
          )
      }
    }

  private def handleErrors: HttpMiddleware[IO] = (routes: HttpRoutes[IO]) =>
    Kleisli { req: Request[IO] =>
      OptionT(
        routes(req).value
          .recoverWith { case error @ InvalidMessageBodyFailure(details, cause) =>
            logger.warn(s"bad request: $details", cause)
            IO(Some(error.toHttpResponse(HttpVersion.`HTTP/1.1`)))
          }
      )
    }
}
