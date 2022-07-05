package io.narrative.connectors.facebook.services

import cats.{Eq, Show}
import cats.syntax.either._
import io.circe.generic.extras.Configuration
import io.circe.{Decoder, DecodingFailure, Encoder, Json}
import io.circe.generic.extras.semiauto.{deriveConfiguredDecoder, deriveConfiguredEncoder}
import io.circe.syntax._
import io.narrative.connectors.facebook.domain.{Profile, Revision, SubscriptionId, TransactionBatchId}

import java.time.Instant

final case class ApiDeliveryCommand(metadata: ApiDeliveryCommand.Metadata, payload: ApiDeliveryCommand.Payload)
object ApiDeliveryCommand {
  // NB: the API is returning camel case field names so we very purposefully don't use the shared CirceConfig
  implicit val config: Configuration = Configuration.default

  implicit val decoder: Decoder[ApiDeliveryCommand] = Decoder.instance(c =>
    for {
      metadata <- c.get[Metadata]("metadata")
      typeString <- c.get[String]("payload_type")
      payload <- typeString match {
        case "PerformDelivery" => DeliverFiles.decoder.tryDecode(c)
        case _                 => DecodingFailure(s"Unsupported payload type ${typeString}", c.history).asLeft
      }
    } yield ApiDeliveryCommand(metadata = metadata, payload = payload)
  )
  implicit val encoder: Encoder[ApiDeliveryCommand] = Encoder.instance {
    case ApiDeliveryCommand(metadata, deliverFiles: ApiDeliveryCommand.DeliverFiles) =>
      Json.obj(
        "payload_type" -> "PerformDelivery".asJson,
        "metadata" -> metadata.asJson,
        "payload" -> deliverFiles.asJson
      )
  }
  implicit val eq: Eq[ApiDeliveryCommand] = Eq.fromUniversalEquals
  implicit val show: Show[ApiDeliveryCommand] = Show.fromToString

  sealed trait Payload

  final case class DeliverFiles(
      quickSettings: Option[Profile.QuickSettings],
      profileId: Profile.Id,
      subscriptionId: SubscriptionId,
      transactionBatchId: TransactionBatchId
  ) extends Payload
  object DeliverFiles {
    implicit val decoder: Decoder[DeliverFiles] = deriveConfiguredDecoder
    implicit val encoder: Encoder[DeliverFiles] = deriveConfiguredEncoder
    implicit val eq: Eq[DeliverFiles] = Eq.fromUniversalEquals
    implicit val show: Show[DeliverFiles] = Show.fromToString
  }

  final case class Metadata(revision: Revision, timestamp: Instant)
  object Metadata {
    implicit val decoder: Decoder[Metadata] = deriveConfiguredDecoder
    implicit val encoder: Encoder[Metadata] = deriveConfiguredEncoder
    implicit val eq: Eq[Metadata] = Eq.fromUniversalEquals
    implicit val show: Show[Metadata] = Show.fromToString
  }
}
