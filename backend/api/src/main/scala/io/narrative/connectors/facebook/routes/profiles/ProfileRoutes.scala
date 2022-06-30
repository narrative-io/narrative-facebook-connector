package io.narrative.connectors.facebook.routes.profiles

import cats.data.OptionT
import cats.effect.{ContextShift, IO}
import com.typesafe.scalalogging.LazyLogging
import io.narrative.connectors.facebook.domain.Profile
import io.narrative.connectors.facebook.routes.Auth
import io.narrative.connectors.facebook.services.{
  ApiClient,
  ApiCompany,
  ApiPaginatedResult,
  BearerToken,
  CreateProfileRequest,
  ProfileService
}
import org.http4s._
import org.http4s.circe.CirceEntityCodec._
import org.http4s.dsl.io._

class ProfileRoutes(apiClient: ApiClient.Ops[IO], service: ProfileService.Ops[IO])(implicit
    contextShift: ContextShift[IO]
) extends LazyLogging {

  val routes: HttpRoutes[IO] = Auth.auth {
    case GET -> Root as auth               => profiles(auth)
    case GET -> Root / UUIDVar(id) as auth => profile(auth, Profile.Id(id))
    case ctx @ POST -> Root as auth        => createProfile(auth, ctx.req)
  }

  private def profiles(auth: BearerToken): IO[Response[IO]] =
    for {
      c <- company(auth)
      profiles <- service.profiles(c.id)
      resp <- Ok(ApiPaginatedResult(profiles))
    } yield resp

  private def profile(auth: BearerToken, id: Profile.Id): IO[Response[IO]] = {
    val profileT = for {
      c <- OptionT.liftF(company(auth))
      profile <- OptionT(service.profile(c.id, id))
    } yield profile

    profileT.semiflatMap(Ok(_)).getOrElseF(NotFound())
  }

  private def createProfile(auth: BearerToken, req: Request[IO]): IO[Response[IO]] =
    for {
      c <- company(auth)
      createReq <- req.as[CreateProfileRequest]
      profile <- service.create(c.id, createReq)
      resp <- Created(profile)
    } yield resp

  private def company(auth: BearerToken): IO[ApiCompany] = apiClient.company(auth)
}
