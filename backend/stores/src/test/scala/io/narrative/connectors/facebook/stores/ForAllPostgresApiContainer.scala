package io.narrative.connectors.facebook.stores

import cats.effect._
import com.dimafeng.testcontainers.PostgreSQLContainer
import doobie._
import doobie.implicits._
import org.scalatest.Suite
import org.testcontainers.utility.DockerImageName
import io.narrative.common.doobie.DoobieTestResources
import io.narrative.common.testcontainers.CatsResourceForAllTestContainer
import cats.effect.Resource

trait ForAllPostgresApiContainer extends CatsResourceForAllTestContainer {
  self: Suite =>

  override type ContainerType = PostgreSQLContainer
  override type ResourceType = Sut

  override val container: PostgreSQLContainer = PostgreSQLContainer(
    dockerImageNameOverride =
      DockerImageName.parse("narrativeio/postgres-hll:11.5").asCompatibleSubstituteFor("postgres"),
    databaseName = "facebookconnector"
  )

  case class Sut(xa: Transactor[IO]) {
    def transact[T](connectionIO: ConnectionIO[T]): IO[T] = connectionIO.transact(xa)
  }

  override def mkResource(
      container: ContainerType
  ): Resource[IO, ResourceType] =
    for {
      xa <- DoobieTestResources.transactor[IO](
        container.driverClassName,
        container.jdbcUrl,
        container.username,
        container.password
      )
      _ <- Resource.eval[IO, Unit](
        DoobieTestResources.runMigrations(
          xa,
          container.jdbcUrl,
          container.username,
          container.password,
          List("classpath:facebookconnector-db/sql") // ++ List("classpath:migrations/")
        )
      )
    } yield Sut(xa)

}
