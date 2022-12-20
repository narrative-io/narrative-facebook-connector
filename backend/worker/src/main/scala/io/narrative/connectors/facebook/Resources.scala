package io.narrative.connectors.facebook

import cats.effect.{Blocker, ContextShift, IO, Resource, Timer}
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
import org.http4s.blaze.client.BlazeClientBuilder

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
      contextShift: ContextShift[IO],
      timer: Timer[IO]
  ): Resource[IO, Resources] =
    for {
      blocker <- Blocker[IO]
      awsCredentials = new DefaultAWSCredentialsProviderChain()
      ssm <- SSMResources.ssmClient(logger.underlying, blocker, awsCredentials)
      xa <- transactor(blocker, ssm, config.database, parallelizationFactor)
      api <- baseAppApiClient(blocker, config, ssm)
      encryption <- encryptionService(awsCredentials, blocker, config, ssm)
      fb <- Resource.eval(facebookClient(blocker, config, ssm))
      sparkSessions <- Resource.eval(SparkSessions.default[IO](blocker))
      sparkSession <- Resource.eval(sparkSessions.getSparkSession)

      parquetTransformer = new ParquetTransformer(sparkSession, blocker)

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
      filesApi = new BackwardsCompatibleFilesApi(blocker, eventsApi, filesApiV1, filesApiV2)

      commandProcessor = new CommandProcessor(commandStore, jobStore, settingsService, connectionsApi, filesApi)
      deliveryProcessor = new DeliveryProcessor(
        filesApi,
        connectionsApi,
        CommandStore(xa),
        settingsStore,
        encryption,
        fb,
        profileStore,
        parquetTransformer,
        blocker
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

  def baseAppApiClient(blocker: Blocker, config: Config, ssm: AWSSimpleSystemsManagement)(implicit
      cs: ContextShift[IO]
  ): Resource[IO, BaseAppApiClient.Ops[IO]] =
    for {
      blazeClient <- BlazeClientBuilder[IO](scala.concurrent.ExecutionContext.Implicits.global).resource
      id <- Resource.eval(resolve(blocker, ssm, config.narrativeApi.clientId)).map(ClientId.apply)
      secret <- Resource.eval(resolve(blocker, ssm, config.narrativeApi.clientSecret)).map(ClientSecret.apply)

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
    id <- resolve(blocker, ssm, config.facebook.appId).map(FacebookApp.Id.apply)
    secret <- resolve(blocker, ssm, config.facebook.appSecret).map(FacebookApp.Secret.apply)
  } yield new FacebookClient(FacebookApp(id, secret), blocker)

  private def transactor(
      blocker: Blocker,
      ssm: AWSSimpleSystemsManagement,
      db: Config.Database,
      parallelizationFactor: Int
  )(implicit
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
    _ <- Resource.eval(
      IO(xa.kernel.setMaximumPoolSize(parallelizationFactor + 2)) // parallel consumers + api polling + settings service
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
