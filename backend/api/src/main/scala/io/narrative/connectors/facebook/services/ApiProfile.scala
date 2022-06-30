package io.narrative.connectors.facebook.services

import cats.{Eq, Show}
import io.circe.generic.extras.semiauto.{deriveConfiguredDecoder, deriveConfiguredEncoder}
import io.circe.{Codec, Decoder, Encoder}
import io.narrative.connectors.facebook.domain.Profile

/** Profile response returned by Narrative API. */
final case class ApiProfile(id: Profile.Id, name: String, description: Option[String], status: ApiProfile.Status)
object ApiProfile {
  import io.narrative.connectors.facebook.codecs.CirceConfig._

  implicit val decoder: Decoder[ApiProfile] = deriveConfiguredDecoder
  implicit val encoder: Encoder[ApiProfile] = deriveConfiguredEncoder
  implicit val eq: Eq[ApiProfile] = Eq.fromUniversalEquals
  implicit val show: Show[ApiProfile] = Show.fromToString

  sealed trait Status
  object Status {
    case object Enabled extends Status
    case object Disabled extends Status
    case object Archived extends Status

    implicit val codec: Codec[Status] = Codec.from(
      Decoder[String].emap {
        case "enabled"  => Right(Enabled)
        case "disabled" => Right(Disabled)
        case "archived" => Right(Archived)
        case other      => Left(s"Invalid status: $other")
      },
      Encoder[String].contramap {
        case Enabled  => "enabled"
        case Disabled => "disabled"
        case Archived => "archived"
      }
    )
  }
}
