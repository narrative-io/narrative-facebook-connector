package io.narrative.connectors.facebook.services

import cats.{Eq, Show}
import io.circe.{Decoder, Encoder}
import io.circe.generic.extras.semiauto.{deriveConfiguredDecoder, deriveConfiguredEncoder}
import io.narrative.connectors.facebook.domain.Audience

import java.time.Instant

case class AudienceResponse(
    id: Audience.Id,
    adAccount: AdAccountResponse,
    createdAt: Instant,
    description: Option[String],
    name: Audience.Name
)

object AudienceResponse {
  import io.narrative.connectors.facebook.codecs.CirceConfig._

  implicit val decoder: Decoder[AudienceResponse] = deriveConfiguredDecoder
  implicit val encoder: Encoder[AudienceResponse] = deriveConfiguredEncoder
  implicit val eq: Eq[AudienceResponse] = Eq.fromUniversalEquals
  implicit val show: Show[AudienceResponse] = Show.fromToString
}
