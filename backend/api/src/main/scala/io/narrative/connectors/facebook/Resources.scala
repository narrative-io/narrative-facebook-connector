package io.narrative.connectors.facebook

import cats.effect.{IO, Resource}
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
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.client.Client

final case class Resources(
    awsCredentials: AWSCredentialsProvider,
    client: Client[IO],
    kms: AWSKMS,
    ssm: AWSSimpleSystemsManagement,
    xa: Transactor[IO]
) {
  def resolve(value: Config.Value): IO[String] =
    Resources.resolve(ssm, value)
}
object Resources extends LazyLogging {
  def apply(config: Config): Resource[IO, Resources] =
    for {
      awsCredentials <- Resource.eval(IO.blocking(new DefaultAWSCredentialsProviderChain()))
      client <- EmberClientBuilder.default[IO].build
      ssm <- SSMResources.ssmClient(logger.underlying, awsCredentials)
      kms <- awsKms(awsCredentials)
      xa <- transactor(ssm, config.database)
    } yield Resources(
      awsCredentials = awsCredentials,
      client = client,
      kms = kms,
      ssm = ssm,
      xa = xa
    )

  def awsKms(awsCredentials: AWSCredentialsProvider): Resource[IO, AWSKMS] =
    Resource.make[IO, AWSKMS](
      IO(AWSKMSClientBuilder.standard().withRegion(Regions.US_EAST_1).withCredentials(awsCredentials).build())
    )(kms => IO(kms.shutdown()))

  def transactor(ssm: AWSSimpleSystemsManagement, db: Config.Database): Resource[IO, Transactor[IO]] = for {
    username <- Resource.eval(resolve(ssm, db.username))
    password <- Resource.eval(resolve(ssm, db.password))
    jdbcUrl <- Resource.eval(resolve(ssm, db.jdbcUrl))
    connectEC <- ExecutionContexts.fixedThreadPool[IO](32)
    xa <- HikariTransactor.newHikariTransactor[IO](
      connectEC = connectEC,
      driverClassName = "org.postgresql.Driver",
      pass = password,
      url = jdbcUrl,
      user = username
    )
  } yield xa

  def resolve(ssm: AWSSimpleSystemsManagement, value: Config.Value): IO[String] =
    value match {
      case Config.Literal(value) =>
        IO.pure(value)
      case Config.SsmParam(value, encrypted) =>
        IO.blocking(ssm.getParameter(new GetParameterRequest().withName(value).withWithDecryption(encrypted)))
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
