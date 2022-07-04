package io.narrative.connectors.facebook.domain

import cats.{Eq, Show}
import io.circe.{Decoder, Encoder}

final case class Revision(value: Long) extends AnyVal

object Revision {
  implicit val decoder: Decoder[Revision] = Decoder.decodeLong.map(Revision.apply)
  implicit val encoder: Encoder[Revision] = Encoder.encodeLong.contramap(_.value)
  implicit val eq: Eq[Revision] = Eq.fromUniversalEquals
  implicit val show: Show[Revision] = Show.show(_.value.toString)
}
