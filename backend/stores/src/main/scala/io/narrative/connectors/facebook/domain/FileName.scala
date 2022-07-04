package io.narrative.connectors.facebook.domain

import cats.{Eq, Show}
import io.circe.{Decoder, Encoder, KeyDecoder, KeyEncoder}

final case class FileName(value: String) extends AnyVal
object FileName {
  implicit val decoder: Decoder[FileName] = Decoder.decodeString.map(FileName.apply)
  implicit val encoder: Encoder[FileName] = Encoder.encodeString.contramap(_.value)
  // Include key de/encoders when file name is used as the key in a JSON map. See e.g. Command.Status.ProcessingFiles
  implicit val keyDecoder: KeyDecoder[FileName] = KeyDecoder.decodeKeyString.map(FileName.apply)
  implicit val keyEncoder: KeyEncoder[FileName] = KeyEncoder.encodeKeyString.contramap(_.value)
  implicit val eq: Eq[FileName] = Eq.fromUniversalEquals
  implicit val show: Show[FileName] = Show.show(_.value)
}
