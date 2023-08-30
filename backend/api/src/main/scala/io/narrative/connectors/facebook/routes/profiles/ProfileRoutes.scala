package io.narrative.connectors.facebook.routes.profiles

import cats.data.{EitherT, OptionT}
import cats.effect.IO
import cats.syntax.show._
import com.typesafe.scalalogging.LazyLogging
import io.narrative.connectors.facebook.domain.Profile
import io.narrative.connectors.facebook.routes.Auth
import io.narrative.connectors.facebook.services.{ApiPaginatedResult, BearerToken, CreateProfileRequest, ProfileService}
import org.http4s._
import org.http4s.circe.CirceEntityCodec._
import org.http4s.dsl.io._

class ProfileRoutes(service: ProfileService.Ops[IO]) extends LazyLogging {

  val routes: HttpRoutes[IO] = Auth.auth {
    case GET -> Root as auth                            => profiles(auth)
    case GET -> Root / UUIDVar(id) as auth              => profile(auth, Profile.Id(id))
    case ctx @ POST -> Root as auth                     => createProfile(auth, ctx.req)
    case _ @POST -> Root / "disconnect" as auth         => disconnectProfiles(auth)
    case POST -> Root / UUIDVar(id) / "archive" as auth => archiveProfile(auth, Profile.Id(id))
    case POST -> Root / UUIDVar(id) / "enable" as auth  => enableProfile(auth, Profile.Id(id))
  }

  private def archiveProfile(auth: BearerToken, id: Profile.Id): IO[Response[IO]] =
    OptionT(service.archive(auth, id)).semiflatMap(Ok(_)).getOrElseF(NotFound())

  private def createProfile(auth: BearerToken, req: Request[IO]): IO[Response[IO]] =
    for {
      createReq <- req.as[CreateProfileRequest]
      resp <- EitherT(service.create(auth, createReq)).foldF(err => BadRequest(err.show), Created(_))
    } yield resp

  private def disconnectProfiles(auth: BearerToken): IO[Response[IO]] = service.disconnect(auth).flatMap(Ok(_))

  private def enableProfile(auth: BearerToken, id: Profile.Id): IO[Response[IO]] =
    EitherT(service.enable(auth, id)).foldF(
      {
        case ProfileService.EnableError.NoSuchProfile(_) => NotFound()
        case err                                         => BadRequest(err.show)
      },
      Ok(_)
    )

  private def profile(auth: BearerToken, id: Profile.Id): IO[Response[IO]] =
    OptionT(service.profile(auth, id)).semiflatMap(Ok(_)).getOrElseF(NotFound())

  private def profiles(auth: BearerToken): IO[Response[IO]] =
    for {
      profiles <- service.profiles(auth)
      resp <- Ok(ApiPaginatedResult(profiles))
    } yield resp
}
