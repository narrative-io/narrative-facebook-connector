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
import io.narrative.connectors.api.datasets.DatasetFilesAPI
import io.narrative.connectors.model.SnapshotId
import io.narrative.connectors.facebook.domain.Profile.QuickSettings

/** Resolves commands saved by the [[CommandConsumer]]. Outputs a resolved, stored command and jobs for processing each
  * file in the delivery command.
  */
class CommandProcessor(
    commandStore: CommandStore.Ops[ConnectionIO],
    jobStore: QueueStore.Ops[Job, ConnectionIO],
    settingsService: SettingsService.Ops[IO],
    connectionsApi: ConnectionsApi.Ops[IO],
    filesApi: BackwardsCompatibleFilesApi.Ops[IO],
    datasetFilesApi: DatasetFilesAPI.Ops[IO]
) extends CommandProcessor.Ops[ConnectionIO]
    with LazyLogging {

  import CommandProcessor._

  override def process(
      event: CommandProcessorEvent,
      toConnectionIO: IO ~> ConnectionIO
  ): ConnectionIO[CommandProcessor.Result] =
    for {
      _ <- logInfo(s"processing ${event}")
      result <- event match {
        case event: ConnectionCreatedCPEvent    => processConnectionCreated(event, toConnectionIO)
        case event: SnapshotAppendedCPEvent     => processSnapshotAppended(event, toConnectionIO)
        case event: SubscriptionDeliveryCPEvent => processSubscriptionDelivery(event, toConnectionIO)
      }
    } yield result

  private def extractQuickSettings(obj: Option[JsonObject]): IO[Option[Profile.QuickSettings]] = obj.map { jsonObject =>
    EitherT.fromEither[IO](jsonObject.asJson.as[Profile.QuickSettings]).rethrowT
  }.sequence

  private def logInfo(str: String) = doobie.free.connection.delay(logger.info(str))

  private def processConnectionCreated(
      event: ConnectionCreatedCPEvent,
      toConnectionIO: IO ~> ConnectionIO
  ): ConnectionIO[CommandProcessor.Result] = for {
    connection <- toConnectionIO(connectionsApi.connection(event.connectionCreated.connectionId))
    quickSettings <- toConnectionIO(extractQuickSettings(connection.quickSettings))
    settings <- toConnectionIO(
      getSettings(
        Profile.Id.apply(connection.profileId),
        ConnectionId.apply(event.connectionCreated.connectionId),
        quickSettings
      )
    )
    deliverHistoricalData = quickSettings.flatMap(_.historicalDataEnabled).getOrElse(false)
    datasetId = connection.datasetId
    resp <-
      if (deliverHistoricalData)
        for {
          _ <- logInfo(
            show"historical data delivery enabled: enqueuing delivery of all files for dataset_id=${datasetId}"
          )
          searchPeriod = DatasetFilesAPI.RightOpenPeriod(until = event.metadata.timestamp)
          apiFiles <- toConnectionIO(datasetFilesApi.datasetFilesForPeriod(datasetId, searchPeriod))
          files = apiFiles.map(apiFile => FileToProcess(name = apiFile.file, snapshotId = apiFile.snapshotId))
          resp <- enqueueCommandAndJobs(
            data = event.connectionCreated,
            files = files,
            metadata = event.metadata,
            settingsId = settings.id
          )
        } yield resp
      else
        for {
          _ <- logInfo(
            show"historical data delivery disabled: skipping enqueuing of existing files for dataset_id=${datasetId}"
          )
        } yield Result.empty
  } yield resp

  private def enqueueCommandAndJobs(
      data: DeliveryEvent.Payload,
      files: List[FileToProcess],
      metadata: DeliveryEvent.Metadata,
      settingsId: Settings.Id
  ): ConnectionIO[Result] = {
    val newJobs = files.map { file =>
      Job(
        eventRevision = Revision.apply(metadata.revision.value),
        file = FileName.apply(file.name),
        snapshotId = file.snapshotId.some
      )
    }
    for {
      _ <- jobStore.enqueue(newJobs)
      command <- commandStore.upsert(
        CommandStore.NewCommand(
          eventRevision = Revision(metadata.revision.value),
          payload = DeliveryEvent(metadata, data),
          status = Command.Status.ProcessingFiles(files.map(file => FileName(file.name))),
          settingsId = settingsId
        )
      )
      _ <- logInfo(show"generated command. revision=${command.eventRevision.show}, payload=${command.payload.show}")
    } yield Result(command = command, jobs = newJobs)
  }

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
          file = FileName.apply(resp.name),
          snapshotId = none
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
      _ <- logInfo(s"generated command. revision=${command.eventRevision.show}, payload=${command.payload.show}")
    } yield Result(command = command, jobs = newJobs)

  private def processSnapshotAppended(
      event: SnapshotAppendedCPEvent,
      toConnectionIO: IO ~> ConnectionIO
  ): ConnectionIO[CommandProcessor.Result] =
    for {
      connection <- toConnectionIO(connectionsApi.connection(event.snapshotAppended.connectionId))
      quickSettings <- toConnectionIO(extractQuickSettings(connection.quickSettings))
      settings <- toConnectionIO(
        getSettings(
          Profile.Id.apply(connection.profileId),
          ConnectionId.apply(event.snapshotAppended.connectionId),
          quickSettings
        )
      )
      apiFiles <- toConnectionIO(filesApi.getFiles(event.metadata.revision.value))
      files = apiFiles.map(apiFile =>
        FileToProcess(name = apiFile.name, snapshotId = SnapshotId(event.snapshotAppended.snapshotId))
      )
      res <- enqueueCommandAndJobs(event.snapshotAppended, files, event.metadata, settings.id)
    } yield res

  private def getSettings(profileId: Profile.Id, connectionId: ConnectionId, quickSettings: Option[QuickSettings]) =
    settingsService
      .getOrCreate(
        quickSettings,
        Settings.Id
          .fromConnection(
            profileId,
            connectionId
          ),
        Audience.Name(s"Connection ${connectionId.show} Audience"),
        profileId
      )
}

object CommandProcessor {

  private final case class FileToProcess(name: String, snapshotId: SnapshotId)

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

  case class ConnectionCreatedCPEvent(
      metadata: DeliveryEvent.Metadata,
      connectionCreated: DeliveryEvent.ConnectionCreated
  ) extends CommandProcessorEvent

  trait ReadOps[F[_]] {}
  trait WriteOps[F[_]] {
    def process(event: CommandProcessorEvent, toConnectionIO: IO ~> ConnectionIO): F[Result]
  }

  trait Ops[F[_]] extends ReadOps[F] with WriteOps[F]

  /** The input job to process, including a projection of the payload to the required type of "ProcessCommand". */
  final case class Result(command: CommandType, jobs: List[Job])

  object Result {
    val empty = Result(NoCommand, List.empty)
  }

}
