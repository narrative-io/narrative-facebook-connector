package io.narrative.connectors.facebook.services

import cats.{Eq, Show}
import cats.syntax.either._
import cats.syntax.option._
import io.circe.generic.extras.semiauto.{deriveConfiguredDecoder, deriveConfiguredEncoder}
import io.circe.{Decoder, Encoder}
import io.narrative.connectors.facebook.domain.Profile

/** Profile response returned by Narrative API. */
final case class ApiProfile(
    id: Profile.Id,
    name: ApiProfile.Name,
    description: Option[ApiProfile.Description],
    status: ApiProfile.Status
)
object ApiProfile {
  import io.narrative.connectors.facebook.codecs.CirceConfig._

  implicit val decoder: Decoder[ApiProfile] = deriveConfiguredDecoder
  implicit val encoder: Encoder[ApiProfile] = deriveConfiguredEncoder
  implicit val eq: Eq[ApiProfile] = Eq.fromUniversalEquals
  implicit val show: Show[ApiProfile] = Show.fromToString

  final case class Description(value: String) extends AnyVal
  object Description {
    implicit val decoder: Decoder[Description] = Decoder.decodeString.map(Description.apply)
    implicit val encoder: Encoder[Description] = Encoder.encodeString.contramap(_.value)
    implicit val eq: Eq[Description] = Eq.fromUniversalEquals
    implicit val show: Show[Description] = Show.show(_.value)
  }

  final case class Name(value: String) extends AnyVal
  object Name {
    implicit val decoder: Decoder[Name] = Decoder.decodeString.map(Name.apply)
    implicit val encoder: Encoder[Name] = Encoder.encodeString.contramap(_.value)
    implicit val eq: Eq[Name] = Eq.fromUniversalEquals
    implicit val show: Show[Name] = Show.show(_.value)
  }

  sealed trait Status
  object Status {
    case object Archived extends Status
    case object Disabled extends Status
    case object Enabled extends Status

    def parse(s: String): Option[Status] = s match {
      case "archived" => Archived.some
      case "disabled" => Disabled.some
      case "enabled"  => Enabled.some
      case _          => none
    }

    def asString(status: Status): String = status match {
      case Archived => "archived"
      case Disabled => "disabled"
      case Enabled  => "enabled"
    }

    implicit val decoder: Decoder[Status] =
      Decoder[String].emap(s => parse(s).map(_.asRight).getOrElse(s"invalid status: ${s}".asLeft))
    implicit val encoder: Encoder[Status] = Encoder[String].contramap(asString)
    implicit val eq: Eq[Status] = Eq.fromUniversalEquals
    implicit val show: Show[Status] = Show.show(asString)
  }
}
