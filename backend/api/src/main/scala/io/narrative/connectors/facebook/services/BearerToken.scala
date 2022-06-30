package io.narrative.connectors.facebook.services

import cats.{Eq, Show}

final case class BearerToken(value: String) extends AnyVal
object BearerToken {
  implicit val eq: Eq[BearerToken] = Eq.fromUniversalEquals
  implicit val show: Show[BearerToken] = Show.show(_ => "<redacted>")
}
