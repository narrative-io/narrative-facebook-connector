package io.narrative.connectors.facebook.stores

import cats.arrow.FunctionK
import cats.effect.IO
import cats.syntax.functor._
import doobie.ConnectionIO
import doobie.implicits._
import doobie.postgres.implicits._
import doobie.Fragments.set
import doobie.util.transactor.Transactor
import io.circe.syntax._
import io.narrative.connectors.api.events.EventsApi.DeliveryEvent
import io.narrative.connectors.facebook.domain.{Command, FileName, Revision, Settings}
import io.narrative.connectors.facebook.stores.CommandStore.StatusUpdate

import java.time.Instant

class CommandStore() extends CommandStore.Ops[ConnectionIO] {
  override def command(revision: Revision): ConnectionIO[Option[Command]] =
    sql"""
         |select
         |  created_at,
         |  event_revision,
         |  payload,
         |  settings_id,
         |  status,
         |  updated_at
         |from commands
         |where event_revision = ${revision}""".stripMargin
      .query[
        (
            Instant,
            Revision,
            DeliveryEvent,
            Settings.Id,
            Command.Status,
            Instant
        )
      ]
      .map {
        case (
              createdAt,
              eventRevision,
              payload,
              settingsId,
              status,
              updatedAt
            ) =>
          Command(
            createdAt = createdAt,
            eventRevision = eventRevision,
            payload = payload,
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
        |  payload,
        |  settings_id,
        |  status
        |) values (
        |  ${nc.eventRevision},
        |  ${nc.payload},
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
        import io.narrative.connectors.facebook.meta.JsonMeta.jsonMeta
        // We have to use do the sadness below because the more straightforward:
        //   fr"""status = jsonb_set(status, '{files,${file}}', '"${status}"', false)"""
        // Results in a prepared statement that looks like:
        //   Fragment("SET status = jsonb_set(status, '{files,?}', '"?"', false) ")
        // But JDBC does not like  parameters for prepared statements, namely ?, appearing in single quotes.
        // Instead we produce a safe prepared statement that looks more like:
        //   Fragment("SET status = jsonb_set(status, ? :: text[], ? :: jsonb, false) ")
        val path = Array[String]("files", file.value)
        set(fr"""status = jsonb_set(status, ${path} :: text[], ${status.asJson} :: jsonb, false)""")
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

  def apply(xa: Transactor[IO]): CommandStore.Ops[IO] = apply(
    new CommandStore(),
    new FunctionK[ConnectionIO, IO] {
      override def apply[A](fa: ConnectionIO[A]): IO[A] = fa.transact(xa)
    }
  )

  def apply[F[_], G[_]](store: CommandStore.Ops[F], fk: FunctionK[F, G]): CommandStore.Ops[G] =
    new CommandStore.Ops[G] {
      override def command(revision: Revision): G[Option[Command]] = fk(store.command(revision))

      override def upsert(command: NewCommand): G[Command] = fk(store.upsert(command))

      override def updateStatus(revision: Revision, update: StatusUpdate): G[Command] =
        fk(store.updateStatus(revision, update))
    }

  final case class NewCommand(
      eventRevision: Revision,
      payload: DeliveryEvent,
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
