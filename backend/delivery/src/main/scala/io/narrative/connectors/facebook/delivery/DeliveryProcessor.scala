package io.narrative.connectors.facebook.delivery

import cats.Show
import cats.effect.IO
import cats.syntax.option._
import cats.syntax.show._
import com.typesafe.scalalogging.LazyLogging
import io.circe.Json
import io.circe.parser.parse
import io.narrative.connectors.facebook.domain.Command.FileStatus
import io.narrative.connectors.facebook.domain.{Audience, FileName, Job}
import io.narrative.connectors.facebook.services.{
  AppApiClient,
  FacebookAudienceMember,
  FacebookClient,
  FacebookToken,
  TokenEncryptionService
}
import io.narrative.connectors.facebook.stores.CommandStore.StatusUpdate.FileUpdate
import io.narrative.connectors.facebook.stores.{CommandStore, ProfileStore}

class DeliveryProcessor(
    api: AppApiClient.Ops[IO],
    commandStore: CommandStore.Ops[IO],
    encryption: TokenEncryptionService.Ops[IO],
    fb: FacebookClient.Ops[IO],
    profileStore: ProfileStore.Ops[IO]
) extends DeliveryProcessor.Ops[IO]
    with LazyLogging {
  import DeliveryProcessor._

  override def process(input: DeliveryProcessor.Input): IO[Unit] = {
    val deliverIO = for {
      profile <- profileStore.profile(input.job.profileId).map(_.get)
      token <- encryption.decrypt(profile.token.encrypted)
      _ <- deliverFile(input, token)
      _ <- markDelivered(input)
    } yield ()

    deliverIO.handleErrorWith(markFailure(input, _))
  }

  private def deliverFile(input: Input, token: FacebookToken): IO[Unit] =
    api
      .download(input.job.eventRevision, file(input))
      .through(fs2.text.utf8Decode)
      .through(fs2.text.lines)
      .map(parse(_).toOption.flatMap(audienceMember))
      .unNone
      .chunkN(FacebookClient.AddToAudienceMaxBatchSize, allowFewer = true)
      .evalMapChunk(chunk => fb.addToAudience(token, audienceId(input), chunk.toList))
      .compile
      .drain

  private def markDelivered(input: Input): IO[Unit] = for {
    _ <- IO(logger.info(s"successfully delivered ${input.show}"))
    _ <- commandStore.updateStatus(input.job.eventRevision, FileUpdate(file(input), FileStatus.Delivered))
  } yield ()

  private def markFailure(input: Input, err: Throwable): IO[Unit] =
    for {
      _ <- IO(logger.error(s"delivery failed ${input.show}", err))
      _ <- commandStore.updateStatus(input.job.eventRevision, FileUpdate(file(input), FileStatus.Failed))
    } yield ()
}

object DeliveryProcessor {
  trait ReadOps[F[_]] {}
  trait WriteOps[F[_]] {
    def process(input: Input): F[Unit]
  }

  trait Ops[F[_]] extends ReadOps[F] with WriteOps[F]

  final case class Input(job: Job, payload: Job.FilePayload)
  object Input {
    implicit val show: Show[Input] = Show.show(in =>
      s"input: revision=${in.job.eventRevision.show}, timestamp=${in.job.eventTimestamp}, audienceId=${audienceId(in)}, file=${file(in).show}, "
    )
  }

  /** Parse a delivered json row to a Facebook audience member. NB: the MVP assumes the delivery is all MAIDs and only
    * supports purchasing the unique_id attribute.
    *
    * Exposed for testing.
    */
  def audienceMember(json: Json): Option[FacebookAudienceMember] =
    json.hcursor
      .downField("data")
      .downField("unique_id")
      .get[String]("value")
      .toOption
      .map(idValue => FacebookAudienceMember(maid = idValue.some))

  private def audienceId(input: Input): Audience.Id = input.payload match {
    case df: Job.DeliverFile => df.audienceId
  }

  private def file(input: Input): FileName = input.payload match {
    case df: Job.DeliverFile => df.file
  }

}
