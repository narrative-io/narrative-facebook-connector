package io.narrative.connectors.facebook.domain

import cats.{Eq, Show}
import doobie.Meta
import io.circe.{Decoder, Encoder}

final case class Audience(id: Audience.Id, name: Audience.Name)

object Audience {
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
