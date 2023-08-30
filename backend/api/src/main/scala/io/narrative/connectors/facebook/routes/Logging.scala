package io.narrative.connectors.facebook.routes

import cats.effect._
import cats.syntax.option._
import com.typesafe.scalalogging.LazyLogging
import org.http4s.{HttpApp, Response}
import org.http4s.server.middleware._

object Logging extends LazyLogging {
  def apply(routes: HttpApp[IO]): HttpApp[IO] = {
    // The order that these middleware are stacked matters. Any other combination and the X-Request-ID header won't
    // propagate to the response when the server throws an exception.
    errorResponseLogging {
      RequestId.httpApp {
        // NB: logBody = false as users send us unencrypted Facebook tokens in requests
        RequestLogger.httpApp(logHeaders = true, logBody = false) {
          ErrorHandling(routes)
        }
      }
    }
  }

  def errorResponseLogging(service: HttpApp[IO]): HttpApp[IO] = {
    val logInfo = (s: String) => IO(logger.info(s))
    val logWarn = (s: String) => IO(logger.warn(s))
    HttpApp[IO] { req =>
      for {
        resp <- service(req)
        _ <- resp.status.code match {
          case code if code >= 400 =>
            org.http4s.internal.Logger.logMessageWithBodyText[IO, Response[IO]](resp)(
              logHeaders = true,
              logBodyText = truncateBody(32768) _
            )(logWarn)
          case _ =>
            Logger.logMessage[IO, Response[IO]](resp)(logHeaders = true, logBody = false)(logInfo)
        }
      } yield resp
    }
  }

  private def truncateBody(maxBytes: Int)(bytes: fs2.Stream[IO, Byte]): Option[IO[String]] =
    bytes.take(maxBytes.toLong).through(fs2.text.utf8Decode).compile.last.map(_.getOrElse("")).some
}
