package io.narrative.connectors.facebook.services

import cats.{Eq, Show}
import cats.effect._
import cats.syntax.applicativeError._
import io.circe.{Decoder, Encoder}
import io.circe.generic.extras.semiauto.{deriveConfiguredDecoder, deriveConfiguredEncoder}
import org.http4s.circe.CirceEntityCodec._
import org.http4s._
import org.http4s.client.{Client, UnexpectedStatus}
import org.http4s.headers.Authorization
import io.narrative.connectors.facebook.domain.Profile

class ApiClient(client: Client[IO], baseUri: Uri)(implicit contextShift: ContextShift[IO]) extends ApiClient.Ops[IO] {
  import ApiClient._

  override def company(token: BearerToken): IO[ApiCompany] = {
    val url = baseUri.addSegment("company-info").addSegment("whoami")
    val request = Request[IO](method = Method.GET, uri = url).withHeaders(authHeader(token))
    client.expect[ApiCompany](request)
  }

  override def installation(token: BearerToken): IO[ApiInstallation] = {
    val url = baseUri.addSegment("installations").addSegment("whoami")
    val request = Request[IO](method = Method.GET, uri = url).withHeaders(authHeader(token))
    client.expect[ApiInstallation](request)
  }

  override def profile(token: BearerToken)(id: Profile.Id): IO[Option[ApiProfile]] =
    installation(token).flatMap { info =>
      val url =
        baseUri.addSegment("installations").addSegment(info.id.toString).addSegment("profiles").addSegment(id.toString)
      val request = Request[IO](method = Method.GET, uri = url).withHeaders(authHeader(token))
      client
        .expect[ApiProfile](request)
        .map(Option.apply)
        .recoverWith { case UnexpectedStatus(Status.NotFound, _, _) =>
          IO(Option.empty[ApiProfile])
        }
    }

  override def profiles(token: BearerToken): IO[List[ApiProfile]] =
    installation(token).flatMap { info =>
      val url = baseUri.addSegment("installations").addSegment(info.id.toString).addSegment("profiles")
      val request = Request[IO](method = Method.GET, uri = url).withHeaders(authHeader(token))
      client.expect[ApiPaginatedResult[ApiProfile]](request).map(_.records)
    }

  override def archiveProfile(token: BearerToken)(id: Profile.Id): IO[ApiProfile] =
    installation(token).flatMap { info =>
      val url =
        baseUri
          .addSegment("installations")
          .addSegment(info.id.toString)
          .addSegment("profiles")
          .addSegment(id.value.toString)
          .addSegment("archive")
      val request = Request[IO](method = Method.POST, uri = url).withHeaders(authHeader(token))
      client.expect[ApiProfile](request)
    }

  override def createProfile(token: BearerToken)(name: String, description: Option[String]): IO[ApiProfile] =
    installation(token).flatMap { info =>
      val url = baseUri.addSegment("installations").addSegment(info.id.toString).addSegment("profiles")
      val entity = NewProfileRequest(name, description)
      val request = Request[IO](method = Method.POST, uri = url).withEntity(entity).withHeaders(authHeader(token))
      client.expect[ApiProfile](request)
    }

  override def disableProfile(token: BearerToken)(id: Profile.Id): IO[ApiProfile] =
    installation(token).flatMap { info =>
      val url =
        baseUri
          .addSegment("installations")
          .addSegment(info.id.toString)
          .addSegment("profiles")
          .addSegment(id.value.toString)
          .addSegment("disable")
      val request = Request[IO](method = Method.POST, uri = url).withHeaders(authHeader(token))
      client.expect[ApiProfile](request)
    }

  override def enableProfile(token: BearerToken)(id: Profile.Id): IO[ApiProfile] =
    installation(token).flatMap { info =>
      val url =
        baseUri
          .addSegment("installations")
          .addSegment(info.id.toString)
          .addSegment("profiles")
          .addSegment(id.value.toString)
          .addSegment("enable")
      val request = Request[IO](method = Method.POST, uri = url).withHeaders(authHeader(token))
      client.expect[ApiProfile](request)
    }

  override def updateProfile(
      token: BearerToken
  )(id: Profile.Id, name: String, description: Option[String]): IO[ApiProfile] =
    installation(token).flatMap { info =>
      val url =
        baseUri.addSegment("installations").addSegment(info.id.toString).addSegment("profiles").addSegment(id.toString)
      val entity = UpdateProfileRequest(name, description)
      val request = Request[IO](method = Method.PUT, uri = url).withEntity(entity).withHeaders(authHeader(token))
      client.expect[ApiProfile](request)
    }
}

object ApiClient {
  trait ReadOps[F[_]] {
    def company(token: BearerToken): F[ApiCompany]
    def installation(token: BearerToken): F[ApiInstallation]
    def profile(token: BearerToken)(id: Profile.Id): F[Option[ApiProfile]]
    def profiles(token: BearerToken): F[List[ApiProfile]]
  }

  trait WriteOps[F[_]] {
    def archiveProfile(token: BearerToken)(id: Profile.Id): F[ApiProfile]
    def createProfile(token: BearerToken)(name: String, description: Option[String]): F[ApiProfile]
    def disableProfile(token: BearerToken)(id: Profile.Id): F[ApiProfile]
    def enableProfile(token: BearerToken)(id: Profile.Id): F[ApiProfile]
    def updateProfile(token: BearerToken)(id: Profile.Id, name: String, description: Option[String]): F[ApiProfile]
  }

  trait Ops[F[_]] extends ReadOps[F] with WriteOps[F]

  private final case class NewProfileRequest(name: String, description: Option[String])
  private object NewProfileRequest {
    import io.narrative.connectors.facebook.codecs.CirceConfig._

    implicit val decoder: Decoder[NewProfileRequest] = deriveConfiguredDecoder
    implicit val encoder: Encoder[NewProfileRequest] = deriveConfiguredEncoder
    implicit val eq: Eq[NewProfileRequest] = Eq.fromUniversalEquals
    implicit val show: Show[NewProfileRequest] = Show.fromToString

  }
  private final case class UpdateProfileRequest(name: String, description: Option[String])
  private object UpdateProfileRequest {
    import io.narrative.connectors.facebook.codecs.CirceConfig._

    implicit val decoder: Decoder[UpdateProfileRequest] = deriveConfiguredDecoder
    implicit val encoder: Encoder[UpdateProfileRequest] = deriveConfiguredEncoder
    implicit val eq: Eq[UpdateProfileRequest] = Eq.fromUniversalEquals
    implicit val show: Show[UpdateProfileRequest] = Show.fromToString
  }

  private def authHeader(token: BearerToken): Authorization = Authorization(
    Credentials.Token(AuthScheme.Bearer, token.value)
  )
}
