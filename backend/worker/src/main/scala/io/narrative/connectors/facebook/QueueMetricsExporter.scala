package io.narrative.connectors.facebook

import cats.Applicative
import cats.effect._
import cats.implicits._
import com.amazonaws.services.cloudwatch.AmazonCloudWatch
import com.amazonaws.services.cloudwatch.model.{Dimension, MetricDatum, PutMetricDataRequest, StandardUnit}
import doobie._
import doobie.implicits._
import io.narrative.connectors.facebook.stores.QueueMetricsStore
import io.narrative.microframework.config.Stage
import org.typelevel.log4cats.LoggerFactory

import scala.jdk.CollectionConverters._
import java.time.Instant
import java.time.format.DateTimeFormatter

class QueueMetricsExporter(
    cloudwatchClient: AmazonCloudWatch,
    queueStatisticsStore: QueueMetricsStore.QueryOps,
    xa: Transactor[IO],
    stage: Stage
)(implicit logging: LoggerFactory[IO])
    extends QueueMetricsExporter.Ops {
  override def computeAndExport(timestamp: Instant): IO[Unit] = {
    Applicative[IO]
      .map3(
        queueStatisticsStore.queueSize().transact(xa),
        queueStatisticsStore.legacyDeliveryQueueSizePerSubscription().transact(xa),
        queueStatisticsStore.deliveryQueueSizePerConnection().transact(xa)
      ) { case (queueSize, legacyDeliveryQueueSizePerSubscription, deliveryQueueSizePerConnection) =>
        QueueMetricsExporter.toMetrics(stage)(
          timestamp,
          queueSize,
          legacyDeliveryQueueSizePerSubscription,
          deliveryQueueSizePerConnection
        )
      }
      .flatMap(`export`)
  }

  /** Exposed for testing
    * @return
    */
  override def export(
      metricData: List[MetricDatum]
  ): IO[Unit] = {
    // https://docs.aws.amazon.com/AmazonCloudWatch/latest/APIReference/API_PutMetricData.html
    metricData
      .grouped(1000)
      .toList
      .traverse { metrics =>
        val request = new PutMetricDataRequest()
          .withNamespace(QueueMetricsExporter.namespace)
          .withMetricData(metrics: _*)

        def summarizeDimension(dimension: Dimension) = s"${dimension.getName}:${dimension.getValue}"
        def summarizeMetric(metricDatum: MetricDatum) =
          s"${metricDatum.getMetricName}: ${metricDatum.getValue} (ts=${DateTimeFormatter.ISO_INSTANT.format(
              metricDatum.getTimestamp.toInstant
            )}, dimensions=${metricDatum.getDimensions.asScala.map(summarizeDimension).mkString(",")})"

        logging.getLogger.info(s"Sending metrics: ${metrics.map(summarizeMetric).mkString(",")}") >>
          IO.blocking(cloudwatchClient.putMetricData(request))
      }
      .void
  }
}
object QueueMetricsExporter {
  trait Ops {
    def computeAndExport(timestamp: Instant): IO[Unit]

    def export(
        metricData: List[MetricDatum]
    ): IO[Unit]
  }

  // We used to publish the event-consumer-heartbeat metric to this namespace as well,
  // so let's stay consistent
  def namespace = s"facebook-connector"

  object Metrics {
    def queueSize(timestamp: Instant, value: Long, stage: Stage) = new MetricDatum()
      .withMetricName("QueueSize")
      .withValue(value.toDouble)
      .withUnit(StandardUnit.Count)
      .withDimensions(new Dimension().withName("Stage").withValue(stage.toString))
      .withTimestamp(java.util.Date.from(timestamp))

    def legacyDeliveryQueueSize(timestamp: Instant, value: Long, subscriptionId: String, stage: Stage) =
      new MetricDatum()
        .withMetricName("LegacyDeliveryQueueSize")
        .withValue(value.toDouble)
        .withUnit(StandardUnit.Count)
        .withDimensions(
          new Dimension().withName("SubscriptionId").withValue(subscriptionId),
          new Dimension().withName("Stage").withValue(stage.toString)
        )
        .withTimestamp(java.util.Date.from(timestamp))

    def deliveryQueueSize(timestamp: Instant, value: Long, connectionId: String, stage: Stage) = new MetricDatum()
      .withMetricName("DeliveryQueueSize")
      .withValue(value.toDouble)
      .withUnit(StandardUnit.Count)
      .withDimensions(
        new Dimension().withName("ConnectionId").withValue(connectionId),
        new Dimension().withName("Stage").withValue(stage.toString)
      )
      .withTimestamp(java.util.Date.from(timestamp))
  }

  def toMetrics(stage: Stage)(
      timestamp: Instant,
      queueSize: Long,
      legacyDeliveryQueueSizePerSubscription: Map[String, Long],
      deliveryQueueSizePerConnection: Map[String, Long]
  ) = {
    List(
      QueueMetricsExporter.Metrics.queueSize(timestamp, queueSize, stage)
    ) ++ legacyDeliveryQueueSizePerSubscription.map { case (subscriptionId, size) =>
      QueueMetricsExporter.Metrics.legacyDeliveryQueueSize(timestamp, size, subscriptionId, stage)
    } ++ deliveryQueueSizePerConnection.map { case (connectionId, size) =>
      QueueMetricsExporter.Metrics.deliveryQueueSize(timestamp, size, connectionId, stage)
    }
  }
}
