package io.narrative.connectors.facebook.stores

import cats.arrow.FunctionK
import cats.effect.IO
import doobie.{ConnectionIO, Transactor}
import doobie.implicits._
import io.narrative.connectors.facebook.domain.Revision

class RevisionStore() extends RevisionStore.Ops[ConnectionIO] {
  override def currentRevision(): ConnectionIO[Revision] =
    sql"select revision from next_revision".query[Long].unique.map(Revision.apply)

  override def setNextRevision(revision: Revision): ConnectionIO[Revision] =
    sql"update next_revision set revision = $revision".update.run.map(_ => revision)
}

object RevisionStore {
  trait ReadOps[F[_]] {
    def currentRevision(): F[Revision]
  }

  trait WriteOps[F[_]] {
    def setNextRevision(revision: Revision): F[Revision]
  }

  trait Ops[F[_]] extends ReadOps[F] with WriteOps[F]

  def apply(xa: Transactor[IO]): RevisionStore.Ops[IO] = apply(
    new RevisionStore(),
    new FunctionK[ConnectionIO, IO] {
      override def apply[A](fa: ConnectionIO[A]): IO[A] = fa.transact(xa)
    }
  )

  def apply[F[_], G[_]](store: RevisionStore.Ops[F], fk: FunctionK[F, G]): RevisionStore.Ops[G] =
    new RevisionStore.Ops[G] {
      override def currentRevision(): G[Revision] = fk(store.currentRevision())

      override def setNextRevision(revision: Revision): G[Revision] = fk(store.setNextRevision(revision))
    }
}
