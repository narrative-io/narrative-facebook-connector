package io.narrative.connectors.facebook.domain

import cats.{Eq, Show}
import doobie.Meta
import io.circe.{Codec, Decoder, Encoder}
import io.circe.generic.extras.semiauto.deriveConfiguredCodec
import io.narrative.connectors.model.SnapshotId

final case class Job(
    eventRevision: Revision,
    file: FileName,
    snapshotId: Option[SnapshotId]
)

object Job {
  import io.narrative.connectors.facebook.codecs.CirceConfig._
  import io.narrative.connectors.facebook.meta.JsonMeta._

  implicit val jobCodec: Codec[Job] = deriveConfiguredCodec
  implicit val jobEq: Eq[Job] = Eq.fromUniversalEquals
  implicit val jobMeta: Meta[Job] = jsonbMeta[Job]

  // Using numeric ID instead of UUID as the ID is entirely internal to the application and it is used to order jobs
  // by insertion order when polling for work.
  final case class Id(value: Long)
  object Id {
    implicit val decoder: Decoder[Id] = Decoder.decodeLong.map(Id.apply)
    implicit val encoder: Encoder[Id] = Encoder.encodeLong.contramap(_.value)
    implicit val eq: Eq[Id] = Eq.fromUniversalEquals
    implicit val meta: Meta[Id] = Meta[Long].imap(Id.apply)(_.value)
    implicit val show: Show[Id] = Show.show(_.value.toString)
  }

}
