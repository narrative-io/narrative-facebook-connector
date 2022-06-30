package io.narrative.connectors.facebook

import cats.effect.{Blocker, ContextShift, IO, Resource}
import com.amazonaws.auth.{AWSCredentialsProvider, DefaultAWSCredentialsProviderChain}
import com.amazonaws.regions.Regions
import com.amazonaws.services.kms.{AWSKMS, AWSKMSClientBuilder}
import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagement
import com.amazonaws.services.simplesystemsmanagement.model.GetParameterRequest
import com.typesafe.scalalogging.LazyLogging
import doobie.Transactor
import doobie.hikari.HikariTransactor
import doobie.util.ExecutionContexts
import io.narrative.common.ssm.SSMResources
import org.http4s.blaze.client.BlazeClientBuilder
import org.http4s.client.Client

import scala.concurrent.ExecutionContext

final case class Resources(
    awsCredentials: AWSCredentialsProvider,
    blocker: Blocker,
    client: Client[IO],
    kms: AWSKMS,
    serverEC: ExecutionContext,
    ssm: AWSSimpleSystemsManagement,
    xa: Transactor[IO]
) {
  def resolve(value: Config.Value)(implicit contextShift: ContextShift[IO]): IO[String] =
    Resources.resolve(blocker, ssm, value)
}
object Resources extends LazyLogging {
  def apply(config: Config)(implicit contextShift: ContextShift[IO]): Resource[IO, Resources] =
    for {
      blocker <- Blocker[IO]
      awsCredentials = new DefaultAWSCredentialsProviderChain()
      client <- BlazeClientBuilder[IO](blocker.blockingContext).resource
      serverEC <- ExecutionContexts.fixedThreadPool[IO](64)
      ssm <- SSMResources.ssmClient(logger.underlying, blocker, awsCredentials)
      kms <- awsKms(awsCredentials)
      xa <- transactor(blocker, ssm, config.database)
    } yield Resources(
      awsCredentials = awsCredentials,
      blocker = blocker,
      client = client,
      kms = kms,
      serverEC = serverEC,
      ssm = ssm,
      xa = xa
    )

  def awsKms(awsCredentials: AWSCredentialsProvider): Resource[IO, AWSKMS] =
    Resource.make[IO, AWSKMS](
      IO(AWSKMSClientBuilder.standard().withRegion(Regions.US_EAST_1).withCredentials(awsCredentials).build())
    )(kms => IO(kms.shutdown()))

  def transactor(blocker: Blocker, ssm: AWSSimpleSystemsManagement, db: Config.Database)(implicit
      contextShift: ContextShift[IO]
  ): Resource[IO, Transactor[IO]] = for {
    username <- Resource.eval(resolve(blocker, ssm, db.username))
    password <- Resource.eval(resolve(blocker, ssm, db.password))
    jdbcUrl <- Resource.eval(resolve(blocker, ssm, db.jdbcUrl))
    connectEC <- ExecutionContexts.fixedThreadPool[IO](32)
    xa <- HikariTransactor.newHikariTransactor[IO](
      blocker = blocker,
      connectEC = connectEC,
      driverClassName = "org.postgresql.Driver",
      pass = password,
      url = jdbcUrl,
      user = username
    )
  } yield xa

  def resolve(blocker: Blocker, ssm: AWSSimpleSystemsManagement, value: Config.Value)(implicit
      contextShift: ContextShift[IO]
  ): IO[String] =
    value match {
      case Config.Literal(value) =>
        IO.pure(value)
      case Config.SsmParam(value, encrypted) =>
        blocker
          .blockOn(IO(ssm.getParameter(new GetParameterRequest().withName(value).withWithDecryption(encrypted))))
          .map(_.getParameter.getValue)
          .attempt
          .map {
            case Left(err) =>
              throw new RuntimeException(
                s"caught exception resolving SSM parameter ${value} (withDecryption=${encrypted})",
                err
              )
            case Right(value) => value
          }
    }
}
