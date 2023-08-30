package io.narrative.connectors.facebook

import cats.data.OptionT
import cats.effect.IO
import cats.syntax.show._
import com.typesafe.scalalogging.LazyLogging
import fs2.io.file.Flags
import io.circe.parser.parse
import io.narrative.connectors.api.connections.ConnectionsApi
import io.narrative.connectors.api.events.EventsApi.DeliveryEvent
import io.narrative.connectors.api.events.EventsApi.DeliveryEvent.{SnapshotAppended, SubscriptionDelivery}
import io.narrative.connectors.api.files.BackwardsCompatibleFilesApi
import io.narrative.connectors.facebook.DeliveryProcessor.{
  DeliverableEntry,
  SnapshotAppendedEntry,
  SubscriptionDeliveryEntry
}
import io.narrative.connectors.facebook.domain.Command.FileStatus
import io.narrative.connectors.facebook.domain.{FileName, Job, Profile, Revision, Settings}
import io.narrative.connectors.facebook.services.{FacebookClient, FacebookToken, TokenEncryptionService}
import io.narrative.connectors.facebook.stores.CommandStore.StatusUpdate.FileUpdate
import io.narrative.connectors.facebook.stores.{CommandStore, ProfileStore, SettingsStore}
import io.narrative.connectors.spark.ParquetTransformer

class DeliveryProcessor(
    fileApi: BackwardsCompatibleFilesApi.Ops[IO],
    connectionsApi: ConnectionsApi.Ops[IO],
    commandStore: CommandStore.Ops[IO],
    settingsStore: SettingsStore.Ops[IO],
    encryption: TokenEncryptionService.Ops[IO],
    fb: FacebookClient.Ops[IO],
    profileStore: ProfileStore.Ops[IO],
    parquetTransformer: ParquetTransformer
) extends DeliveryProcessor.Ops[IO]
    with LazyLogging {

  override def processIfDeliverable(job: Job): IO[Unit] = {
    val deliverIO = for {
      command <- OptionT(commandStore.command(job.eventRevision))
        .getOrRaise(new RuntimeException(s"could not find command with revision ${job.eventRevision.show}"))
      _ <- command.payload.payload match {
        case sd: SubscriptionDelivery => processDeliverable(job, SubscriptionDeliveryEntry(sd, command.settingsId))
        case sa: SnapshotAppended     => processDeliverable(job, SnapshotAppendedEntry(sa, command.settingsId))
        case DeliveryEvent.ConnectionCreated(connectionId) =>
          IO(logger.info(s"Connection-created event [$connectionId] ignored"))
      }
    } yield ()

    deliverIO.handleErrorWith(markFailure(job, job.file, _))
  }

  private def processDeliverable(job: Job, deliverable: DeliverableEntry): IO[Unit] =
    for {
      settings <- OptionT(settingsStore.settings(deliverable.settingsId))
        .getOrRaise(new RuntimeException(s"could not find settings with id ${deliverable.settingsId.show}"))
      profileId <- (deliverable match {
        case SubscriptionDeliveryEntry(e, _) => IO(e.profileId)
        case SnapshotAppendedEntry(e, _)     => connectionsApi.connection(e.connectionId).map(_.profileId)
      }).map(Profile.Id.apply)
      profile <- profile_!(profileId)
      token <- encryption.decrypt(profile.token.encrypted)
      _ <- deliverFile(job.eventRevision, job, deliverable, settings, token)
      _ <- markDelivered(job, job.file)
    } yield ()

  private def deliverFile(
      eventRevision: Revision,
      job: Job,
      event: DeliverableEntry,
      settings: Settings,
      token: FacebookToken
  ): IO[Unit] = {
    val (fileResource, parseAudience) = event match {
      case _: SubscriptionDeliveryEntry =>
        (
          fileApi.downloadFile(eventRevision.value, job.file.value),
          AudienceParser.parseLegacy _
        )
      case _: SnapshotAppendedEntry =>
        (
          fileApi.downloadFile(eventRevision.value, job.file.value).flatMap(parquetTransformer.toJson),
          AudienceParser.parseDataset _
        )
    }
    fileResource.use { path =>
      fs2.io.file
        .Files[IO]
        .readAll(fs2.io.file.Path.fromNioPath(path), 65536, Flags.Read)
        .through(fs2.text.utf8.decode)
        .through(fs2.text.lines)
        .map(parse(_).toOption.map(parseAudience))
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
    _ <- IO(logger.info(s"delivery success. $job"))
    _ <- commandStore.updateStatus(job.eventRevision, FileUpdate(fileName, FileStatus.Delivered))
  } yield ()

  private def markFailure(job: Job, fileName: FileName, err: Throwable): IO[Unit] =
    for {
      _ <- IO(logger.error(s"delivery failed. $job", err))
      _ <- commandStore.updateStatus(job.eventRevision, FileUpdate(fileName, FileStatus.Failed))
    } yield ()
}

object DeliveryProcessor {
  sealed trait DeliverableEntry {
    def settingsId: Settings.Id
  }

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
