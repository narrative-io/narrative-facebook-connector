package io.narrative.connectors.facebook.domain

import cats.{Eq, Show}
import doobie.Meta
import io.circe.{Decoder, Encoder}

final case class AdAccount(id: AdAccount.Id, name: AdAccount.Name)

object AdAccount {

  /** Ad account IDs are sometimes prefixed with the string "act_" in Facebook's APIs, sometimes they are not. * This
    * representation choose to canonicalize the prefixed version.
    *
    * The Facebook SDK will try to hide the fact that an account's node id is of the form "act_123456789" by removing
    * the prefix when e.g. `com.facebook.ads.sdk.AdAccount#getId` is called, but this breaks calls to methods like
    * `com.facebook.ads.sdk.AdAccount#fetchByIds` which will not return results unless the input ids are prefixed.
    */
  final case class Id(value: String) {
    lazy val withoutPrefix: String = value.stripPrefix(Id.Prefix)
  }

  object Id {
    private val Prefix: String = "act_"

    implicit val decoder: Decoder[Id] = Decoder.decodeString.map(Id.fromPrefixed_!)
    implicit val encoder: Encoder[Id] = Encoder.encodeString.contramap(_.value)
    implicit val eq: Eq[Id] = Eq.fromUniversalEquals
    implicit val meta: Meta[Id] = Meta[String].imap(Id.apply)(_.value)
    implicit val show: Show[Id] = Show.show(_.value)

    /** Unsafe. Assumes provided string is a valid ad account id prefixed with "act_". */
    def fromPrefixed_!(id: String): Id = Id(id)

    /** Unsafe. Assumes provided string is a valid ad account id not prefixed with "act_". */
    def fromUnprefixed_!(id: String): Id = Id(s"${Prefix}${id}")
  }

  final case class Name(value: String) extends AnyVal
  object Name {
    implicit val decoder: Decoder[Name] = Decoder.decodeString.map(Name.apply)
    implicit val encoder: Encoder[Name] = Encoder.encodeString.contramap(_.value)
    implicit val eq: Eq[Name] = Eq.fromUniversalEquals
    implicit val meta: Meta[Name] = Meta[String].imap(Name.apply)(_.value)
    implicit val show: Show[Name] = Show.show(_.value)
  }
}
