package io.narrative.connectors.facebook.services

import cats.data.OptionT
import cats.instances.list._
import cats.syntax.option._
import cats.syntax.show._
import cats.syntax.traverse._
import cats.effect.IO
import io.narrative.connectors.facebook.domain.{AdAccount, Business, CompanyId, FacebookUser, Profile, Token}
import io.narrative.connectors.facebook.service.FacebookClient
import io.narrative.connectors.facebook.stores.ProfileStore

class ProfileService(
    fb: FacebookClient.Ops[IO],
    encryption: TokenEncryptionService.Ops[IO],
    store: ProfileStore.Ops[IO]
) extends ProfileService.Ops[IO] {

  override def meta(token: FacebookToken): IO[TokenMetaResponse] =
    for {
      meta <- fb.tokenMeta(token)
      adAccounts <- if (meta.isValid) fb.adAccounts(token) else IO.pure(List.empty[AdAccountResponse])
    } yield TokenMetaResponse(adAccounts = adAccounts, token = meta)

  override def profile(companyId: CompanyId, id: Profile.Id): IO[Option[ProfileResponse]] =
    OptionT(store.profile(id)).semiflatMap(profileResponse).value

  override def profiles(companyId: CompanyId): IO[List[ProfileResponse]] = {
    for {
      stored <- store.profiles(ProfileStore.Query(companyIds = List(companyId)))
      profileResponses <- stored.traverse(profileResponse)
    } yield profileResponses
  }

  // todo(mbabic) proper error handling
  // todo(mbabic) validate token has proper scopes
  override def create(companyId: CompanyId, req: CreateProfileRequest): IO[ProfileResponse] =
    for {
      longLived <- fb.exchangeForLongLivedToken(req.token)
      meta <- fb.tokenMeta(longLived)
      encrypted <- encryption.encrypt(longLived)
      adAccount <- OptionT(fb.adAccount(longLived, req.adAccountId))
        .getOrRaise(new IllegalArgumentException(s"cannot find ad account with id ${req.adAccountId.show}"))
      _ = assertSupportsCustomAudiences_!(adAccount)
      business = business_!(adAccount)
      token = validToken_!(encrypted, meta)

      profile <- store.create(
        adAccount = AdAccount(id = adAccount.id, name = adAccount.name),
        audience = none, // todo(mbabic)
        business = business,
        companyId = companyId,
        token = Token(encrypted = encrypted, issuedAt = meta.issuedAt.get, user = token.user)
      )
    } yield ProfileResponse(
      id = profile.id,
      adAccount = adAccount,
      audience = none,
      createdAt = profile.createdAt,
      companyId = profile.companyId,
      token = meta,
      updatedAt = profile.updatedAt
    )

  private def profileResponse(profile: Profile): IO[ProfileResponse] =
    for {
      token <- encryption.decrypt(profile.token.encrypted)
      meta <- fb.tokenMeta(token)
    } yield ProfileResponse(
      id = profile.id,
      adAccount = AdAccountResponse(
        id = profile.adAccount.id,
        business = BusinessResponse(id = profile.business.id, name = profile.business.name).some,
        name = profile.adAccount.name,
        supportsCustomAudiences = true, // todo(mbabic) re-validate
        userAcceptedCustomAudienceTos = true // todo(mbabic) re-validate
      ),
      audience = none, // todo(mbabic)
      createdAt = profile.createdAt,
      companyId = profile.companyId,
      token = meta,
      updatedAt = profile.createdAt
    )

  private def assertSupportsCustomAudiences_!(adAccount: AdAccountResponse): Unit =
    if (adAccount.supportsCustomAudiences)
      ()
    else
      throw new IllegalArgumentException(
        s"ad account ${adAccount.name.show} (id=${adAccount.id.show}) does not support custom audiences"
      )

  private def business_!(adAccount: AdAccountResponse): Business =
    adAccount.business
      .map(b => Business(id = b.id, name = b.name))
      .getOrElse(
        throw new IllegalArgumentException(s"add accounts must be ")
      )

  private def validToken_!(encrypted: Token.Encrypted, meta: TokenMeta): Token =
    if (meta.isValid)
      Token(
        encrypted = encrypted,
        issuedAt = meta.issuedAt.get,
        user = FacebookUser(id = meta.user.get.id, name = meta.user.get.name)
      )
    else
      throw new IllegalArgumentException(s"cannot create profile: token no longer valid")
}

object ProfileService {
  trait ReadOps[F[_]] {
    def meta(token: FacebookToken): F[TokenMetaResponse]
    def profile(companyId: CompanyId, id: Profile.Id): F[Option[ProfileResponse]]
    def profiles(companyId: CompanyId): F[List[ProfileResponse]]
  }

  trait WriteOps[F[_]] {
    def create(companyId: CompanyId, req: CreateProfileRequest): F[ProfileResponse]
  }

  trait Ops[F[_]] extends ReadOps[F] with WriteOps[F]
}
