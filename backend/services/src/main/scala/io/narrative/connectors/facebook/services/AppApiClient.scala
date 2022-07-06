package io.narrative.connectors.facebook.services

import cats.{Eq, Show}
import cats.effect.IO
import cats.effect.concurrent.Ref
import cats.syntax.applicativeError._
import cats.syntax.option._
import com.typesafe.scalalogging.LazyLogging
import fs2.Stream
import io.circe.{Decoder, Encoder}
import io.circe.generic.extras.semiauto.{deriveConfiguredDecoder, deriveConfiguredEncoder}
import io.narrative.connectors.facebook.domain.{FileName, Revision}
import org.http4s.{AuthScheme, BasicCredentials, Credentials, Method, Request, Status, Uri, UrlForm}
import org.http4s.client.{Client, UnexpectedStatus}
import org.http4s.circe.CirceEntityCodec._
import org.http4s.headers.Authorization

/** todo(mbabic) scaladocs */
class AppApiClient private (
    accessToken: Ref[IO, Option[AppApiClient.ApiToken]],
    baseUri: Uri,
    client: Client[IO],
    clientId: AppApiClient.ClientId,
    clientSecret: AppApiClient.ClientSecret
) extends AppApiClient.Ops[IO]
    with LazyLogging {

  import AppApiClient._

  override def commands(from: Revision, maxResults: Int): IO[ApiCommands] = {
    val url = baseUri
      .addSegment("v1")
      .addSegment("app")
      .addSegment("commands")
      .addSegment("delivery")
      .withQueryParam("from_revision", from.value)
      .withQueryParam("max_results", maxResults)
    withToken { token =>
      val req = Request[IO](method = Method.GET, uri = url).withHeaders(authHeader(token))
      client.expect[ApiCommands](req)
    }
  }

  override def download(revision: Revision, name: FileName): fs2.Stream[IO, Byte] = for {
    url <- fs2.Stream.eval(downloadUrl(revision, name))
    req = Request[IO](method = Method.GET, uri = url)
    bytes <- client.stream(req).flatMap { resp =>
      if (!resp.status.responseClass.isSuccess)
        Stream.raiseError[IO](UnexpectedStatus(resp.status, req.method, req.uri))
      else
        resp.body
    }
  } yield bytes

  override def files(revision: Revision): IO[List[ApiDeliveryFile]] = {
    val url = baseUri
      .addSegment("v1")
      .addSegment("app")
      .addSegment("commands")
      .addSegment("delivery")
      .addSegment(revision.value.toString)
      .addSegment("files")
    withToken { token =>
      val request = Request[IO](method = Method.GET, uri = url).withHeaders(authHeader(token))
      client.expect[List[ApiDeliveryFile]](request)
    }
  }

  private def downloadUrl(revision: Revision, name: FileName): IO[Uri] = {
    val url = baseUri
      .addSegment("v1")
      .addSegment("app")
      .addSegment("commands")
      .addSegment("delivery")
      .addSegment(revision.value.toString)
      .addSegment("download")
      .addSegment(name.value)

    withToken { token =>
      val request = Request[IO](method = Method.POST, uri = url).withHeaders(authHeader(token))
      client.expect[String](request).map(Uri.unsafeFromString)
    }
  }

  private def withToken[T](fn: ApiToken => IO[T]) = for {
    tokenOpt <- accessToken.get
    token <- tokenOpt match {
      case None    => issueNewToken()
      case Some(t) => IO(t)
    }
    response <- fn(token).recoverWith { case UnexpectedStatus(Status.Unauthorized, _, _) =>
      refreshToken(token).flatMap(newToken => fn(newToken))
    }
  } yield response

  private def issueNewToken(): IO[ApiToken] = {
    logger.info(s"issuing a new token using client id and secret")
    val url = baseUri.addSegment("oauth").addSegment("token")
    val request =
      Request[IO](method = Method.POST, uri = url)
        .withHeaders(Authorization(BasicCredentials(clientId.value, clientSecret.value)))
        .withEntity(UrlForm("grant_type" -> "client_credentials"))

    for {
      newToken <- client.expect[ApiToken](request)
      _ <- accessToken.set(newToken.some)
    } yield newToken
  }

  private def refreshToken(token: ApiToken): IO[ApiToken] = {
    logger.info(s"refreshing token using refresh token")
    val url = baseUri.addSegment("oauth").addSegment("token")
    val request =
      Request[IO](method = Method.POST, uri = url)
        .withHeaders(Authorization(BasicCredentials(clientId.value, clientSecret.value)))
        .withEntity(UrlForm("grant_type" -> "refresh_token", "refresh_token" -> token.refreshToken))

    (for {
      newToken <- client.expect[ApiToken](request)
      _ <- accessToken.set(newToken.some)
    } yield newToken).recoverWith {
      // If refresh fails, issue a new token
      case UnexpectedStatus(Status.BadRequest, _, _) => issueNewToken()
      case _                                         => issueNewToken()
    }
  }
}

object AppApiClient {
  trait ReadOps[F[_]] {
    def commands(from: Revision, maxResults: Int): F[ApiCommands]
    def download(revision: Revision, name: FileName): fs2.Stream[F, Byte]
    def files(revision: Revision): F[List[ApiDeliveryFile]]
  }

  trait Ops[F[_]] extends ReadOps[F]

  def apply(baseUri: Uri, client: Client[IO], clientId: ClientId, clientSecret: ClientSecret): IO[AppApiClient] = for {
    token <- Ref[IO].of(none[ApiToken])
  } yield new AppApiClient(
    accessToken = token,
    baseUri = baseUri,
    client = client,
    clientId = clientId,
    clientSecret = clientSecret
  )

  private final case class ApiToken(
      accessToken: String,
      accessTokenExpiresIn: Long,
      refreshToken: String,
      refreshTokenExpiresIn: Long
  )
  private object ApiToken {
    import io.narrative.connectors.facebook.codecs.CirceConfig._

    implicit val decoder: Decoder[ApiToken] = deriveConfiguredDecoder
    implicit val encoder: Encoder[ApiToken] = deriveConfiguredEncoder
    implicit val eq: Eq[ApiToken] = Eq.fromUniversalEquals
    implicit val show: Show[ApiToken] = Show.show(t =>
      s"ApiToken(accessToken=<redacted>, accessTokenExpiresIn=${t.accessTokenExpiresIn}, refreshToken=<redacted>, refreshTokenExpiresIn=${t.refreshTokenExpiresIn})"
    )
  }

  final case class ClientId(value: String)
  object ClientId {
    implicit val eq: Eq[ClientId] = Eq.fromUniversalEquals
    implicit val show: Show[ClientId] = Show.show(_.value)
  }

  final case class ClientSecret(value: String)
  object ClientSecret {
    implicit val eq: Eq[ClientSecret] = Eq.fromUniversalEquals
    implicit val show: Show[ClientSecret] = Show.show(_.value)
  }

  private def authHeader(token: ApiToken): Authorization =
    Authorization(Credentials.Token(AuthScheme.Bearer, token.accessToken))
}
