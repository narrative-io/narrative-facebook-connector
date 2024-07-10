package io.narrative.connectors.facebook

import cats.data.OptionT
import cats.effect.IO
import cats.syntax.show._
import fs2.io.file.Flags
import io.circe.parser.parse
import io.narrative.connectors.api.connections.ConnectionsApi
import io.narrative.connectors.api.events.EventsApi.DeliveryEvent
import io.narrative.connectors.api.events.EventsApi.DeliveryEvent.{
  SnapshotAppended,
  SubscriptionDelivery,
  ConnectionCreated
}
import io.narrative.connectors.api.files.BackwardsCompatibleFilesApi
import io.narrative.connectors.facebook.DeliveryProcessor.SubscriptionDeliveryEntry
import io.narrative.connectors.facebook.domain.Command.FileStatus
import io.narrative.connectors.facebook.domain.{FileName, Job, Profile, Revision, Settings}
import io.narrative.connectors.facebook.services.{FacebookClient, FacebookToken, TokenEncryptionService}
import io.narrative.connectors.facebook.stores.CommandStore.StatusUpdate.FileUpdate
import io.narrative.connectors.facebook.stores.{CommandStore, ProfileStore, SettingsStore}
import io.narrative.connectors.spark.ParquetTransformer
import org.typelevel.log4cats.{LoggerFactory, SelfAwareStructuredLogger}
import io.narrative.connectors.facebook.domain.ConnectionId
import io.narrative.connectors.facebook.domain.Audience
import io.narrative.connectors.model.DatasetId

// TODO: handle connection creation events?
class DeliveryProcessor(
    fileApi: BackwardsCompatibleFilesApi.Ops[IO],
    connectionsApi: ConnectionsApi.Ops[IO],
    commandStore: CommandStore.Ops[IO],
    settingsStore: SettingsStore.Ops[IO],
    encryption: TokenEncryptionService.Ops[IO],
    fb: FacebookClient.Ops[IO],
    profileStore: ProfileStore.Ops[IO],
    parquetTransformer: ParquetTransformer
)(implicit loggerFactory: LoggerFactory[IO])
    extends DeliveryProcessor.Ops[IO] {

  private val logger: SelfAwareStructuredLogger[IO] = loggerFactory.getLogger

  override def processIfDeliverable(job: Job): IO[Unit] = {
    val deliverIO = for {
      command <- OptionT(commandStore.command(job.eventRevision))
        .getOrRaise(new RuntimeException(s"could not find command with revision ${job.eventRevision.show}"))
      _ <- command.payload.payload match {
        case sd: SubscriptionDelivery =>
          processDeliverableSubscription(job, SubscriptionDeliveryEntry(sd, command.settingsId))
        case sa: SnapshotAppended =>
          processDeliverable(ConnectionId(sa.connectionId), command.settingsId, job)
        case cc: DeliveryEvent.ConnectionCreated =>
          processDeliverable(ConnectionId(cc.connectionId), command.settingsId, job)
      }
    } yield ()

    deliverIO.handleErrorWith(markFailure(job, job.file, _))
  }

  private def processDeliverable(connectionId: ConnectionId, settingsId: Settings.Id, job: Job): IO[Unit] =
    for {
      connection <- connectionsApi.connection(connectionId.value)
      profileId = Profile.Id(connection.profileId)
      settings <- OptionT(settingsStore.settings(settingsId))
        .getOrRaise(new RuntimeException(s"could not find settings with id ${settingsId.show}"))
      profile <- profile_!(profileId)
      token <- encryption.decrypt(profile.token.encrypted)
      _ <- IO.raiseWhen(job.snapshotId.isEmpty)(
        new RuntimeException(s"Job($job) requires a snapshot id for snapshots/connection created events.")
      )
      _ <- deliverFile(settings.audienceId, connection.datasetId, job, token)
      _ <- markDelivered(job, job.file)
    } yield ()

  private def deliverFile(
      audienceId: Audience.Id,
      datasetId: DatasetId,
      job: Job,
      token: FacebookToken
  ): IO[Unit] = {
    fileApi
      .downloadFile(datasetId.value, job.snapshotId.get.longValue, job.file.value)
      .flatMap(parquetTransformer.toJson)
      .use { path =>
        fs2.io.file
          .Files[IO]
          .readAll(fs2.io.file.Path.fromNioPath(path), 65536, Flags.Read)
          .through(fs2.text.utf8.decode)
          .through(fs2.text.lines)
          .map(parse(_).toOption.map(AudienceParser.parseDataset _))
          .unNone
          .chunkN(FacebookClient.AddToAudienceMaxBatchSize, true)
          .evalMapChunk(chunk => fb.addToAudience(token, audienceId, chunk.toList))
          .compile
          .drain
      }
  }

  private def processDeliverableSubscription(job: Job, deliverable: SubscriptionDeliveryEntry): IO[Unit] =
    for {
      settings <- OptionT(settingsStore.settings(deliverable.settingsId))
        .getOrRaise(new RuntimeException(s"could not find settings with id ${deliverable.settingsId.show}"))
      profileId = Profile.Id(deliverable.subscriptionDelivery.profileId)
      profile <- profile_!(profileId)
      token <- encryption.decrypt(profile.token.encrypted)
      _ <- deliverFileSubscription(job.eventRevision, job, settings, token)
      _ <- markDelivered(job, job.file)
    } yield ()

  private def deliverFileSubscription(
      eventRevision: Revision,
      job: Job,
      settings: Settings,
      token: FacebookToken
  ): IO[Unit] = {
    fileApi.downloadFile(eventRevision.value, job.file.value).use { path =>
      fs2.io.file
        .Files[IO]
        .readAll(fs2.io.file.Path.fromNioPath(path), 65536, Flags.Read)
        .through(fs2.text.utf8.decode)
        .through(fs2.text.lines)
        .map(parse(_).toOption.map(AudienceParser.parseLegacy _))
        .unNone
        .chunkN(FacebookClient.AddToAudienceMaxBatchSize, allowFewer = true)
        .evalMapChunk(chunk => fb.addToAudience(token, settings.audienceId, chunk.toList))
        .compile
        .drain
    }
  }

  private def profile_!(profileId: Profile.Id): IO[Profile] =
    OptionT(profileStore.profile(profileId))
      .getOrRaise(new RuntimeException(s"profile ${profileId.show} does not exist."))

  private def markDelivered(job: Job, fileName: FileName): IO[Unit] = for {
    _ <- logger.info(s"delivery success. $job")
    _ <- commandStore.updateStatus(job.eventRevision, FileUpdate(fileName, FileStatus.Delivered))
  } yield ()

  private def markFailure(job: Job, fileName: FileName, err: Throwable): IO[Unit] =
    for {
      _ <- logger.error(err)(s"delivery failed. $job")
      _ <- commandStore.updateStatus(job.eventRevision, FileUpdate(fileName, FileStatus.Failed))
    } yield ()
}

object DeliveryProcessor {
  sealed trait DeliverableEntry {
    def settingsId: Settings.Id
  }

  case class ConnectionCreatedEntry(
      connectionCreated: ConnectionCreated,
      settingsId: Settings.Id
  ) extends DeliverableEntry
  case class SnapshotAppendedEntry(
      snapshotAppended: SnapshotAppended,
      settingsId: Settings.Id
  ) extends DeliverableEntry

  case class SubscriptionDeliveryEntry(
      subscriptionDelivery: SubscriptionDelivery,
      settingsId: Settings.Id
  ) extends DeliverableEntry

  trait ReadOps[F[_]] {}
  trait WriteOps[F[_]] {
    def processIfDeliverable(job: Job): F[Unit]
  }

  trait Ops[F[_]] extends ReadOps[F] with WriteOps[F]

}
