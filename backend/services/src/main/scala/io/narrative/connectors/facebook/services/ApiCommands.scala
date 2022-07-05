package io.narrative.connectors.facebook.services

import cats.{Eq, Show}
import cats.instances.list._
import cats.syntax.eq._
import cats.syntax.show._
import io.circe.{Decoder, Encoder}
import io.circe.generic.extras.semiauto.{deriveConfiguredDecoder, deriveConfiguredEncoder}
import io.narrative.connectors.facebook.domain.Revision

/** The result of polling the /commands endpoint of the Narrative API. */
final case class ApiCommands(records: List[ApiDeliveryCommand], nextRevision: Revision)
object ApiCommands {
  import io.narrative.connectors.facebook.codecs.CirceConfig._

  implicit def decoder: Decoder[ApiCommands] = deriveConfiguredDecoder
  implicit def encoder: Encoder[ApiCommands] = deriveConfiguredEncoder
  implicit def eq: Eq[ApiCommands] =
    Eq.instance((a, b) => a.nextRevision === b.nextRevision && a.records === b.records)
  implicit def show: Show[ApiCommands] =
    Show.show(commands => s"ApiCommands(nextRevision=${commands.nextRevision.show}, records=${commands.records.show}")
}
