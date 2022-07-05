package io.narrative.connectors.facebook.domain

import cats.{Eq, Show}
import doobie.Meta
import doobie.postgres.implicits._
import io.circe.{Decoder, Encoder}

import java.util.UUID

final case class SubscriptionId(value: UUID) extends AnyVal
object SubscriptionId {
  implicit val decoder: Decoder[SubscriptionId] = Decoder.decodeUUID.map(SubscriptionId.apply)
  implicit val encoder: Encoder[SubscriptionId] = Encoder.encodeUUID.contramap(_.value)
  implicit val eq: Eq[SubscriptionId] = Eq.fromUniversalEquals
  implicit val meta: Meta[SubscriptionId] = Meta[UUID].imap(SubscriptionId.apply)(_.value)
  implicit val show: Show[SubscriptionId] = Show.show(_.value.toString)
}
