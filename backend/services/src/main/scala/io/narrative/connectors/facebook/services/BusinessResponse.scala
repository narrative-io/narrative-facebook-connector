package io.narrative.connectors.facebook.services

import cats.Show
import cats.kernel.Eq
import io.circe.{Decoder, Encoder}
import io.circe.generic.extras.semiauto.{deriveConfiguredDecoder, deriveConfiguredEncoder}
import io.narrative.connectors.facebook.domain.Business

final case class BusinessResponse(id: Business.Id, name: Business.Name)
object BusinessResponse {
  import io.narrative.connectors.facebook.codecs.CirceConfig._

  implicit val decoder: Decoder[BusinessResponse] = deriveConfiguredDecoder
  implicit val encoder: Encoder[BusinessResponse] = deriveConfiguredEncoder
  implicit val eq: Eq[BusinessResponse] = Eq.fromUniversalEquals
  implicit val show: Show[BusinessResponse] = Show.fromToString
}
