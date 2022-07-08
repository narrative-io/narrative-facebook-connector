package io.narrative.connectors.facebook

import cats.data.NonEmptyList
import cats.effect.IO
import cats.syntax.show._
import com.typesafe.scalalogging.LazyLogging
import io.narrative.connectors.facebook.domain.{Job, Revision}
import io.narrative.connectors.facebook.services.{ApiCommands, ApiDeliveryCommand, AppApiClient}
import io.narrative.connectors.facebook.stores.{JobStore, RevisionStore}

/** Polls the narrative API to look for new commands. If found, the commands are enqueued as jobs for further
  * processing.
  */
class CommandConsumer(api: AppApiClient.Ops[IO], jobStore: JobStore.Ops, revisionStore: RevisionStore.Ops[IO])
    extends CommandConsumer.Ops[IO]
    with LazyLogging {
  import CommandConsumer._

  override def tick(maxWip: Int): IO[CommandConsumer.Result] =
    for {
      revision <- revisionStore.currentRevision()
      _ <- IO(logger.info(s"polling for new command from revision ${revision.show} ..."))
      commands <- api.commands(revision, maxResults = maxWip)
      jobs <- consume(commands)
    } yield CommandConsumer.Result(jobs = jobs, nextRevision = commands.nextRevision)

  private def consume(commands: ApiCommands): IO[List[Job]] = {
    val jobs = commands.records.map(mkJob)
    for {
      enqueued <- NonEmptyList.fromList(jobs) match {
        case Some(jobsNel) => jobStore.enqueue(jobsNel)
        case None          => IO.pure(List.empty)
      }
      _ <-
        if (enqueued.nonEmpty)
          for {
            _ <- IO(logger.info(s"enqueued ${enqueued.size} jobs. next revision: ${commands.nextRevision.show}"))
            _ <- revisionStore.setNextRevision(commands.nextRevision)
          } yield ()
        else
          IO(logger.info(s"empty poll: no new commands to process. next revision: ${commands.nextRevision.show}"))
    } yield enqueued
  }
}

object CommandConsumer {
  trait Ops[F[_]] {

    /** @param maxWip
      *   The maximum number of commands to request from the Narrative API.
      * @return
      *   The jobs enqueued and the next revision to process. See [[Result]]
      */
    def tick(maxWip: Int): F[Result]
  }

  final case class Result(jobs: List[Job], nextRevision: Revision)

  private def mkJob(command: ApiDeliveryCommand): JobStore.NewJob =
    command.payload match {
      case ApiDeliveryCommand.DeliverFiles(quickSettings, profileId, subscriptionId, transactionBatchId) =>
        JobStore.NewJob(
          eventRevision = command.metadata.revision,
          eventTimestamp = command.metadata.timestamp,
          quickSettings = quickSettings,
          payload = Job.ProcessCommand(subscriptionId, transactionBatchId),
          profileId = profileId
        )
    }
}
