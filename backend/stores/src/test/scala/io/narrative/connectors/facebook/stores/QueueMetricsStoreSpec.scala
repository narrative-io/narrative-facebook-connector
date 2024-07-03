package io.narrative.connectors.facebook.stores

import cats.implicits._
import doobie.implicits._
import io.narrative.connectors.api
import io.narrative.connectors.api.events.EventsApi.DeliveryEvent
import io.narrative.connectors.facebook.domain.Command.FileStatus
import io.narrative.connectors.facebook.domain.{Command, FileName, Job, Revision, Settings}
import io.narrative.connectors.queue.QueueStore
import org.scalatest.OptionValues
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import java.time.Instant
import java.util.UUID
import scala.util.Random

class QueueMetricsStoreSpec extends AnyFunSuite with Matchers with OptionValues with ForAllPostgresApiContainer {
  import QueueMetricsStoreSpec._

  test("queue size for empty queue")(withResource { r =>
    val queueStatisticsStore = new QueueMetricsStore()
    for {
      _ <- sql"""delete from commands""".update.run.transact(r.xa)
      _ <- sql"""delete from queue""".update.run.transact(r.xa)
      queueSize <- queueStatisticsStore.queueSize().transact(r.xa)

    } yield {
      queueSize shouldEqual 0
    }
  })

  test("queue size for non-empty queue")(withResource { r =>
    val queueStore = new QueueStore[Job]()
    val queueStatisticsStore = new QueueMetricsStore()
    val files0 = filesForRevision(0)
    val files1 = filesForRevision(1)
    for {
      _ <- sql"""delete from commands""".update.run.transact(r.xa)
      _ <- sql"""delete from queue""".update.run.transact(r.xa)
      _ <- sql"alter table commands disable trigger all".update.run.transact(r.xa)
      _ <- new CommandStore().upsert(subscriptionDeliveryCommandRow(0)).transact(r.xa)
      _ <- files0.files.keys.toList.traverse { file =>
        queueStore.enqueue(Job(Revision(0), file, None)).transact(r.xa)
      }
      _ <- new CommandStore().upsert(subscriptionDeliveryCommandRow(1)).transact(r.xa)
      _ <- files1.files.keys.toList.traverse { file =>
        queueStore.enqueue(Job(Revision(1), file, None)).transact(r.xa)
      }
      queueSize <- queueStatisticsStore.queueSize().transact(r.xa)

    } yield {
      queueSize shouldEqual files0.files.size + files1.files.size
    }
  })

  test("legacy delivery queue size per subscription for empty queue")(withResource { r =>
    val queueStatisticsStore = new QueueMetricsStore()
    for {
      _ <- sql"""delete from commands""".update.run.transact(r.xa)
      _ <- sql"""delete from queue""".update.run.transact(r.xa)
      queueSize <- queueStatisticsStore.legacyDeliveryQueueSizePerSubscription().transact(r.xa)

    } yield {
      queueSize shouldEqual Map.empty[UUID, Long]
    }
  })

  test("legacy delivery queue size subscription for non-empty queue")(withResource { r =>
    val queueStore = new QueueStore[Job]()
    val queueStatisticsStore = new QueueMetricsStore()
    val subscriptionId0 = UUID.randomUUID()
    val files0 = filesForRevision(0)
    val subscriptionId1 = UUID.randomUUID()
    val files1 = filesForRevision(1)
    for {
      _ <- sql"""delete from commands""".update.run.transact(r.xa)
      _ <- sql"""delete from queue""".update.run.transact(r.xa)
      _ <- sql"alter table commands disable trigger all".update.run.transact(r.xa)
      _ <- new CommandStore().upsert(subscriptionDeliveryCommandRow(0, subscriptionId0)).transact(r.xa)
      _ <- files0.files.keys.toList.traverse { file =>
        queueStore.enqueue(Job(Revision(0), file, None)).transact(r.xa)
      }
      _ <- new CommandStore().upsert(subscriptionDeliveryCommandRow(1, subscriptionId1)).transact(r.xa)
      _ <- files1.files.keys.toList.traverse { file =>
        queueStore.enqueue(Job(Revision(1), file, None)).transact(r.xa)
      }
      queueSizePerSubscriptionId <- queueStatisticsStore.legacyDeliveryQueueSizePerSubscription().transact(r.xa)

    } yield {
      queueSizePerSubscriptionId shouldEqual Map(
        subscriptionId0.toString.toLowerCase -> files0.files.size,
        subscriptionId1.toString.toLowerCase -> files1.files.size
      )
    }
  })

  test("delivery queue size per connection for empty queue")(withResource { r =>
    val queueStatisticsStore = new QueueMetricsStore()
    for {
      _ <- sql"""delete from commands""".update.run.transact(r.xa)
      _ <- sql"""delete from queue""".update.run.transact(r.xa)
      queueSize <- queueStatisticsStore.deliveryQueueSizePerConnection().transact(r.xa)

    } yield {
      queueSize shouldEqual Map.empty[UUID, Long]
    }
  })

  test("delivery queue size per connection for non-empty queue")(withResource { r =>
    val queueStore = new QueueStore[Job]()
    val queueStatisticsStore = new QueueMetricsStore()
    val connectionId0 = UUID.randomUUID()
    val files0 = filesForRevision(0)
    val connectionId1 = UUID.randomUUID()
    val files1 = filesForRevision(1)
    for {
      _ <- sql"""delete from commands""".update.run.transact(r.xa)
      _ <- sql"""delete from queue""".update.run.transact(r.xa)
      _ <- new CommandStore().upsert(datasetDeliveryCommandRow(0, connectionId0)).transact(r.xa)
      _ <- files0.files.keys.toList.traverse { file =>
        queueStore.enqueue(Job(Revision(0), file, None)).transact(r.xa)
      }
      _ <- new CommandStore().upsert(datasetDeliveryCommandRow(1, connectionId1)).transact(r.xa)
      _ <- files1.files.keys.toList.traverse { file =>
        queueStore.enqueue(Job(Revision(1), file, None)).transact(r.xa)
      }
      queueSizePerSubscriptionId <- queueStatisticsStore.deliveryQueueSizePerConnection().transact(r.xa)

    } yield {
      queueSizePerSubscriptionId shouldEqual Map(
        connectionId0.toString.toLowerCase -> files0.files.size,
        connectionId1.toString.toLowerCase -> files1.files.size
      )
    }
  })
}

object QueueMetricsStoreSpec {
  def filesForRevision(revision: Long) = Command.Status.ProcessingFiles(
    Map(FileName(s"foo-${revision}") -> FileStatus.Pending, FileName(s"bar-${revision}") -> FileStatus.Pending)
  )

  def subscriptionDeliveryCommandRow(
      revision: Long,
      subscriptionId: UUID = UUID.randomUUID()
  ): CommandStore.NewCommand =
    CommandStore.NewCommand(
      eventRevision = Revision(revision),
      payload = DeliveryEvent(
        DeliveryEvent.Metadata(api.events.Revision(revision), Instant.now()),
        DeliveryEvent.SubscriptionDelivery(
          None,
          UUID.randomUUID(),
          subscriptionId,
          UUID.randomUUID()
        )
      ),
      settingsId =
        Settings.Id("profile_c7a44da1-8e8e-4149-810b-8a161e1405a3:subscription_73680d3d-395e-40de-8c34-51b999bd2dee"),
      status = filesForRevision(revision)
    )

  def datasetDeliveryCommandRow(revision: Long, connectionId: UUID = UUID.randomUUID()): CommandStore.NewCommand =
    CommandStore.NewCommand(
      eventRevision = Revision(revision),
      payload = DeliveryEvent(
        DeliveryEvent.Metadata(api.events.Revision(revision), Instant.now()),
        DeliveryEvent.SnapshotAppended(
          Random.nextLong(),
          0L,
          connectionId
        )
      ),
      settingsId =
        Settings.Id("profile_c7a44da1-8e8e-4149-810b-8a161e1405a3:subscription_73680d3d-395e-40de-8c34-51b999bd2dee"),
      status = filesForRevision(revision)
    )
}
