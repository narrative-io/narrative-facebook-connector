package io.narrative.connectors.facebook.stores

import cats.arrow.FunctionK
import cats.data.OptionT
import cats.effect.IO
import cats.syntax.functor._
import cats.syntax.list._
import cats.syntax.option._
import cats.syntax.show._
import doobie.{ConnectionIO, Fragment, Transactor}
import doobie.implicits._
import doobie.implicits.legacy.instant._
import doobie.util.fragments.{in, whereAnd, whereAndOpt}
import io.narrative.connectors.facebook.domain.{AdAccount, Audience, Business, CompanyId, FacebookUser, Profile, Token}

import java.time.Instant

class ProfileStore() extends ProfileStore.Ops[ConnectionIO] {
  override def profile(id: Profile.Id): ConnectionIO[Option[Profile]] =
    profiles(whereAnd(fr"id = ${id}")).map(_.headOption)

  override def profiles(query: ProfileStore.Query): ConnectionIO[List[Profile]] =
    profiles(
      whereAndOpt(
        query.companyIds.toNel.map(ids => in(fr"company_id", ids)),
        query.profileIds.toNel.map(ids => in(fr"id", ids))
      )
    )

  override def create(
      id: Profile.Id,
      adAccount: AdAccount,
      audience: Option[Audience],
      business: Business,
      companyId: CompanyId,
      token: Token
  ): ConnectionIO[Profile] =
    for {
      _ <-
        sql"""
           |insert into profiles (
           |  id,
           |  ad_account_id,
           |  ad_account_name,
           |  audience_id,
           |  audience_name,
           |  business_id,
           |  business_name,
           |  company_id,
           |  created_at,
           |  token_encrypted,
           |  token_issued_at,
           |  user_id,
           |  user_name,
           |  updated_at
           |) values (
           |  ${id},
           |  ${adAccount.id},
           |  ${adAccount.name},
           |  ${audience.map(_.id)},
           |  ${audience.map(_.name)},
           |  ${business.id},
           |  ${business.name},
           |  ${companyId},
           |  now() at time zone 'utc',
           |  ${token.encrypted},
           |  ${token.issuedAt},
           |  ${token.user.id},
           |  ${token.user.name},
           |  now() at time zone 'utc'
           |)""".stripMargin.update.run.void
      updated <- profile_!(id)
    } yield updated

  private def profiles(where: Fragment): ConnectionIO[List[Profile]] =
    (
      fr"""
          |select
          |  id,
          |  ad_account_id,
          |  ad_account_name,
          |  audience_id,
          |  audience_name,
          |  business_id,
          |  business_name,
          |  company_id,
          |  created_at,
          |  token_encrypted,
          |  token_issued_at,
          |  user_id,
          |  user_name,
          |  updated_at
          |from profiles
          |""".stripMargin ++ where
    ).query[
      (
          Profile.Id,
          AdAccount.Id,
          AdAccount.Name,
          Option[Audience.Id],
          Option[Audience.Name],
          Business.Id,
          Business.Name,
          CompanyId,
          Instant,
          Token.Encrypted,
          Instant,
          FacebookUser.Id,
          FacebookUser.Name,
          Instant
      )
    ].map {
      case (
            id,
            adAccountId,
            adAccountName,
            audienceId,
            audienceName,
            businessId,
            businessName,
            companyId,
            createdAt,
            tokenEncrypted,
            tokenIssuedAt,
            userId,
            userName,
            updatedAt
          ) =>
        Profile(
          id = id,
          adAccount = AdAccount(id = adAccountId, name = adAccountName),
          audience = (audienceId, audienceName) match {
            case (Some(aid), Some(aname)) => Audience(id = aid, name = aname).some
            case (None, None)             => none
            case (maybeId, maybeName) =>
              throw new RuntimeException(
                s"invariant violated: profile ${id.show} in invalid state, either both audience id and name must be specified or neither should be set. audience_id=${maybeId}, audience_name=${maybeName}"
              )
          },
          business = Business(id = businessId, name = businessName),
          companyId = companyId,
          createdAt = createdAt,
          token = Token(
            encrypted = tokenEncrypted,
            issuedAt = tokenIssuedAt,
            user = FacebookUser(id = userId, name = userName)
          ),
          updatedAt = updatedAt
        )
    }.to[List]

  private def profile_!(id: Profile.Id): ConnectionIO[Profile] =
    OptionT(profile(id)).getOrRaise(
      new RuntimeException(
        s"invariant violated: could not find profile with id ${id.show} when we required that it exists (e.g. after creation or update)"
      )
    )
}

object ProfileStore {
  trait ReadOps[F[_]] {
    def profile(id: Profile.Id): F[Option[Profile]]
    def profiles(query: Query): F[List[Profile]]
  }

  trait WriteOps[F[_]] {
    def create(
        id: Profile.Id,
        adAccount: AdAccount,
        audience: Option[Audience],
        business: Business,
        companyId: CompanyId,
        token: Token
    ): F[Profile]
  }

  trait Ops[F[_]] extends ReadOps[F] with WriteOps[F]

  def apply(xa: Transactor[IO]): ProfileStore.Ops[IO] = apply(
    new ProfileStore(),
    new FunctionK[ConnectionIO, IO] {
      override def apply[A](fa: ConnectionIO[A]): IO[A] = fa.transact(xa)
    }
  )

  def apply[F[_], G[_]](store: ProfileStore.Ops[F], fk: FunctionK[F, G]): ProfileStore.Ops[G] =
    new ProfileStore.Ops[G] {
      override def profile(id: Profile.Id): G[Option[Profile]] = fk(store.profile(id))

      override def profiles(query: Query): G[List[Profile]] = fk(store.profiles(query))

      override def create(
          id: Profile.Id,
          adAccount: AdAccount,
          audience: Option[Audience],
          business: Business,
          companyId: CompanyId,
          token: Token
      ): G[Profile] = fk(store.create(id, adAccount, audience, business, companyId, token))
    }

  final case class Query(companyIds: List[CompanyId] = List.empty, profileIds: List[Profile.Id] = List.empty)
}
