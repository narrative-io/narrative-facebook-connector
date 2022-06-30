package io.narrative.connectors.facebook.services

import cats.{Eq, Show}
import io.circe.generic.extras.semiauto.{deriveConfiguredDecoder, deriveConfiguredEncoder}
import io.circe.{Decoder, Encoder}

import java.time.Instant

// todo(mbabic) properly model scopes, separate valid from invalid token
final case class TokenMeta(
    issuedAt: Option[Instant] = None,
    isValid: Boolean,
    scopes: List[String] = List.empty,
    user: Option[FacebookUserResponse] = None
)

object TokenMeta {
  import io.narrative.connectors.facebook.codecs.CirceConfig._

  implicit val decoder: Decoder[TokenMeta] = deriveConfiguredDecoder
  implicit val encoder: Encoder[TokenMeta] = deriveConfiguredEncoder
  implicit val eq: Eq[TokenMeta] = Eq.fromUniversalEquals
  implicit val show: Show[TokenMeta] = Show.fromToString
}
