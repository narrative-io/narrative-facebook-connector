package io.narrative.connectors.facebook.services

import cats.{Eq, Show}
import io.circe.generic.extras.semiauto.{deriveConfiguredDecoder, deriveConfiguredEncoder}
import io.circe.{Decoder, Encoder}
import io.narrative.connectors.facebook.domain.{CompanyId, FileName, SubscriptionId, TransactionBatchId}

import java.time.Instant

final case class ApiDeliveryFile(
    companyId: CompanyId,
    file: FileName,
    size: Long,
    subscriptionId: SubscriptionId,
    timestamp: Instant,
    transactionBatchId: TransactionBatchId
)
object ApiDeliveryFile {
  import io.narrative.connectors.facebook.codecs.CirceConfig._

  implicit val decoder: Decoder[ApiDeliveryFile] = deriveConfiguredDecoder
  implicit val encoder: Encoder[ApiDeliveryFile] = deriveConfiguredEncoder
  implicit val eq: Eq[ApiDeliveryFile] = Eq.fromUniversalEquals
  implicit val show: Show[ApiDeliveryFile] = Show.fromToString
}
