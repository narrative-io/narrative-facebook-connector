package io.narrative.connectors.facebook.domain

import cats.{Eq, Show}
import io.circe.{Decoder, Encoder}

final case class CompanyId(value: Long) extends AnyVal
object CompanyId {
  implicit val decoder: Decoder[CompanyId] = Decoder.decodeLong.map(CompanyId.apply)
  implicit val encoder: Encoder[CompanyId] = Encoder.encodeLong.contramap(_.value)
  implicit val eq: Eq[CompanyId] = Eq.fromUniversalEquals
  implicit val show: Show[CompanyId] = Show.show(_.value.toString)
}
