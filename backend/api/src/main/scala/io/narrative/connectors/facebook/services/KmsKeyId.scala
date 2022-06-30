package io.narrative.connectors.facebook.services

import cats.{Eq, Show}

final case class KmsKeyId(value: String) extends AnyVal
object KmsKeyId {
  implicit val eq: Eq[KmsKeyId] = Eq.fromUniversalEquals
  implicit val show: Show[KmsKeyId] = Show.show(_.value)
}
