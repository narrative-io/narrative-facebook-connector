package io.narrative.connectors.facebook.stores

import cats.{Eq, Show}
import cats.data.{NonEmptyList, OptionT}
import cats.effect.{IO, Timer}
import doobie.{ConnectionIO, Transactor, Update}
import doobie.implicits._
import doobie.implicits.legacy.instant._
import doobie.util.fragments.{in, whereAnd}
import io.narrative.connectors.facebook.domain.{Job, Profile, Revision}

import java.time.Instant
import scala.concurrent.duration._

case class JobStore(xa: Transactor[IO]) extends JobStore.Ops {
  override def blockingPoll[T](interval: FiniteDuration)(f: Job => IO[T])(implicit timer: Timer[IO]): IO[T] =
    OptionT(pollNoCommit).semiflatMap(f(_).to[ConnectionIO]).transact(xa).value.flatMap {
      case Some(value) => IO.pure(value)
      case None        => IO.sleep(interval) *> blockingPoll(interval)(f)
    }

  // can return none when the job definition conflicts with an existing one
  override def enqueue(job: JobStore.NewJob): IO[Option[Job]] = enqueue(NonEmptyList.one(job)).map(_.headOption)

  override def enqueue(jobs: NonEmptyList[JobStore.NewJob]): IO[List[Job]] = {
    val insertStmt = """
         |insert into jobs (
         |  event_revision,
         |  event_timestamp,
         |  quick_settings,
         |  payload,
         |  profile_id
         |) values (
         |  ?,
         |  ?,
         |  ?,
         |  ?,
         |  ?
         |)
         |on conflict (event_revision, payload)
         |do nothing""".stripMargin

    for {
      ids <- Update[JobStore.NewJob](insertStmt)
        .updateManyWithGeneratedKeys[Job.Id]("id")(jobs)
        .transact(xa)
        .compile
        .toList
      updated <- NonEmptyList.fromList(ids) match {
        case Some(idsNel) =>
          (fr"""
               |select
               |  id,
               |  created_at,
               |  event_revision,
               |  event_timestamp,
               |  quick_settings,
               |  payload,
               |  profile_id,
               |  updated_at
               |from jobs
               |""".stripMargin ++ whereAnd(in(fr"id", idsNel)))
            .query[
              (Job.Id, Instant, Revision, Instant, Option[Profile.QuickSettings], Job.Payload, Profile.Id, Instant)
            ]
            .map { case (id, createdAt, eventRevision, eventTimestamp, quickSettings, payload, profileId, updatedAt) =>
              Job(
                id = id,
                createdAt = createdAt,
                eventRevision = eventRevision,
                eventTimestamp = eventTimestamp,
                quickSettings = quickSettings,
                payload = payload,
                profileId = profileId,
                updatedAt = updatedAt
              )
            }
            .to[List]
            .transact(xa)
        case None =>
          IO.pure(List.empty)
      }
    } yield updated
  }

  override def poll[T](f: Option[Job] => IO[T]): IO[T] = {
    val pollT = for {
      job <- pollNoCommit
      result <- f(job).to[ConnectionIO]
    } yield result
    pollT.transact(xa)
  }

  private def pollNoCommit: ConnectionIO[Option[Job]] =
    sql"""
         |delete
         |from jobs
         |where id = (
         |  select id
         |  from jobs
         |  order by event_revision, id
         |  for update skip locked limit 1
         |)
         |returning
         |  id,
         |  created_at,
         |  event_revision,
         |  event_timestamp,
         |  quick_settings,
         |  payload,
         |  profile_id,
         |  updated_at""".stripMargin
      .query[(Job.Id, Instant, Revision, Instant, Option[Profile.QuickSettings], Job.Payload, Profile.Id, Instant)]
      .map { case (id, createdAt, eventRevision, eventTimestamp, quickSettings, payload, profileId, updatedAt) =>
        Job(
          id = id,
          createdAt = createdAt,
          eventRevision = eventRevision,
          eventTimestamp = eventTimestamp,
          quickSettings = quickSettings,
          payload = payload,
          profileId = profileId,
          updatedAt = updatedAt
        )
      }
      .option
}

object JobStore {
  // NB: not defined in terms of abstract effect F[_] as we accept a callback when polling for a new job and it's
  // easier to monomorphize on IO.
  trait ReadOps {}
  trait WriteOps {
    def blockingPoll[T](interval: FiniteDuration = 30.seconds)(f: Job => IO[T])(implicit timer: Timer[IO]): IO[T]
    def enqueue(job: NewJob): IO[Option[Job]]
    def enqueue(jobs: NonEmptyList[NewJob]): IO[List[Job]]
    def poll[T](f: Option[Job] => IO[T]): IO[T]
  }

  trait Ops extends ReadOps with WriteOps

  final case class NewJob(
      eventRevision: Revision,
      eventTimestamp: Instant,
      quickSettings: Profile.QuickSettings,
      payload: Job.Payload,
      profileId: Profile.Id
  )
  object NewJob {
    implicit val eq: Eq[NewJob] = Eq.fromUniversalEquals
    implicit val show: Show[NewJob] = Show.fromToString
  }
}
