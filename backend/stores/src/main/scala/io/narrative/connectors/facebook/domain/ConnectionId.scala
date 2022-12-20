package io.narrative.connectors.facebook.domain

import cats.{Eq, Show}
import doobie.Meta
import doobie.postgres.implicits._
import io.circe.{Decoder, Encoder}
import java.util.UUID
final case class ConnectionId(value: UUID) extends AnyVal
object ConnectionId {
  implicit val decoder: Decoder[ConnectionId] = Decoder.decodeUUID.map(ConnectionId.apply)
  implicit val encoder: Encoder[ConnectionId] = Encoder.encodeUUID.contramap(_.value)
  implicit val eq: Eq[ConnectionId] = Eq.fromUniversalEquals
  implicit val meta: Meta[ConnectionId] = Meta[UUID].imap(ConnectionId.apply)(_.value)
  implicit val show: Show[ConnectionId] = Show.show(_.value.toString)
}
