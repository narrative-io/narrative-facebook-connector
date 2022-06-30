package io.narrative.connectors.facebook.domain

import cats.{Eq, Show}
import doobie.Meta
import doobie.postgres.implicits._
import io.circe.{Decoder, Encoder}

import java.time.Instant
import java.util.UUID

final case class Profile(
    id: Profile.Id,
    adAccount: AdAccount,
    audience: Option[Audience],
    business: Business,
    createdAt: Instant,
    companyId: CompanyId,
    token: Token,
    updatedAt: Instant
)
object Profile {
  final case class Id(value: UUID) extends AnyVal
  object Id {
    implicit val decoder: Decoder[Id] = Decoder.decodeUUID.map(Id.apply)
    implicit val encoder: Encoder[Id] = Encoder.encodeUUID.contramap(_.value)
    implicit val eq: Eq[Id] = Eq.fromUniversalEquals
    implicit val meta: Meta[Id] = Meta[UUID].imap(Id.apply)(_.value)
    implicit val show: Show[Id] = Show.show(_.value.toString)
  }
}