package io.narrative.connectors.facebook.stores

import cats.arrow.FunctionK
import cats.effect.IO
import cats.syntax.functor._
import cats.syntax.show._
import doobie.{ConnectionIO, Transactor}
import doobie.implicits._
import doobie.implicits.legacy.instant._
import io.narrative.connectors.facebook.domain.{Audience, Settings}

import java.time.Instant

class SettingsStore() extends SettingsStore.Ops[ConnectionIO] {
  override def settings(id: Settings.Id): ConnectionIO[Option[Settings]] =
    sql"""
         |select
         |  id,
         |  audience_id,
         |  created_at,
         |  updated_at
         |from settings
         |where id = ${id}""".stripMargin
      .query[(Settings.Id, Audience.Id, Instant, Instant)]
      .map { case (id, audienceId, createdAt, updatedAt) =>
        Settings(
          id = id,
          audienceId = audienceId,
          createdAt = createdAt,
          updatedAt = updatedAt
        )
      }
      .option

  override def upsert(id: Settings.Id, audienceId: Audience.Id): ConnectionIO[Settings] =
    for {
      _ <-
        sql"""
             |insert into settings (
             |  id,
             |  audience_id,
             |  created_at,
             |  updated_at
             |) values (
             |  ${id},
             |  ${audienceId},
             |  now() at time zone 'UTC',
             |  now() at time zone 'UTC'
             |)
             |on conflict (id)
             |do update set
             |  audience_id = ${audienceId},
             |  updated_at = now() at time zone 'UTC'""".stripMargin.update.run.void
      updated <- settings(id).map(
        _.getOrElse(
          throw new RuntimeException(s"invariant violated: could not find settings with id ${id.show} after upsert")
        )
      )
    } yield updated
}

object SettingsStore {
  trait ReadOps[F[_]] {
    def settings(id: Settings.Id): F[Option[Settings]]
  }

  trait WriteOps[F[_]] {
    def upsert(id: Settings.Id, audienceId: Audience.Id): F[Settings]
  }

  trait Ops[F[_]] extends ReadOps[F] with WriteOps[F]

  def apply(xa: Transactor[IO]): SettingsStore.Ops[IO] = apply(
    new SettingsStore(),
    new FunctionK[ConnectionIO, IO] {
      override def apply[A](fa: ConnectionIO[A]): IO[A] = fa.transact(xa)
    }
  )

  def apply[F[_], G[_]](store: SettingsStore.Ops[F], fk: FunctionK[F, G]): SettingsStore.Ops[G] =
    new SettingsStore.Ops[G] {
      override def settings(id: Settings.Id): G[Option[Settings]] = fk(store.settings(id))

      override def upsert(id: Settings.Id, audienceId: Audience.Id): G[Settings] = fk(store.upsert(id, audienceId))
    }
}
