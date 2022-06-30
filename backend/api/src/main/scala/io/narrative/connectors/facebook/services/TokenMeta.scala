package io.narrative.connectors.facebook.services

import cats.{Eq, Show}
import cats.syntax.either._
import io.circe.generic.extras.semiauto.{deriveConfiguredDecoder, deriveConfiguredEncoder}
import io.circe.syntax._
import io.circe.{Decoder, Encoder, Json}

import java.time.Instant

sealed trait TokenMeta {
  def isValid: Boolean
}
object TokenMeta {
  import io.narrative.connectors.facebook.codecs.CirceConfig._

  final case class Valid(issuedAt: Instant, scopes: List[Scope], user: FacebookUserResponse) extends TokenMeta {
    override val isValid: Boolean = true
  }
  object Valid {
    implicit val decoder: Decoder[TokenMeta] = deriveConfiguredDecoder
    implicit val encoder: Encoder[TokenMeta] = deriveConfiguredEncoder
  }
  final case object Invalid extends TokenMeta {
    override val isValid: Boolean = false
  }

  implicit val decoder: Decoder[TokenMeta] = Decoder.instance(c =>
    for {
      isValid <- c.get[Boolean]("is_valid")
      meta <-
        if (isValid)
          deriveConfiguredDecoder[Valid].tryDecode(c)
        else
          Invalid.asRight
    } yield meta
  )
  implicit val encoder: Encoder[TokenMeta] = Encoder.instance {
    case valid: Valid =>
      deriveConfiguredEncoder[Valid].encodeObject(valid).asJson.deepMerge(Json.obj("is_valid" -> true.asJson))
    case Invalid => Json.obj("is_valid" -> false.asJson)
  }
  implicit val eq: Eq[TokenMeta] = Eq.fromUniversalEquals
  implicit val show: Show[TokenMeta] = Show.fromToString

  /** A user-granted API access associated with a token.
    *
    * Called a "scope" here because that's the term used in Facebook API calls, though in the docs they are documented
    * under "permissions".
    *
    * See https://developers.facebook.com/docs/permissions/reference
    */
  sealed trait Scope
  object Scope {
    final case object AdsManagement extends Scope
    final case object BusinessManagement extends Scope
    final case object PublicProfile extends Scope
    final case class Unknown(value: String) extends Scope

    def parse(s: String): Scope = s match {
      case "ads_management"      => AdsManagement
      case "business_management" => BusinessManagement
      case "public_profile"      => PublicProfile
      case _                     => Unknown(s)
    }

    def asString(scope: Scope): String = scope match {
      case AdsManagement      => "ads_management"
      case BusinessManagement => "business_management"
      case PublicProfile      => "public_profile"
      case Unknown(value)     => value
    }

    implicit val decoder: Decoder[Scope] = Decoder.decodeString.map(parse)
    implicit val encoder: Encoder[Scope] = Encoder.encodeString.contramap(asString)
    implicit val eq: Eq[Scope] = Eq.fromUniversalEquals
    implicit val show: Show[Scope] = Show.show(asString)
  }
}
