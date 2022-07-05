package io.narrative.connectors.facebook.stores

import cats.syntax.functor._
import doobie.ConnectionIO
import doobie.implicits._
import doobie.implicits.legacy.instant._
import doobie.Fragments.set
import io.narrative.connectors.facebook.domain.{Command, FileName, Profile, Revision, Settings}
import io.narrative.connectors.facebook.stores.CommandStore.StatusUpdate

import java.time.Instant

class CommandStore() extends CommandStore.Ops[ConnectionIO] {
  override def command(revision: Revision): ConnectionIO[Option[Command]] =
    sql"""
         |select
         |  created_at,
         |  event_revision,
         |  event_timestamp,
         |  quick_settings,
         |  payload,
         |  profile_id,
         |  settings_id,
         |  status,
         |  updated_at
         |from commands
         |where event_revision = ${revision}""".stripMargin
      .query[
        (
            Instant,
            Revision,
            Instant,
            Profile.QuickSettings,
            Command.Payload,
            Profile.Id,
            Settings.Id,
            Command.Status,
            Instant
        )
      ]
      .map {
        case (
              createdAt,
              eventRevision,
              eventTimestamp,
              quickSettings,
              payload,
              profileId,
              settingsId,
              status,
              updatedAt
            ) =>
          Command(
            createdAt = createdAt,
            eventRevision = eventRevision,
            eventTimestamp = eventTimestamp,
            quickSettings = quickSettings,
            payload = payload,
            profileId = profileId,
            settingsId = settingsId,
            status = status,
            updatedAt = updatedAt
          )
      }
      .option

  override def upsert(nc: CommandStore.NewCommand): ConnectionIO[Command] = for {
    _ <-
      sql"""
        |insert into commands (
        |  event_revision,
        |  event_timestamp,
        |  quick_settings,
        |  payload,
        |  profile_id,
        |  settings_id,
        |  status
        |) values (
        |  ${nc.eventRevision},
        |  ${nc.eventTimestamp},
        |  ${nc.quickSettings},
        |  ${nc.payload},
        |  ${nc.profileId},
        |  ${nc.settingsId},
        |  ${nc.status}
        |)
        |on conflict (event_revision)
        |do update set
        |  status = ${nc.status},
        |  updated_at = now() at time zone 'UTC'
        """.stripMargin.update.run.void
    updated <- command_!(nc.eventRevision)
  } yield updated

  override def updateStatus(revision: Revision, update: CommandStore.StatusUpdate): ConnectionIO[Command] = {
    val setStmt = update match {
      case StatusUpdate.FileUpdate(file, status) =>
        set(fr"status = jsonb_set(status, '{files,${file}}', ${status}, false)")
      case StatusUpdate.CommandUpdate(value) =>
        set(fr"status = ${value}")
    }

    for {
      _ <- (fr"update commands" ++ setStmt ++ fr"where event_revision = ${revision}").update.run.void
      updated <- command_!(revision)
    } yield updated
  }

  private def command_!(revision: Revision): ConnectionIO[Command] =
    command(revision).map {
      case Some(value) => value
      case None =>
        throw new RuntimeException(
          s"invariant violated: could not find command for revision ${revision} after insertion or update"
        )
    }
}

object CommandStore {
  trait ReadOps[F[_]] {
    def command(revision: Revision): F[Option[Command]]
  }

  trait WriteOps[F[_]] {
    def upsert(command: NewCommand): F[Command]
    def updateStatus(revision: Revision, update: StatusUpdate): F[Command]
  }

  trait Ops[F[_]] extends ReadOps[F] with WriteOps[F]

  final case class NewCommand(
      eventRevision: Revision,
      eventTimestamp: Instant,
      quickSettings: Profile.QuickSettings,
      payload: Command.Payload,
      profileId: Profile.Id,
      settingsId: Settings.Id,
      status: Command.Status
  )
  sealed trait StatusUpdate
  object StatusUpdate {

    /** Update the given file with the given status. The update is atomic at the file-level: if another process
      * concurrently updates the status of a different file then both updates will be committed.
      */
    final case class FileUpdate(file: FileName, status: Command.FileStatus) extends StatusUpdate

    /** Overwrite the stored status with the given status. */
    final case class CommandUpdate(value: Command.Status) extends StatusUpdate
  }
}
