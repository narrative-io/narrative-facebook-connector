package io.narrative.connectors.facebook

import cats.data.OptionT
import cats.effect.{Blocker, ContextShift, IO}
import cats.syntax.show._
import com.typesafe.scalalogging.LazyLogging
import io.circe.parser.parse
import io.narrative.connectors.api.connections.ConnectionsApi
import io.narrative.connectors.api.events.EventsApi.DeliveryEvent
import io.narrative.connectors.api.files.BackwardsCompatibleFilesApi
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
    parquetTransformer: ParquetTransformer,
    blocker: Blocker
) extends DeliveryProcessor.Ops[IO]
    with LazyLogging {

  override def process(job: Job)(implicit cs: ContextShift[IO]): IO[Unit] = {
    val deliverIO = for {
      command <- OptionT(commandStore.command(job.eventRevision))
        .getOrRaise(new RuntimeException(s"could not find command with revision ${job.eventRevision.show}"))
      settings <- OptionT(settingsStore.settings(command.settingsId))
        .getOrRaise(new RuntimeException(s"could not find settings with id ${command.settingsId.show}"))
      profileId <- (command.payload.payload match {
        case e: DeliveryEvent.SubscriptionDelivery => IO(e.profileId)
        case e: DeliveryEvent.SnapshotAppended     => connectionsApi.connection(e.connectionId).map(_.profileId)
      }).map(Profile.Id.apply)
      profile <- profile_!(profileId)
      token <- encryption.decrypt(profile.token.encrypted)
      _ <- deliverFile(job.eventRevision, job, command.payload.payload, settings, token)
      _ <- markDelivered(job, job.file)
    } yield ()

    deliverIO.handleErrorWith(markFailure(job, job.file, _))
  }

  private def deliverFile(
      eventRevision: Revision,
      job: Job,
      event: DeliveryEvent.Payload,
      settings: Settings,
      token: FacebookToken
  )(implicit
      cs: ContextShift[IO]
  ): IO[Unit] = {
    val (fileResource, parseAudience) = event match {
      case _: DeliveryEvent.SubscriptionDelivery =>
        (
          fileApi.downloadFile(eventRevision.value, job.file.value),
          AudienceParser.parseLegacy _
        )
      case _: DeliveryEvent.SnapshotAppended =>
        (
          fileApi.downloadFile(eventRevision.value, job.file.value).flatMap(parquetTransformer.toJson),
          AudienceParser.parseDataset _
        )
    }
    fileResource.use { path =>
      fs2.io.file
        .readAll[IO](path, blocker, 65536)
        .through(fs2.text.utf8Decode)
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
  trait ReadOps[F[_]] {}
  trait WriteOps[F[_]] {
    def process(job: Job)(implicit cs: ContextShift[F]): F[Unit]
  }

  trait Ops[F[_]] extends ReadOps[F] with WriteOps[F]

}
