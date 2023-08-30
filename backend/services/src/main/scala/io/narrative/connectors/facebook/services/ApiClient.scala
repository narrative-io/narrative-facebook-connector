package io.narrative.connectors.facebook.services

import cats.{Eq, Show}
import cats.effect.IO
import cats.implicits._
import io.circe.{Decoder, Encoder}
import io.circe.generic.extras.semiauto.{deriveConfiguredDecoder, deriveConfiguredEncoder}
import org.http4s.circe.CirceEntityCodec._
import org.http4s._
import org.http4s.client.{Client, UnexpectedStatus}
import org.http4s.headers.Authorization
import io.narrative.connectors.facebook.domain.Profile

/** todo(mbabic) scaladocs */
class ApiClient(baseUri: Uri, client: Client[IO]) extends ApiClient.Ops[IO] {
  import ApiClient._

  override def company(auth: BearerToken): IO[ApiCompany] = {
    val url = baseUri.addSegment("company-info").addSegment("whoami")
    val request = Request[IO](method = Method.GET, uri = url).withHeaders(authHeader(auth))
    client.expect[ApiCompany](request)
  }

  override def installation(auth: BearerToken): IO[ApiInstallation] = {
    val url = baseUri.addSegment("installations").addSegment("whoami")
    val request = Request[IO](method = Method.GET, uri = url).withHeaders(authHeader(auth))
    client.expect[ApiInstallation](request)
  }

  override def profile(auth: BearerToken, id: Profile.Id): IO[Option[ApiProfile]] =
    installation(auth).flatMap { info =>
      val url =
        baseUri
          .addSegment("installations")
          .addSegment(info.id.value.toString)
          .addSegment("profiles")
          .addSegment(id.value.toString)
      val request = Request[IO](method = Method.GET, uri = url).withHeaders(authHeader(auth))
      client
        .expect[ApiProfile](request)
        .map(Option.apply)
        .recoverWith { case UnexpectedStatus(Status.NotFound, _, _) =>
          IO.pure(Option.empty[ApiProfile])
        }
    }

  override def profiles(auth: BearerToken): IO[List[ApiProfile]] =
    installation(auth).flatMap { info =>
      val url = baseUri.addSegment("installations").addSegment(info.id.value.toString).addSegment("profiles")
      val request = Request[IO](method = Method.GET, uri = url).withHeaders(authHeader(auth))
      client.expect[ApiPaginatedResult[ApiProfile]](request).map(_.records)
    }

  override def archiveProfile(auth: BearerToken, id: Profile.Id): IO[Option[ApiProfile]] =
    installation(auth).flatMap { info =>
      val url =
        baseUri
          .addSegment("installations")
          .addSegment(info.id.value.toString)
          .addSegment("profiles")
          .addSegment(id.value.toString)
          .addSegment("archive")
      val request = Request[IO](method = Method.POST, uri = url).withHeaders(authHeader(auth))
      client
        .expect[ApiProfile](request)
        .map(Option.apply)
        .recoverWith { case UnexpectedStatus(Status.NotFound, _, _) =>
          IO.pure(Option.empty[ApiProfile])
        }
    }

  override def createProfile(auth: BearerToken, name: String, description: Option[String]): IO[ApiProfile] =
    installation(auth).flatMap { info =>
      val url = baseUri.addSegment("installations").addSegment(info.id.show).addSegment("profiles")
      val entity = NewProfileRequest(name, description)
      val request = Request[IO](method = Method.POST, uri = url).withEntity(entity).withHeaders(authHeader(auth))
      client.expect[ApiProfile](request)
    }

  override def disableProfile(auth: BearerToken, id: Profile.Id): IO[Option[ApiProfile]] =
    installation(auth).flatMap { info =>
      val url =
        baseUri
          .addSegment("installations")
          .addSegment(info.id.value.toString)
          .addSegment("profiles")
          .addSegment(id.value.toString)
          .addSegment("disable")
      val request = Request[IO](method = Method.POST, uri = url).withHeaders(authHeader(auth))
      client
        .expect[ApiProfile](request)
        .map(Option.apply)
        .recoverWith { case UnexpectedStatus(Status.NotFound, _, _) =>
          IO.pure(Option.empty[ApiProfile])
        }
    }

  override def enableProfile(auth: BearerToken, id: Profile.Id): IO[Option[ApiProfile]] =
    installation(auth).flatMap { info =>
      val url =
        baseUri
          .addSegment("installations")
          .addSegment(info.id.value.toString)
          .addSegment("profiles")
          .addSegment(id.value.toString)
          .addSegment("enable")
      val request = Request[IO](method = Method.POST, uri = url).withHeaders(authHeader(auth))
      client
        .expect[ApiProfile](request)
        .map(Option.apply)
        .recoverWith { case UnexpectedStatus(Status.NotFound, _, _) =>
          IO.pure(Option.empty[ApiProfile])
        }
    }

  override def updateProfile(
      auth: BearerToken,
      id: Profile.Id,
      name: String,
      description: Option[String]
  ): IO[Option[ApiProfile]] =
    installation(auth).flatMap { info =>
      val url =
        baseUri
          .addSegment("installations")
          .addSegment(info.id.value.toString)
          .addSegment("profiles")
          .addSegment(id.value.toString)
      val entity = UpdateProfileRequest(name, description)
      val request = Request[IO](method = Method.PUT, uri = url).withEntity(entity).withHeaders(authHeader(auth))
      client
        .expect[ApiProfile](request)
        .map(Option.apply)
        .recoverWith { case UnexpectedStatus(Status.NotFound, _, _) =>
          IO.pure(Option.empty[ApiProfile])
        }
    }
}

object ApiClient {
  trait ReadOps[F[_]] {
    def company(auth: BearerToken): F[ApiCompany]
    def installation(auth: BearerToken): F[ApiInstallation]
    def profile(auth: BearerToken, id: Profile.Id): F[Option[ApiProfile]]
    def profiles(auth: BearerToken): F[List[ApiProfile]]
  }

  trait WriteOps[F[_]] {
    def archiveProfile(auth: BearerToken, id: Profile.Id): F[Option[ApiProfile]]
    def createProfile(auth: BearerToken, name: String, description: Option[String]): F[ApiProfile]
    def disableProfile(auth: BearerToken, id: Profile.Id): F[Option[ApiProfile]]
    def enableProfile(auth: BearerToken, id: Profile.Id): F[Option[ApiProfile]]
    def updateProfile(
        auth: BearerToken,
        id: Profile.Id,
        name: String,
        description: Option[String]
    ): F[Option[ApiProfile]]
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

  private def authHeader(auth: BearerToken): Authorization =
    Authorization(Credentials.Token(AuthScheme.Bearer, auth.value))
}
