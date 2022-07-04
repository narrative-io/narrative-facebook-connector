package io.narrative.connectors.facebook.domain

import cats.{Eq, Show}
import io.circe.{Decoder, Encoder}

final case class FileName(value: String) extends AnyVal
object FileName {
  implicit val decoder: Decoder[FileName] = Decoder.decodeString.map(FileName.apply)
  implicit val encoder: Encoder[FileName] = Encoder.encodeString.contramap(_.value)
  implicit val eq: Eq[FileName] = Eq.fromUniversalEquals
  implicit val show: Show[FileName] = Show.show(_.value)
}
