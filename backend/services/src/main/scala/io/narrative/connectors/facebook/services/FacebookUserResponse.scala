package io.narrative.connectors.facebook.services

import cats.{Eq, Show}
import io.circe.{Decoder, Encoder}
import io.circe.generic.extras.semiauto.{deriveConfiguredDecoder, deriveConfiguredEncoder}
import io.narrative.connectors.facebook.domain.FacebookUser

final case class FacebookUserResponse(id: FacebookUser.Id, name: FacebookUser.Name)

object FacebookUserResponse {
  import io.narrative.connectors.facebook.codecs.CirceConfig._

  implicit val decoder: Decoder[FacebookUserResponse] = deriveConfiguredDecoder
  implicit val encoder: Encoder[FacebookUserResponse] = deriveConfiguredEncoder
  implicit val eq: Eq[FacebookUserResponse] = Eq.fromUniversalEquals
  implicit val show: Show[FacebookUserResponse] = Show.fromToString
}
