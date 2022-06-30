package io.narrative.connectors.facebook.services

import cats.{Eq, Show}
import io.circe.{Decoder, Encoder}
import io.circe.generic.extras.semiauto.{deriveConfiguredDecoder, deriveConfiguredEncoder}
import io.narrative.connectors.facebook.domain.AdAccount

final case class CreateProfileRequest(
    adAccountId: AdAccount.Id,
    token: FacebookToken
)
object CreateProfileRequest {
  import io.narrative.connectors.facebook.codecs.CirceConfig._

  implicit val decoder: Decoder[CreateProfileRequest] = deriveConfiguredDecoder
  implicit val encoder: Encoder[CreateProfileRequest] = deriveConfiguredEncoder
  implicit val eq: Eq[CreateProfileRequest] = Eq.fromUniversalEquals
  implicit val show: Show[CreateProfileRequest] = Show.fromToString
}
