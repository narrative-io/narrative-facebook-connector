package io.narrative.connectors.facebook.delivery

import cats.effect.{Blocker, ContextShift, IO, Resource, Timer}
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
import io.narrative.connectors.facebook.services.AppApiClient.{ClientId, ClientSecret}
import io.narrative.connectors.facebook.services.{AppApiClient, FacebookClient, KmsKeyId, TokenEncryptionService}
import io.narrative.connectors.facebook.stores.{CommandStore, JobStore, ProfileStore, RevisionStore, SettingsStore}
import org.http4s.Uri
import org.http4s.blaze.client.BlazeClientBuilder

final case class Resources(
    commandConsumer: CommandConsumer.Ops[IO],
    jobProcessor: JobProcessor.Ops[IO]
)
object Resources extends LazyLogging {
  def apply(config: Config)(implicit contextShift: ContextShift[IO], timer: Timer[IO]): Resource[IO, Resources] =
    for {
      blocker <- Blocker[IO]
      awsCredentials = new DefaultAWSCredentialsProviderChain()
      ssm <- SSMResources.ssmClient(logger.underlying, blocker, awsCredentials)
      xa <- transactor(blocker, ssm, config.database)
      api <- appApiClient(blocker, config, ssm)
      encryption <- encryptionService(awsCredentials, blocker, config, ssm)
      fb <- Resource.eval(facebookClient(blocker, config, ssm))

      commandStore = CommandStore(xa)
      jobStore = new JobStore(xa)
      profileStore = ProfileStore(xa)
      settingsStore = SettingsStore(xa)
      settingsService = new SettingsService(encryption, fb, profileStore, settingsStore)

      commandConsumer = new CommandConsumer(api, jobStore, RevisionStore(xa))

      commandProcessor = new CommandProcessor(api, commandStore, jobStore, settingsService)
      deliveryProcessor = new DeliveryProcessor(api, commandStore, encryption, fb, profileStore)
      jobProcessor = new JobProcessor(commandProcessor, deliveryProcessor, jobStore)
    } yield Resources(
      commandConsumer = commandConsumer,
      jobProcessor = jobProcessor
    )

  private def appApiClient(blocker: Blocker, config: Config, ssm: AWSSimpleSystemsManagement)(implicit
      contextShift: ContextShift[IO]
  ): Resource[IO, AppApiClient] =
    for {
      client <- BlazeClientBuilder[IO](blocker.blockingContext).resource
      baseUri <- Resource.eval(resolve(blocker, ssm, config.narrativeApi.baseUri)).map(Uri.unsafeFromString)
      id <- Resource.eval(resolve(blocker, ssm, config.narrativeApi.clientId)).map(ClientId.apply)
      secret <- Resource.eval(resolve(blocker, ssm, config.narrativeApi.clientSecret)).map(ClientSecret.apply)
      api <- Resource.eval(AppApiClient(baseUri, client, id, secret))
    } yield api

  private def awsKms(awsCredentials: AWSCredentialsProvider): Resource[IO, AWSKMS] =
    Resource.make[IO, AWSKMS](
      IO(AWSKMSClientBuilder.standard().withRegion(Regions.US_EAST_1).withCredentials(awsCredentials).build())
    )(kms => IO(kms.shutdown()))

  private def encryptionService(
      awsCredentials: AWSCredentialsProvider,
      blocker: Blocker,
      config: Config,
      ssm: AWSSimpleSystemsManagement
  )(implicit contextShift: ContextShift[IO]): Resource[IO, TokenEncryptionService.Ops[IO]] = for {
    kms <- awsKms(awsCredentials)
    keyId <- Resource.eval(resolve(blocker, ssm, config.kms.tokenEncryptionKeyId)).map(KmsKeyId.apply)
  } yield new TokenEncryptionService(blocker, keyId, kms)

  private def facebookClient(blocker: Blocker, config: Config, ssm: AWSSimpleSystemsManagement)(implicit
      contextShift: ContextShift[IO],
      timer: Timer[IO]
  ): IO[FacebookClient.Ops[IO]] = for {
    id <- resolve(blocker, ssm, config.facebook.appId)
    secret <- resolve(blocker, ssm, config.facebook.appSecret)
  } yield new FacebookClient(id, secret, blocker)

  private def transactor(blocker: Blocker, ssm: AWSSimpleSystemsManagement, db: Config.Database)(implicit
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

  private def resolve(blocker: Blocker, ssm: AWSSimpleSystemsManagement, value: Config.Value)(implicit
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
