package io.narrative.connectors.facebook.delivery

import cats.effect.{IO, Timer}
import cats.syntax.show._
import com.typesafe.scalalogging.LazyLogging
import io.narrative.connectors.facebook.domain.Job
import io.narrative.connectors.facebook.stores.JobStore

import scala.concurrent.duration.FiniteDuration

class JobProcessor(
    commandProcessor: CommandProcessor.Ops[IO],
    deliveryProcessor: DeliveryProcessor.Ops[IO],
    jobStore: JobStore.Ops
)(implicit timer: Timer[IO])
    extends JobProcessor.Ops[IO]
    with LazyLogging {

  override def tick(pollInterval: FiniteDuration): IO[Unit] = jobStore.blockingPoll(pollInterval) { job =>
    for {
      _ <- IO(
        logger.info(
          s"starting job id=${job.id.show}, revision=${job.eventRevision.show}, timestamp=${job.eventTimestamp}, payload=${job.payload.show}"
        )
      )
      _ <- process(job)
      _ <- IO(logger.info(s"finished job id=${job.id.show}, revision=${job.eventRevision.show}"))
    } yield ()
  }

  private def process(job: Job): IO[Unit] = job.payload match {
    case command: Job.CommandPayload => commandProcessor.process(CommandProcessor.Input(job, command)).void
    case file: Job.FilePayload       => deliveryProcessor.process(DeliveryProcessor.Input(job, file)).void
  }
}

object JobProcessor {
  trait ReadOps[F[_]] {}
  trait WriteOps[F[_]] {
    def tick(pollInterval: FiniteDuration): IO[Unit]
  }

  trait Ops[F[_]] extends ReadOps[F] with WriteOps[F]
}
