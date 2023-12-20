package io.narrative.connectors.facebook

import cats.data.EitherT
import cats.effect.IO
import cats.implicits._
import cats.~>
import com.typesafe.scalalogging.LazyLogging
import doobie.ConnectionIO
import io.circe.JsonObject
import io.circe.syntax.EncoderOps
import io.narrative.connectors.api.connections.ConnectionsApi
import io.narrative.connectors.api.events.EventsApi.DeliveryEvent
import io.narrative.connectors.api.files.BackwardsCompatibleFilesApi
import io.narrative.connectors.facebook.domain._
import io.narrative.connectors.facebook.stores.CommandStore
import io.narrative.connectors.queue.QueueStore

/** Resolves commands saved by the [[CommandConsumer]]. Outputs a resolved, stored command and jobs for processing each
  * file in the delivery command.
  */
class CommandProcessor(
    commandStore: CommandStore.Ops[ConnectionIO],
    jobStore: QueueStore.Ops[Job, ConnectionIO],
    settingsService: SettingsService.Ops[IO],
    connectionsApi: ConnectionsApi.Ops[IO],
    filesApi: BackwardsCompatibleFilesApi.Ops[IO]
) extends CommandProcessor.Ops[ConnectionIO]
    with LazyLogging {

  import CommandProcessor._

  override def process(
      event: CommandProcessorEvent,
      toConnectionIO: IO ~> ConnectionIO
  ): ConnectionIO[CommandProcessor.Result] =
    for {
      _ <- doobie.free.connection.delay(logger.info(s"processing ${event}"))
      result <- event match {
        case event: SnapshotAppendedCPEvent     => processSnapshotAppended(event, toConnectionIO)
        case event: SubscriptionDeliveryCPEvent => processSubscriptionDelivery(event, toConnectionIO)
      }
    } yield result

  private def extractQuickSettings(obj: Option[JsonObject]): IO[Option[Profile.QuickSettings]] = obj.map { jsonObject =>
    EitherT.fromEither[IO](jsonObject.asJson.as[Profile.QuickSettings]).rethrowT
  }.sequence

  private def processSubscriptionDelivery(
      event: SubscriptionDeliveryCPEvent,
      toConnectionIO: IO ~> ConnectionIO
  ): ConnectionIO[CommandProcessor.Result] =
    for {
      quickSettings <- toConnectionIO(extractQuickSettings(event.subscriptionDelivery.quickSettings))
      settings <- toConnectionIO(
        settingsService
          .getOrCreate(
            quickSettings,
            Settings.Id
              .fromSubscription(
                Profile.Id.apply(event.subscriptionDelivery.profileId),
                SubscriptionId.apply(event.subscriptionDelivery.subscriptionId)
              ),
            Audience.Name(
              s"Subscription ${SubscriptionId.apply(event.subscriptionDelivery.subscriptionId).show} Audience"
            ),
            Profile.Id.apply(event.subscriptionDelivery.profileId)
          )
      )
      files <- toConnectionIO(filesApi.getFiles(event.metadata.revision.value))
      newJobs = files.map { resp =>
        Job(
          eventRevision = Revision.apply(event.metadata.revision.value),
          file = FileName.apply(resp.name)
        )
      }
      _ <- jobStore.enqueue(newJobs)
      command <- commandStore.upsert(
        CommandStore.NewCommand(
          eventRevision = Revision.apply(event.metadata.revision.value),
          payload = DeliveryEvent(event.metadata, event.subscriptionDelivery),
          settingsId = settings.id,
          status = Command.Status.ProcessingFiles(files.map(file => FileName(file.name)))
        )
      )
      result = Result(command = command, jobs = newJobs)
      _ <- doobie.free.connection.delay(
        logger.info(s"generated command. revision=${command.eventRevision.show}, payload=${command.payload.show}")
      )
    } yield result

  private def processSnapshotAppended(
      event: SnapshotAppendedCPEvent,
      toConnectionIO: IO ~> ConnectionIO
  ): ConnectionIO[CommandProcessor.Result] =
    for {
      connection <- toConnectionIO(connectionsApi.connection(event.snapshotAppended.connectionId))
      quickSettings <- toConnectionIO(extractQuickSettings(connection.quickSettings))

      settings <- toConnectionIO(
        settingsService
          .getOrCreate(
            quickSettings,
            Settings.Id
              .fromConnection(
                Profile.Id.apply(connection.profileId),
                ConnectionId.apply(event.snapshotAppended.connectionId)
              ),
            Audience.Name(s"Connection ${ConnectionId.apply(event.snapshotAppended.connectionId).show} Audience"),
            Profile.Id.apply(connection.profileId)
          )
      )
      files <- toConnectionIO(filesApi.getFiles(event.metadata.revision.value))
      newJobs = files.map { resp =>
        Job(
          eventRevision = Revision.apply(event.metadata.revision.value),
          file = FileName.apply(resp.name)
        )
      }
      _ <- jobStore.enqueue(newJobs)
      command <- commandStore.upsert(
        CommandStore.NewCommand(
          eventRevision = Revision(event.metadata.revision.value),
          payload = DeliveryEvent(event.metadata, event.snapshotAppended),
          settingsId = settings.id,
          status = Command.Status.ProcessingFiles(files.map(file => FileName(file.name)))
        )
      )
      result = Result(command = command, jobs = newJobs)
      _ <- doobie.free.connection.pure(
        logger.info(s"generated command. revision=${command.eventRevision.show}, payload=${command.payload.show}")
      )
    } yield result
}

object CommandProcessor {

  sealed trait CommandProcessorEvent {
    def metadata: DeliveryEvent.Metadata
  }

  case class SnapshotAppendedCPEvent(
      metadata: DeliveryEvent.Metadata,
      snapshotAppended: DeliveryEvent.SnapshotAppended
  ) extends CommandProcessorEvent

  case class SubscriptionDeliveryCPEvent(
      metadata: DeliveryEvent.Metadata,
      subscriptionDelivery: DeliveryEvent.SubscriptionDelivery
  ) extends CommandProcessorEvent

  trait ReadOps[F[_]] {}
  trait WriteOps[F[_]] {
    def process(event: CommandProcessorEvent, toConnectionIO: IO ~> ConnectionIO): F[Result]
  }

  trait Ops[F[_]] extends ReadOps[F] with WriteOps[F]

  /** The input job to process, including a projection of the payload to the required type of "ProcessCommand". */
  final case class Result(command: Command, jobs: List[Job])

}
