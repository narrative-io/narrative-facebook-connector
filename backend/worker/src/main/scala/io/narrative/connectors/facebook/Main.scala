package io.narrative.connectors.facebook

import cats.effect.{IO, IOApp, Resource}
import cats.implicits._
import com.typesafe.scalalogging.LazyLogging
import doobie.ConnectionIO
import io.narrative.connectors.queue.QueueConsumer

object Main extends IOApp.Simple with LazyLogging {
  override def run: IO[Unit] = {
    val worker = for {
      config <- Resource.eval(Config())
      resources <- Resources(config)
      _ <- Resource.eval(run(resources))
    } yield ()

    worker.use(_ => IO.never).void
  }

  // Kick off command consumption and multiple job processors in parallel.
  // See https://typelevel.org/cats-effect/docs/2.x/datatypes/io#parallelism
  private def run(resources: Resources): IO[Unit] =
    (
      resources.eventConsumer.consume(resources.eventProcessor.process(_).map(_ => ())),
      QueueConsumer.parallelize(resources.queueStore, resources.transactor, 10) { job =>
        resources.deliveryProcessor.process(job).to[ConnectionIO]
      }
    ).parMapN((_, _) => ())

}
