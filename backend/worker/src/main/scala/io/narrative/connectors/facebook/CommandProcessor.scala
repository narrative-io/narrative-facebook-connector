package io.narrative.connectors.facebook

import cats.Show
import cats.data.NonEmptyList
import cats.effect.IO
import cats.syntax.option._
import cats.syntax.show._
import com.typesafe.scalalogging.LazyLogging
import io.narrative.connectors.facebook.domain.{Audience, Command, FileName, Job, Settings}
import io.narrative.connectors.facebook.services.AppApiClient
import io.narrative.connectors.facebook.stores.{CommandStore, JobStore}

/** Resolves commands saved by the [[CommandConsumer]]. Outputs a resolved, stored command and jobs for processing each
  * file in the delivery command.
  */
class CommandProcessor(
    api: AppApiClient.Ops[IO],
    commandStore: CommandStore.Ops[IO],
    jobStore: JobStore.Ops,
    settingsService: SettingsService.Ops[IO]
) extends CommandProcessor.Ops[IO]
    with LazyLogging {
  import CommandProcessor._

  override def process(input: Input): IO[CommandProcessor.Result] = {
    for {
      _ <- IO(logger.info(s"processing ${input.show}"))
      settings <- settingsService.getOrCreate(input.job.quickSettings, input.payload, input.job.profileId)
      files <- api.files(input.job.eventRevision)
      newJobs = files.map(resp => mkJob(settings.audience.id, resp.file, input))
      jobs <- jobStore.enqueue(NonEmptyList.fromListUnsafe(newJobs)) // todo(mbabic) unsafe
      command <- commandStore.upsert(mkCommand(input, settings.id, Command.Status.ProcessingFiles(files.map(_.file))))
      _ <- IO(
        logger.info(s"generated command. revision=${command.eventRevision.show}, payload=${command.payload.show}")
      )
    } yield Result(command = command, jobs = jobs)
  }
}

object CommandProcessor {
  trait ReadOps[F[_]] {}
  trait WriteOps[F[_]] {
    def process(input: CommandProcessor.Input): F[Result]
  }

  trait Ops[F[_]] extends ReadOps[F] with WriteOps[F]

  // The input job to process, including a projection of the payload to the required type of "ProcessCommand".
  final case class Input(job: Job, payload: Job.CommandPayload)
  object Input {
    def apply(job: Job): Option[Input] = job.payload match {
      case p: Job.CommandPayload => Input(job, p).some
      case _                     => none
    }

    implicit val show: Show[Input] = Show.show(in =>
      s"input: id=${in.job.id.show}, revision=${in.job.eventRevision.show}, timestamp=${in.job.eventTimestamp}, payload=${in.payload.show}"
    )
  }
  final case class Result(command: Command, jobs: List[Job])

  private def mkCommand(input: Input, settingsId: Settings.Id, status: Command.Status): CommandStore.NewCommand =
    input.payload match {
      case Job.ProcessCommand(subscriptionId, transactionBatchId) =>
        CommandStore.NewCommand(
          eventRevision = input.job.eventRevision,
          eventTimestamp = input.job.eventTimestamp,
          quickSettings = input.job.quickSettings,
          payload =
            Command.SubscriptionDelivery(subscriptionId = subscriptionId, transactionBatchId = transactionBatchId),
          profileId = input.job.profileId,
          settingsId = settingsId,
          status = status
        )
    }

  private def mkJob(audienceId: Audience.Id, file: FileName, input: Input): JobStore.NewJob =
    input.payload match {
      case Job.ProcessCommand(subscriptionId, transactionBatchId) =>
        JobStore.NewJob(
          eventRevision = input.job.eventRevision,
          eventTimestamp = input.job.eventTimestamp,
          quickSettings = input.job.quickSettings,
          payload = Job.DeliverFile(
            audienceId = audienceId,
            file = file,
            subscriptionId = subscriptionId,
            transactionBatchId = transactionBatchId
          ),
          profileId = input.job.profileId
        )
    }

}
