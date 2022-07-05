package io.narrative.connectors.facebook.services

import cats.Show
import cats.data.{EitherT, OptionT}
import cats.instances.list._
import cats.syntax.either._
import cats.syntax.eq._
import cats.syntax.option._
import cats.syntax.show._
import cats.syntax.traverse._
import cats.effect.IO
import io.narrative.connectors.facebook.domain.{AdAccount, Business, FacebookUser, Profile, Token}
import io.narrative.connectors.facebook.services.TokenMeta.Scope
import io.narrative.connectors.facebook.stores.ProfileStore

class ProfileService(
    api: ApiClient.Ops[IO],
    fb: FacebookClient.Ops[IO],
    encryption: TokenEncryptionService.Ops[IO],
    store: ProfileStore.Ops[IO]
) extends ProfileService.Ops[IO] {

  import ProfileService._

  override def meta(token: FacebookToken): IO[TokenMetaResponse] =
    for {
      meta <- fb.tokenMeta(token)
      adAccounts <- meta match {
        case TokenMeta.Valid(_, scopes, _) if scopes.contains(Scope.AdsManagement) =>
          fb.adAccounts(token)
        case _ =>
          // cannot fetch company ad accounts with the "ads_management" scope
          IO.pure(List.empty)
      }
    } yield TokenMetaResponse(adAccounts = adAccounts, token = meta)

  override def profile(auth: BearerToken, id: Profile.Id): IO[Option[ProfileResponse]] = {
    val profileT = for {
      apiProfile <- OptionT(api.profile(auth, id))
      storedProfile <- OptionT(store.profile(id))
      response <- OptionT.liftF(profileResponse(apiProfile, storedProfile))
    } yield response

    profileT.value
  }

  override def profiles(auth: BearerToken): IO[List[ProfileResponse]] = {
    for {
      company <- api.company(auth)
      apiProfiles <- api.profiles(auth)
      // nb: including company id in query to prevent fetching all profiles when `apiProfiles` is empty
      storedProfiles <- store.profiles(
        ProfileStore.Query(companyIds = List(company.id), profileIds = apiProfiles.map(_.id))
      )
      profileResponses <- apiProfiles
        .map(apiProfile => (apiProfile, storedProfiles.find(_.id === apiProfile.id)))
        // todo(mbabic) silently dropping api profiles for which no stored profile exists
        .collect { case (apiProfile, Some(storedProfile)) => (apiProfile, storedProfile) }
        .traverse { case (apiProfile, storedProfile) => profileResponse(apiProfile, storedProfile) }
    } yield profileResponses
  }

  override def archive(auth: BearerToken, id: Profile.Id): IO[Option[ProfileResponse]] = {
    val archiveT = for {
      apiProfile <- OptionT(api.archiveProfile(auth, id))
      stored <- OptionT(store.profile(id))
      resp <- OptionT.liftF(profileResponse(apiProfile, stored))
    } yield resp

    archiveT.value
  }

  override def create(auth: BearerToken, req: CreateProfileRequest): IO[Either[CreateError, ProfileResponse]] = {
    val createT = for {
      meta <- EitherT(fb.tokenMeta(req.token).map {
        case valid: TokenMeta.Valid =>
          val missingScopes = RequiredScopes.filterNot(valid.scopes.contains)
          if (missingScopes.isEmpty)
            valid.asRight
          else
            CreateError.missingScopes(missingScopes).asLeft
        case TokenMeta.Invalid => CreateError.invalidToken.asLeft
      })
      longLived <- EitherT.liftF(fb.exchangeForLongLivedToken(req.token))
      encrypted <- EitherT.liftF(encryption.encrypt(longLived))
      adAccount <- EitherT.fromOptionF(
        fb.adAccount(longLived, req.adAccountId),
        CreateError.invalidAdAccount(req.adAccountId, s"ad account does not exist or cannot be accessed by user")
      )
      business <- EitherT.fromOption[IO](
        adAccount.business,
        CreateError.invalidAdAccount(
          req.adAccountId,
          s"ad account does not support custom audiences as it is not associated with a business"
        )
      )

      company <- EitherT.liftF(api.company(auth))
      apiProfile <- EitherT.liftF(api.createProfile(auth, req.name, req.description))
      profile <- EitherT.liftF(
        store.create(
          id = apiProfile.id,
          adAccount = AdAccount(id = adAccount.id, name = adAccount.name),
          audience = none, // todo(mbabic) support audience selection at profile creation
          business = Business(id = business.id, name = business.name),
          companyId = company.id,
          token = Token(
            encrypted = encrypted,
            issuedAt = meta.issuedAt,
            user = FacebookUser(id = meta.user.id, name = meta.user.name)
          )
        )
      )
      resp <- EitherT.liftF[IO, CreateError, ProfileResponse](profileResponse(apiProfile, profile, meta.some))
    } yield resp

    createT.value
  }

  override def enable(auth: BearerToken, id: Profile.Id): IO[Either[EnableError, ProfileResponse]] = {
    val createT = for {
      apiProfile <- EitherT.fromOptionF(api.profile(auth, id), EnableError.noSuchProfile(id))
      _ <- EitherT.cond[IO](
        apiProfile.status === ApiProfile.Status.Disabled,
        (),
        EnableError.invalidProfile(id, s"profiles with status ${apiProfile.status.show} cannot be activated")
      )
      stored <- EitherT.fromOptionF(store.profile(id), EnableError.noSuchProfile(id))
      decrypted <- EitherT.liftF(encryption.decrypt(stored.token.encrypted))
      meta <- EitherT(fb.tokenMeta(decrypted).map {
        case valid: TokenMeta.Valid => valid.asRight
        case TokenMeta.Invalid      => EnableError.invalidToken.asLeft
      })
      _ <- EitherT.fromOptionF(
        fb.adAccount(decrypted, stored.adAccount.id),
        EnableError.invalidAdAccount(stored.adAccount.id, s"ad account does not exist or cannot be accessed by user")
      )
      updated <- EitherT.fromOptionF(api.enableProfile(auth, id), EnableError.noSuchProfile(id))
      resp <- EitherT.liftF[IO, EnableError, ProfileResponse](profileResponse(updated, stored, meta.some))
    } yield resp

    createT.value
  }

  // todo(mbabic) revalidate custom audience support, support choosing audience at profile creation
  private def profileResponse(
      apiProfile: ApiProfile,
      storedProfile: Profile,
      metaOpt: Option[TokenMeta] = none
  ): IO[ProfileResponse] =
    for {
      meta <- metaOpt match {
        case Some(value) => IO.pure(value)
        case None        => encryption.decrypt(storedProfile.token.encrypted).flatMap(fb.tokenMeta)
      }
    } yield ProfileResponse(
      id = storedProfile.id,
      adAccount = AdAccountResponse(
        id = storedProfile.adAccount.id,
        business = BusinessResponse(id = storedProfile.business.id, name = storedProfile.business.name).some,
        name = storedProfile.adAccount.name,
        supportsCustomAudiences = true,
        userAcceptedCustomAudienceTos = true
      ),
      audience = none,
      createdAt = storedProfile.createdAt,
      companyId = storedProfile.companyId,
      description = apiProfile.description,
      name = apiProfile.name,
      status = apiProfile.status,
      token = meta,
      updatedAt = storedProfile.createdAt
    )
}

object ProfileService {
  private val RequiredScopes: List[Scope] = List(Scope.AdsManagement, Scope.BusinessManagement)

  trait ReadOps[F[_]] {
    def meta(token: FacebookToken): F[TokenMetaResponse]
    def profile(auth: BearerToken, id: Profile.Id): F[Option[ProfileResponse]]
    def profiles(auth: BearerToken): F[List[ProfileResponse]]
  }

  trait WriteOps[F[_]] {
    def archive(auth: BearerToken, id: Profile.Id): F[Option[ProfileResponse]]
    def create(auth: BearerToken, req: CreateProfileRequest): F[Either[CreateError, ProfileResponse]]
    def enable(auth: BearerToken, id: Profile.Id): F[Either[EnableError, ProfileResponse]]
  }

  trait Ops[F[_]] extends ReadOps[F] with WriteOps[F]

  sealed trait CreateError
  final case class InvalidAdAccount(id: AdAccount.Id, reason: String) extends CreateError with EnableError
  final object InvalidToken extends CreateError with EnableError
  object CreateError {
    final case class MissingScopes(scopes: List[Scope]) extends CreateError

    def invalidAdAccount(id: AdAccount.Id, reason: String): CreateError = InvalidAdAccount(id, reason)
    def invalidToken: CreateError = InvalidToken
    def missingScopes(missing: List[Scope]): CreateError = MissingScopes(missing)

    implicit val show: Show[CreateError] = Show.show {
      case InvalidAdAccount(id, reason) =>
        s"invalid ad account ${id.show}. ${reason}"
      case InvalidToken =>
        s"user access token has expired or been invalidated"
      case MissingScopes(scopes) =>
        s"user access token missing required permissions: ${scopes.map(Scope.asString).sorted.mkString(", ")}"
    }
  }

  sealed trait EnableError
  object EnableError {
    final case class InvalidProfile(id: Profile.Id, reason: String) extends EnableError
    final case class NoSuchProfile(id: Profile.Id) extends EnableError

    def invalidAdAccount(id: AdAccount.Id, reason: String): EnableError = InvalidAdAccount(id, reason)
    def invalidProfile(id: Profile.Id, reason: String): EnableError = InvalidProfile(id, reason)
    def invalidToken: EnableError = InvalidToken
    def noSuchProfile(id: Profile.Id): EnableError = NoSuchProfile(id)

    implicit val show: Show[EnableError] = Show.show {
      case InvalidAdAccount(id, reason) =>
        s"invalid ad account ${id.show}: ${reason}"
      case InvalidProfile(id, reason) =>
        s"invalid profile ${id.show}: ${reason}"
      case InvalidToken =>
        s"user access token has expired or been invalidated"
      case NoSuchProfile(id) =>
        s"could not find profile with id ${id.show}"
    }
  }
}
