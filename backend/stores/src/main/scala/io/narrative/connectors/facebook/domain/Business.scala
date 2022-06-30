package io.narrative.connectors.facebook.domain

import cats.Show
import cats.kernel.Eq
import doobie.Meta
import io.circe.{Decoder, Encoder}

/** The Facebook business account associated with an ad account. */
case class Business(id: Business.Id, name: Business.Name)
object Business {
  implicit val eq: Eq[Business] = Eq.fromUniversalEquals
  implicit val show: Show[Business] = Show.fromToString

  final case class Id(value: String) extends AnyVal
  object Id {
    implicit val decoder: Decoder[Id] = Decoder.decodeString.map(Id.apply)
    implicit val encoder: Encoder[Id] = Encoder.encodeString.contramap(_.value)
    implicit val eq: Eq[Id] = Eq.fromUniversalEquals
    implicit val meta: Meta[Id] = Meta[String].imap(Id.apply)(_.value)
    implicit val show: Show[Id] = Show.show(_.value)
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
