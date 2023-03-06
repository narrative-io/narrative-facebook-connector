package io.narrative.connectors.facebook

import cats.data.EitherT
import cats.effect.IO
import cats.implicits._
import com.typesafe.scalalogging.LazyLogging
import doobie.ConnectionIO
import doobie.implicits._
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

  private def extractQuickSettings(obj: Option[JsonObject]): IO[Option[Profile.QuickSettings]] = obj.map { jsonObject =>
    EitherT.fromEither[IO](jsonObject.asJson.as[Profile.QuickSettings]).rethrowT
  }.sequence

  override def process(event: CommandProcessorEvent): ConnectionIO[CommandProcessor.Result] = {
    for {
      _ <- doobie.free.connection.delay(logger.info(s"processing ${event}"))
      result <- event match {
        case SnapshotAppendedCPEvent(metadata, snapshotAppended) =>
          for {
            connection <- connectionsApi.connection(snapshotAppended.connectionId).to[ConnectionIO]
            quickSettings <- extractQuickSettings(connection.quickSettings).to[ConnectionIO]

            settings <- settingsService
              .getOrCreate(
                quickSettings,
                Settings.Id
                  .fromConnection(
                    Profile.Id.apply(connection.profileId),
                    ConnectionId.apply(snapshotAppended.connectionId)
                  ),
                Audience.Name(s"Connection ${ConnectionId.apply(snapshotAppended.connectionId).show} Audience"),
                Profile.Id.apply(connection.profileId)
              )
              .to[ConnectionIO]
            files <- filesApi.getFiles(event.metadata.revision.value).to[ConnectionIO]
            newJobs = files.map { resp =>
              Job(
                eventRevision = Revision.apply(event.metadata.revision.value),
                file = FileName.apply(resp.name)
              )
            }
            _ <- jobStore.enqueue(newJobs)
            command <- commandStore.upsert(
              CommandStore.NewCommand(
                eventRevision = Revision(metadata.revision.value),
                payload = DeliveryEvent(metadata, snapshotAppended),
                settingsId = settings.id,
                status = Command.Status.ProcessingFiles(files.map(file => FileName(file.name)))
              )
            )
            result = Result(command = command, jobs = newJobs)
            _ <- doobie.free.connection.pure(
              logger.info(s"generated command. revision=${command.eventRevision.show}, payload=${command.payload.show}")
            )
          } yield result
        case SubscriptionDeliveryCPEvent(metadata, subscriptionDelivery) =>
          for {
            quickSettings <- extractQuickSettings(subscriptionDelivery.quickSettings).to[ConnectionIO]
            settings <- settingsService
              .getOrCreate(
                quickSettings,
                Settings.Id
                  .fromSubscription(
                    Profile.Id.apply(subscriptionDelivery.profileId),
                    SubscriptionId.apply(subscriptionDelivery.subscriptionId)
                  ),
                Audience.Name(
                  s"Subscription ${SubscriptionId.apply(subscriptionDelivery.subscriptionId).show} Audience"
                ),
                Profile.Id.apply(subscriptionDelivery.profileId)
              )
              .to[ConnectionIO]
            files <- filesApi.getFiles(metadata.revision.value).to[ConnectionIO]
            newJobs = files.map { resp =>
              Job(
                eventRevision = Revision.apply(metadata.revision.value),
                file = FileName.apply(resp.name)
              )
            }
            _ <- jobStore.enqueue(newJobs)
            command <- commandStore.upsert(
              CommandStore.NewCommand(
                eventRevision = Revision.apply(event.metadata.revision.value),
                payload = DeliveryEvent(metadata, subscriptionDelivery),
                settingsId = settings.id,
                status = Command.Status.ProcessingFiles(files.map(file => FileName(file.name)))
              )
            )
            result = Result(command = command, jobs = newJobs)
            _ <- doobie.free.connection.delay(
              logger.info(s"generated command. revision=${command.eventRevision.show}, payload=${command.payload.show}")
            )
          } yield result
      }
    } yield result
  }
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
      snapshotAppended: DeliveryEvent.SubscriptionDelivery
  ) extends CommandProcessorEvent

  trait ReadOps[F[_]] {}
  trait WriteOps[F[_]] {
    def process(event: CommandProcessorEvent): F[Result]
  }

  trait Ops[F[_]] extends ReadOps[F] with WriteOps[F]

  /** The input job to process, including a projection of the payload to the required type of "ProcessCommand". */
  final case class Result(command: Command, jobs: List[Job])

}
