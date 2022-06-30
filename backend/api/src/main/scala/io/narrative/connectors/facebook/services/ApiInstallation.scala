package io.narrative.connectors.facebook.services

import cats.Show
import cats.kernel.Eq
import io.circe.{Decoder, Encoder}
import io.circe.generic.extras.semiauto.{deriveConfiguredDecoder, deriveConfiguredEncoder}

final case class ApiInstallation(id: ApiInstallation.Id)

object ApiInstallation {
  import io.narrative.connectors.facebook.codecs.CirceConfig._

  implicit val decoder: Decoder[ApiInstallation] = deriveConfiguredDecoder
  implicit val encoder: Encoder[ApiInstallation] = deriveConfiguredEncoder
  implicit val eq: Eq[ApiInstallation] = Eq.fromUniversalEquals
  implicit val show: Show[ApiInstallation] = Show.fromToString

  final case class Id(value: Long) extends AnyVal
  object Id {
    implicit val decoder: Decoder[Id] = Decoder.decodeLong.map(Id.apply)
    implicit val encoder: Encoder[Id] = Encoder.encodeLong.contramap(_.value)
    implicit val eq: Eq[Id] = Eq.fromUniversalEquals
    implicit val show: Show[Id] = Show.show(_.value.toString)
  }
}
