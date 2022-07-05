package io.narrative.connectors.facebook.services

import cats.{Eq, Show}
import io.circe.{Decoder, Encoder}
import io.circe.generic.extras.semiauto.{deriveConfiguredDecoder, deriveConfiguredEncoder}
import io.narrative.connectors.facebook.domain.CompanyId

final case class ApiCompany(id: CompanyId)
object ApiCompany {
  import io.narrative.connectors.facebook.codecs.CirceConfig._

  implicit val decoder: Decoder[ApiCompany] = deriveConfiguredDecoder
  implicit val encoder: Encoder[ApiCompany] = deriveConfiguredEncoder
  implicit val eq: Eq[ApiCompany] = Eq.fromUniversalEquals
  implicit val show: Show[ApiCompany] = Show.fromToString
}
