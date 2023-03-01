package io.narrative.connectors.facebook

import cats.effect.{IO, IOApp, Resource}
import cats.implicits.catsSyntaxTuple2Parallel
import com.typesafe.scalalogging.LazyLogging
import doobie.ConnectionIO

import io.narrative.connectors.api.events.EventsApi.DeliveryEvent
import io.narrative.connectors.facebook.CommandProcessor.{SnapshotAppendedCPEvent, SubscriptionDeliveryCPEvent}
import io.narrative.connectors.queue.QueueConsumer

object Main extends IOApp.Simple with LazyLogging {
  val parallelizationFactor = 1
  override def run: IO[Unit] = {
    val worker = for {
      config <- Resource.eval(Config())
      resources <- Resources(config, parallelizationFactor)
      _ <- Resource.eval(run(resources))
    } yield ()

    worker.use(_ => IO.never).void
  }

  // Kick off command consumption and multiple job processors in parallel.
  // See https://typelevel.org/cats-effect/docs/2.x/datatypes/io#parallelism
  private def run(resources: Resources): IO[Unit] =
    (
      resources.eventConsumer.consume(event =>
        event.payload match {
          case sd: DeliveryEvent.SubscriptionDelivery =>
            resources.eventProcessor.process(SubscriptionDeliveryCPEvent(event.metadata, sd)).map(_ => ())
          case sa: DeliveryEvent.SnapshotAppended =>
            resources.eventProcessor.process(SnapshotAppendedCPEvent(event.metadata, sa)).map(_ => ())
          case DeliveryEvent.ConnectionCreated(connectionId) =>
            doobie.free.connection.delay(logger.info(s"Connection-created event [$connectionId] ignored"))
        }
      ),
      QueueConsumer.parallelize(resources.queueStore, resources.transactor, parallelizationFactor) { job =>
        resources.deliveryProcessor.processIfDeliverable(job).to[ConnectionIO]
      }
    ).parMapN((_, _) => ())

}
