package io.narrative.connectors.facebook.services

import cats.{Eq, Show}
import io.circe.{Decoder, Encoder}
import io.circe.generic.extras.semiauto.{deriveConfiguredDecoder, deriveConfiguredEncoder}

final case class TokenMetaResponse(adAccounts: List[AdAccountResponse], token: TokenMeta)
object TokenMetaResponse {
  import io.narrative.connectors.facebook.codecs.CirceConfig._

  implicit val decoder: Decoder[TokenMetaResponse] = deriveConfiguredDecoder
  implicit val encoder: Encoder[TokenMetaResponse] = deriveConfiguredEncoder
  implicit val eq: Eq[TokenMetaResponse] = Eq.fromUniversalEquals
  implicit val show: Show[TokenMetaResponse] = Show.fromToString
}
