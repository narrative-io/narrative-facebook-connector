package io.narrative.connectors.facebook

import cats.effect.{IO, IOApp, Resource}
import cats.implicits.catsSyntaxTuple2Parallel
import com.typesafe.scalalogging.LazyLogging
import doobie.{ConnectionIO, WeakAsync}
import io.narrative.connectors.api.events.EventsApi.DeliveryEvent
import io.narrative.connectors.facebook.CommandProcessor.{SnapshotAppendedCPEvent, SubscriptionDeliveryCPEvent}
import io.narrative.connectors.queue.QueueConsumer
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.slf4j.Slf4jFactory

object Main extends IOApp.Simple with LazyLogging {
  private implicit val loggingConnectionIO: LoggerFactory[ConnectionIO] = Slf4jFactory.create[ConnectionIO]
  private implicit val logging: LoggerFactory[IO] = Slf4jFactory.create[IO]
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
  private def run(resources: Resources): IO[Unit] = WeakAsync.liftK[IO, ConnectionIO].use { toConnectionIO =>
    (
      resources.eventConsumer.consume(event =>
        event.payload match {
          case sd: DeliveryEvent.SubscriptionDelivery =>
            resources.eventProcessor
              .process(SubscriptionDeliveryCPEvent(event.metadata, sd), toConnectionIO)
              .map(_ => ())
          case sa: DeliveryEvent.SnapshotAppended =>
            resources.eventProcessor.process(SnapshotAppendedCPEvent(event.metadata, sa), toConnectionIO).map(_ => ())
          case DeliveryEvent.ConnectionCreated(connectionId) =>
            loggingConnectionIO.getLogger.info(s"Connection-created event [$connectionId] ignored")
        }
      ),
      QueueConsumer.parallelize(resources.queueStore, resources.transactor, parallelizationFactor) { job =>
        toConnectionIO(resources.deliveryProcessor.processIfDeliverable(job))
      }
    ).parMapN((_, _) => ())
  }

}
