package io.narrative.connectors.facebook

import cats.effect.{IO, IOApp, Resource}
import cats.implicits._
import com.typesafe.scalalogging.LazyLogging
import retry.{RetryDetails, RetryPolicies, retryingOnAllErrors}

import scala.concurrent.duration._

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
      retryForever(consumeIO(resources.commandConsumer)),
      parallelize(10)(retryForever(processIO(resources.jobProcessor)))
    ).parMapN((_, _) => ())

  private def consumeIO(consumer: CommandConsumer.Ops[IO]): IO[Unit] =
    consumer.tick(maxWip = 10).flatMap { result =>
      val duration = if (result.jobs.isEmpty) 1.minute else 0.minutes
      IO.sleep(duration) *> consumeIO(consumer)
    }

  // no need to sleep as the processor blocks waiting for work from the job queue
  private def processIO(processor: JobProcessor.Ops[IO]): IO[Unit] =
    processor.tick(10.seconds).flatMap(_ => processIO(processor))

  private def parallelize[A](n: Int)(f: => IO[A]): IO[Unit] = List.fill(n)(f).parSequence_

  private def retryForever[A](f: => IO[A]): IO[A] = {
    val retryPolicy = RetryPolicies.capDelay[IO](10.minutes, RetryPolicies.exponentialBackoff(1.second))
    val errorHandler = (error: Throwable, _: RetryDetails) => IO(logger.warn(s"caught exception. retrying ...", error))
    retryingOnAllErrors(retryPolicy, errorHandler)(f)
  }
}
