package io.narrative.connectors.facebook

import cats.effect.{IO, Resource}
import com.amazonaws.auth.{AWSCredentialsProvider, DefaultAWSCredentialsProviderChain}
import com.amazonaws.regions.Regions
import com.amazonaws.services.kms.{AWSKMS, AWSKMSClientBuilder}
import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagement
import com.amazonaws.services.simplesystemsmanagement.model.GetParameterRequest
import com.typesafe.scalalogging.LazyLogging
import doobie.{ConnectionIO, Transactor}
import doobie.hikari.HikariTransactor
import doobie.util.ExecutionContexts
import io.narrative.common.ssm.SSMResources
import io.narrative.connectors.api.BaseAppApiClient
import io.narrative.connectors.api.connections.ConnectionsApi
import io.narrative.connectors.api.events.{EventConsumer, EventRevisionStore, EventsApi}
import io.narrative.connectors.api.files.BackwardsCompatibleFilesApi
import io.narrative.connectors.api.files.v1.FilesApiV1
import io.narrative.connectors.api.files.v2.FilesApiV2
import io.narrative.connectors.facebook.domain.Job
import io.narrative.connectors.facebook.services.AppApiClient.{ClientId, ClientSecret}
import io.narrative.connectors.facebook.services.{FacebookApp, FacebookClient, KmsKeyId, TokenEncryptionService}
import io.narrative.connectors.facebook.stores.{CommandStore, ProfileStore, SettingsStore}
import io.narrative.connectors.queue.QueueStore
import io.narrative.connectors.spark.{ParquetTransformer, SparkSessions}
import io.narrative.microframework.config.Stage
import org.http4s.ember.client.EmberClientBuilder
import cats.effect.Temporal
import org.typelevel.log4cats.LoggerFactory

final case class Resources(
    eventConsumer: EventConsumer.Ops[IO],
    eventProcessor: CommandProcessor.Ops[ConnectionIO],
    deliveryProcessor: DeliveryProcessor.Ops[IO],
    queueStore: QueueStore.Ops[Job, ConnectionIO],
    parquetTransformer: ParquetTransformer,
    transactor: Transactor[IO]
)
object Resources extends LazyLogging {

  def apply(config: Config, parallelizationFactor: Int)(implicit
      timer: Temporal[IO],
      logging: LoggerFactory[IO]
  ): Resource[IO, Resources] =
    for {
      awsCredentials <- Resource.eval(IO.blocking(new DefaultAWSCredentialsProviderChain()))
      ssm <- SSMResources.ssmClient(logger.underlying, awsCredentials)
      xa <- transactor(ssm, config.database, parallelizationFactor)
      api <- baseAppApiClient(config, ssm)
      encryption <- encryptionService(awsCredentials, config, ssm)
      fb <- Resource.eval(facebookClient(config, ssm))
      sparkSessions <- Resource.eval(SparkSessions.default[IO])
      sparkSession <- Resource.eval(sparkSessions.getSparkSession)

      parquetTransformer = new ParquetTransformer(sparkSession)

      commandStore = new CommandStore()
      profileStore = ProfileStore(xa)
      settingsStore = SettingsStore(xa)
      settingsService = new SettingsService(encryption, fb, profileStore, settingsStore)
      jobStore = new QueueStore[Job]
      revisionStore = new EventRevisionStore

      connectionsApi = new ConnectionsApi(api)
      filesApiV1 = new FilesApiV1(api)
      filesApiV2 = new FilesApiV2(api)
      eventsApi = new EventsApi(api)
      filesApi = new BackwardsCompatibleFilesApi(eventsApi, filesApiV1, filesApiV2)

      commandProcessor = new CommandProcessor(commandStore, jobStore, settingsService, connectionsApi, filesApi)
      deliveryProcessor = new DeliveryProcessor(
        filesApi,
        connectionsApi,
        CommandStore(xa),
        settingsStore,
        encryption,
        fb,
        profileStore,
        parquetTransformer
      )
      eventConsumer = new EventConsumer(eventsApi, revisionStore, xa)

    } yield Resources(
      eventConsumer = eventConsumer,
      eventProcessor = commandProcessor,
      deliveryProcessor = deliveryProcessor,
      queueStore = jobStore,
      parquetTransformer = parquetTransformer,
      transactor = xa
    )

  def baseAppApiClient(config: Config, ssm: AWSSimpleSystemsManagement): Resource[IO, BaseAppApiClient.Ops[IO]] =
    for {
      blazeClient <- EmberClientBuilder.default[IO].build
      id <- Resource.eval(resolve(ssm, config.narrativeApi.clientId)).map(ClientId.apply)
      secret <- Resource.eval(resolve(ssm, config.narrativeApi.clientSecret)).map(ClientSecret.apply)

      narrativeApiClient <- config.stage match {
        case Stage.Prod => BaseAppApiClient.production(blazeClient, id.value, secret.value)
        case _          => BaseAppApiClient.development(blazeClient, id.value, secret.value)
      }

    } yield narrativeApiClient

  private def awsKms(awsCredentials: AWSCredentialsProvider): Resource[IO, AWSKMS] =
    Resource.make[IO, AWSKMS](
      IO(AWSKMSClientBuilder.standard().withRegion(Regions.US_EAST_1).withCredentials(awsCredentials).build())
    )(kms => IO(kms.shutdown()))

  private def encryptionService(
      awsCredentials: AWSCredentialsProvider,
      config: Config,
      ssm: AWSSimpleSystemsManagement
  ): Resource[IO, TokenEncryptionService.Ops[IO]] = for {
    kms <- awsKms(awsCredentials)
    keyId <- Resource.eval(resolve(ssm, config.kms.tokenEncryptionKeyId)).map(KmsKeyId.apply)
  } yield new TokenEncryptionService(keyId, kms)

  private def facebookClient(config: Config, ssm: AWSSimpleSystemsManagement): IO[FacebookClient.Ops[IO]] = for {
    id <- resolve(ssm, config.facebook.appId).map(FacebookApp.Id.apply)
    secret <- resolve(ssm, config.facebook.appSecret).map(FacebookApp.Secret.apply)
  } yield new FacebookClient(FacebookApp(id, secret))

  private def transactor(
      ssm: AWSSimpleSystemsManagement,
      db: Config.Database,
      parallelizationFactor: Int
  ): Resource[IO, Transactor[IO]] = for {
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
    _ <- Resource.eval(
      IO(xa.kernel.setMaximumPoolSize(parallelizationFactor + 2)) // parallel consumers + api polling + settings service
    )
  } yield xa

  private def resolve(ssm: AWSSimpleSystemsManagement, value: Config.Value): IO[String] =
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
