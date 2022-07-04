package io.narrative.connectors.facebook.domain

import cats.{Eq, Show}
import cats.syntax.either._
import doobie.Meta
import io.circe.{Decoder, DecodingFailure, Encoder, Json}
import io.circe.generic.extras.semiauto.{deriveConfiguredDecoder, deriveConfiguredEncoder}
import io.circe.syntax._

import java.time.Instant

final case class Job(
    id: Job.Id,
    createdAt: Instant,
    eventRevision: Revision,
    eventTimestamp: Instant,
    quickSettings: Option[Profile.QuickSettings],
    payload: Job.Payload,
    profileId: Profile.Id,
    updatedAt: Instant
)

object Job {
  import io.narrative.connectors.facebook.codecs.CirceConfig._
  import io.narrative.connectors.facebook.meta.JsonMeta._

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

  sealed trait Payload

  implicit val decoder: Decoder[Payload] = Decoder.instance(c =>
    for {
      typeString <- c.get[String]("type")
      payload <- typeString match {
        case "deliver_file"    => DeliverFile.decoder.tryDecode(c)
        case "process_command" => ProcessCommand.decoder.tryDecode(c)
        case s =>
          DecodingFailure(
            s"unexpected payload type '${s}'. expected one of deliver_file, process_command",
            c.history
          ).asLeft
      }
    } yield payload
  )
  implicit val encoder: Encoder[Payload] = Encoder.instance {
    case df: DeliverFile    => DeliverFile.encoder(df).deepMerge(Json.obj("type" -> "deliver_file".asJson))
    case pc: ProcessCommand => ProcessCommand.encoder(pc).deepMerge(Json.obj("type" -> "process_command".asJson))
  }
  implicit val eq: Eq[Payload] = Eq.fromUniversalEquals
  implicit val meta: Meta[Payload] = jsonbMeta[Payload]
  implicit val show: Show[Payload] = Show.fromToString

  /** Deliver the given file to the given audience for a subscription delivery. */
  final case class DeliverFile(
      audienceId: Audience.Id,
      file: FileName,
      subscriptionId: SubscriptionId,
      transactionBatchId: TransactionBatchId
  ) extends Payload
  object DeliverFile {
    implicit val decoder: Decoder[DeliverFile] = deriveConfiguredDecoder
    implicit val encoder: Encoder[DeliverFile] = deriveConfiguredEncoder
    implicit val eq: Eq[DeliverFile] = Eq.fromUniversalEquals
    implicit val show: Show[DeliverFile] = Show.fromToString
  }

  /** Process an incoming command for a subscription delivery, enqueuing further file-specific work and creating
    * audiences as required.
    */
  final case class ProcessCommand(
      subscriptionId: SubscriptionId,
      transactionBatchId: TransactionBatchId
  ) extends Payload
  object ProcessCommand {
    implicit val decoder: Decoder[ProcessCommand] = deriveConfiguredDecoder
    implicit val encoder: Encoder[ProcessCommand] = deriveConfiguredEncoder
    implicit val eq: Eq[ProcessCommand] = Eq.fromUniversalEquals
    implicit val show: Show[ProcessCommand] = Show.fromToString
  }
}
