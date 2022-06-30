package io.narrative.connectors.facebook.services

import cats.{Eq, Show}
import io.circe.generic.extras.semiauto.{deriveConfiguredDecoder, deriveConfiguredEncoder}
import io.circe.{Decoder, Encoder}
import io.narrative.connectors.facebook.domain.AdAccount

final case class AdAccountResponse(
    id: AdAccount.Id,
    business: Option[BusinessResponse],
    name: AdAccount.Name,
    supportsCustomAudiences: Boolean,
    userAcceptedCustomAudienceTos: Boolean
)

object AdAccountResponse {
  import io.narrative.connectors.facebook.codecs.CirceConfig._

  implicit val decoder: Decoder[AdAccountResponse] = deriveConfiguredDecoder
  implicit val encoder: Encoder[AdAccountResponse] = deriveConfiguredEncoder
  implicit val eq: Eq[AdAccountResponse] = Eq.fromUniversalEquals
  implicit val show: Show[AdAccountResponse] = Show.fromToString
}
