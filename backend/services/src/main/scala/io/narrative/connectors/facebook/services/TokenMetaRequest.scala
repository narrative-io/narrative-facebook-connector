package io.narrative.connectors.facebook.services

import cats.{Eq, Show}
import io.circe.generic.extras.semiauto.{deriveConfiguredDecoder, deriveConfiguredEncoder}
import io.circe.{Decoder, Encoder}

final case class TokenMetaRequest(token: FacebookToken)
object TokenMetaRequest {
  import io.narrative.connectors.facebook.codecs.CirceConfig._

  implicit val decoder: Decoder[TokenMetaRequest] = deriveConfiguredDecoder
  implicit val encoder: Encoder[TokenMetaRequest] = deriveConfiguredEncoder
  implicit val eq: Eq[TokenMetaRequest] = Eq.fromUniversalEquals
  implicit val show: Show[TokenMetaRequest] = Show.fromToString
}
