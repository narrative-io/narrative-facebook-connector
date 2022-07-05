package io.narrative.connectors.facebook.services

import cats.{Eq, Show}
import io.circe.{Decoder, Encoder}

case class FacebookToken(value: String) extends AnyVal
object FacebookToken {
  implicit val decoder: Decoder[FacebookToken] = Decoder.decodeString.map(FacebookToken.apply)
  implicit val encoder: Encoder[FacebookToken] = Encoder.encodeString.contramap(_.value)
  implicit val eq: Eq[FacebookToken] = Eq.fromUniversalEquals
  implicit val show: Show[FacebookToken] = Show.show(_ => "<redacted>")
}
