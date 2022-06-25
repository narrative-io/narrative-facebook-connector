package io.narrative.connectors.facebook.domain

import cats.Show
import cats.kernel.Eq

case class FacebookApiToken(value: String) extends AnyVal
object FacebookApiToken {
  implicit val eq: Eq[FacebookApiToken] = Eq.fromUniversalEquals
  implicit val show: Show[FacebookApiToken] = Show.show(_ => "FacebookApiToken(<redacted>)")
}
