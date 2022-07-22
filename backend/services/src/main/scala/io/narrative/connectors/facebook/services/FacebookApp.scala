package io.narrative.connectors.facebook.services

import cats.{Eq, Show}

case class FacebookApp(id: FacebookApp.Id, secret: FacebookApp.Secret)
object FacebookApp {
  final case class Id(value: String) extends AnyVal
  object Id {
    implicit val eq: Eq[Id] = Eq.fromUniversalEquals
    implicit val show: Show[Id] = Show.show(_.value)
  }

  final case class Secret(value: String) extends AnyVal
  object Secret {
    implicit val eq: Eq[Secret] = Eq.fromUniversalEquals
    implicit val show: Show[Secret] = Show.show(_.value)
  }
}
