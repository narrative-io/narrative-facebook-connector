package io.narrative.connectors.facebook.services

import cats.{Eq, Show}
import io.circe.generic.extras.semiauto.{deriveConfiguredDecoder, deriveConfiguredEncoder}
import io.circe.{Decoder, Encoder}
import io.narrative.connectors.facebook.domain.{CompanyId, Profile}

import java.time.Instant

case class ProfileResponse(
    id: Profile.Id,
    adAccount: AdAccountResponse,
    audience: Option[AudienceResponse],
    createdAt: Instant,
    companyId: CompanyId,
    token: TokenMeta,
    updatedAt: Instant
)
object ProfileResponse {
  import io.narrative.connectors.facebook.codecs.CirceConfig._

  implicit val decoder: Decoder[ProfileResponse] = deriveConfiguredDecoder
  implicit val encoder: Encoder[ProfileResponse] = deriveConfiguredEncoder
  implicit val eq: Eq[ProfileResponse] = Eq.fromUniversalEquals
  implicit val show: Show[ProfileResponse] = Show.fromToString
}
