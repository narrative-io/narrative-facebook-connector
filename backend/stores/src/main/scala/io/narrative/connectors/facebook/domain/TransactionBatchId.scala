package io.narrative.connectors.facebook.domain

import cats.{Eq, Show}
import doobie.Meta
import doobie.postgres.implicits._
import io.circe.{Decoder, Encoder}

import java.util.UUID

final case class TransactionBatchId(value: UUID) extends AnyVal
object TransactionBatchId {
  implicit val decoder: Decoder[TransactionBatchId] = Decoder.decodeUUID.map(TransactionBatchId.apply)
  implicit val encoder: Encoder[TransactionBatchId] = Encoder.encodeUUID.contramap(_.value)
  implicit val eq: Eq[TransactionBatchId] = Eq.fromUniversalEquals
  implicit val meta: Meta[TransactionBatchId] = Meta[UUID].imap(TransactionBatchId.apply)(_.value)
  implicit val show: Show[TransactionBatchId] = Show.show(_.value.toString)
}
