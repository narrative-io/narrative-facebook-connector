package io.narrative.connectors.facebook.domain

import cats.{Eq, Show}
import cats.syntax.either._
import cats.syntax.option._
import doobie.Meta
import io.circe.{Decoder, DecodingFailure, Encoder, Json}
import io.circe.generic.extras.semiauto.{deriveConfiguredDecoder, deriveConfiguredEncoder}
import io.circe.syntax._

import java.time.Instant

case class Command(
    createdAt: Instant,
    eventRevision: Revision,
    eventTimestamp: Instant,
    quickSettings: Profile.QuickSettings,
    payload: Command.Payload,
    profileId: Profile.Id,
    settingsId: Settings.Id,
    status: Command.Status,
    updatedAt: Instant
)
object Command {
  import io.narrative.connectors.facebook.codecs.CirceConfig._
  import io.narrative.connectors.facebook.meta.JsonMeta._

  sealed trait Payload

  implicit val decoder: Decoder[Payload] = Decoder.instance(c =>
    for {
      typeString <- c.get[String]("type")
      payload <- typeString match {
        case "subscription_delivery" => SubscriptionDelivery.decoder.tryDecode(c)
        case s =>
          DecodingFailure(
            s"unexpected command payload type '${s}'. expected one of: subscription_delivery",
            c.history
          ).asLeft
      }
    } yield payload
  )
  implicit val encoder: Encoder[Payload] = Encoder.instance { case sd: SubscriptionDelivery =>
    SubscriptionDelivery.encoder(sd).deepMerge(Json.obj("type" -> "subscription_delivery".asJson))
  }
  implicit val eq: Eq[Payload] = Eq.fromUniversalEquals
  implicit val meta: Meta[Payload] = jsonbMeta[Payload]
  implicit val show: Show[Payload] = Show.fromToString

  final case class SubscriptionDelivery(
      subscriptionId: SubscriptionId,
      transactionBatchId: TransactionBatchId
  ) extends Payload
  object SubscriptionDelivery {
    implicit val decoder: Decoder[SubscriptionDelivery] = deriveConfiguredDecoder
    implicit val encoder: Encoder[SubscriptionDelivery] = deriveConfiguredEncoder
    implicit val eq: Eq[SubscriptionDelivery] = Eq.fromUniversalEquals
    implicit val show: Show[SubscriptionDelivery] = Show.fromToString
  }

  sealed trait FileStatus
  object FileStatus {
    final case object Delivered extends FileStatus
    // todo(mbabic) failure reason
    final case object Failed extends FileStatus
    final case object Pending extends FileStatus

    def parse(s: String): Option[FileStatus] = s match {
      case "delivered" => Delivered.some
      case "failed"    => Failed.some
      case "pending"   => Pending.some
    }

    def asString(status: FileStatus): String = status match {
      case Delivered => "delivered"
      case Failed    => "failed"
      case Pending   => "pending"
    }

    implicit val decoder: Decoder[FileStatus] = Decoder.decodeString.emap(s =>
      parse(s)
        .map(_.asRight)
        .getOrElse(s"unexpected file status '${s}'. expected one of: delivered, failed, pending".asLeft)
    )
    implicit val encoder: Encoder[FileStatus] = Encoder.encodeString.contramap(asString)
    implicit val eq: Eq[FileStatus] = Eq.fromUniversalEquals
    implicit val meta: Meta[FileStatus] = Meta[String].imap(s =>
      parse(s).getOrElse(
        throw new RuntimeException(s"failed to decode file status '${s}'. expected one of delivered, failed, pending")
      )
    )(asString)
    implicit val show: Show[FileStatus] = Show.show(asString)
  }

  sealed trait Status
  sealed trait Failure extends Status
  object Status {
    implicit val decoder: Decoder[Status] = Decoder.instance(c =>
      for {
        typeString <- c.get[String]("type")
        status <- typeString match {
          case "audience_creation_failed" => AudienceCreationFailed.decoder.tryDecode(c)
          case "invalid_token"            => InvalidToken.decoder.tryDecode(c)
          case "processing_files"         => ProcessingFiles.decoder.tryDecode(c)
          case s =>
            DecodingFailure(
              s"unexpected status type '${s}'. expected one of audience_creation_failed, invalid_token, processing_files",
              c.history
            ).asLeft
        }
      } yield status
    )
    implicit val encoder: Encoder[Status] = Encoder.instance {
      case acf: AudienceCreationFailed =>
        AudienceCreationFailed.encoder(acf).deepMerge(Json.obj("type" -> "audience_creation_failed".asJson))
      case it: InvalidToken =>
        InvalidToken.encoder(it).deepMerge(Json.obj("type" -> "invalid_token".asJson))
      case pf: ProcessingFiles =>
        ProcessingFiles.encoder(pf).deepMerge(Json.obj("type" -> "processing_files".asJson))
    }
    implicit val eq: Eq[Status] = Eq.fromUniversalEquals
    implicit val meta: Meta[Status] = jsonbMeta[Status]
    implicit val show: Show[Status] = Show.fromToString

    /** Creation of an audience to which to deliver data failed. */
    final case class AudienceCreationFailed(details: String, stackTrace: Option[String]) extends Failure
    object AudienceCreationFailed {
      implicit val decoder: Decoder[AudienceCreationFailed] = deriveConfiguredDecoder
      implicit val encoder: Encoder[AudienceCreationFailed] = deriveConfiguredEncoder
      implicit val eq: Eq[AudienceCreationFailed] = Eq.fromUniversalEquals
      implicit val show: Show[AudienceCreationFailed] = Show.fromToString
    }

    /** Delivery failed because the token associated with the profile is no longer valid. */
    final case class InvalidToken(details: String, stackTrace: Option[String]) extends Failure
    object InvalidToken {
      implicit val decoder: Decoder[InvalidToken] = deriveConfiguredDecoder
      implicit val encoder: Encoder[InvalidToken] = deriveConfiguredEncoder
      implicit val eq: Eq[InvalidToken] = Eq.fromUniversalEquals
      implicit val show: Show[InvalidToken] = Show.fromToString
    }

    /** Files delivery jobs successfully enqueued. The per-file jobs will update the status of each file. */
    final case class ProcessingFiles(files: Map[FileName, FileStatus]) extends Status
    object ProcessingFiles {
      implicit val decoder: Decoder[ProcessingFiles] = deriveConfiguredDecoder
      implicit val encoder: Encoder[ProcessingFiles] = deriveConfiguredEncoder
      implicit val eq: Eq[ProcessingFiles] = Eq.fromUniversalEquals
      implicit val show: Show[ProcessingFiles] = Show.fromToString
    }
  }
}
