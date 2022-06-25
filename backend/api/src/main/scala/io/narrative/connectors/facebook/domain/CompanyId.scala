package io.narrative.connectors.facebook.domain

import cats.Show
import cats.kernel.Eq

final case class CompanyId(value: Long) extends AnyVal
object CompanyId {
  implicit val eq: Eq[CompanyId] = Eq.fromUniversalEquals
  implicit val show: Show[CompanyId] = Show.show(_.value.toString)
}
