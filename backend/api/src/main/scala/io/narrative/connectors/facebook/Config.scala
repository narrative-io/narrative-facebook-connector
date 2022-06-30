package io.narrative.connectors.facebook

import cats.data.OptionT
import cats.effect.IO
import io.narrative.microframework.config.Stage

final case class Config(
    database: Config.Database,
    facebook: Config.Facebook,
    kms: Config.Kms,
    narrativeApi: Config.NarrativeApi,
    server: Config.Server,
    stage: Stage
)
object Config {

  def apply(): IO[Config] = for {
    stage <- envOrElse("STAGE", "dev").map(Stage.apply)
    database <- Database(stage)
    facebook <- Facebook()
    narrativeApi <- NarrativeApi(stage)
    server <- Server()
    kms <- Kms()
  } yield Config(
    database = database,
    facebook = facebook,
    kms = kms,
    narrativeApi = narrativeApi,
    server = server,
    stage = stage
  )

  final case class Database(
      jdbcUrl: Config.Literal,
      password: Config.Value,
      username: Config.Value
  )
  object Database {
    def apply(stage: Stage): IO[Database] = for {
      username <- valueOrElse(
        "DB_USER",
        "DB_USER_SSM",
        encrypted = false,
        Config.ssmParam(s"/${stage}/connectors/facebook/api/facebookconnector-db/user", encrypted = false)
      )
      password <- valueOrElse(
        "DB_PASSWORD",
        "DB_PASSWORD_SSM",
        encrypted = true,
        Config.ssmParam(s"/${stage}/connectors/facebook/api/facebookconnector-db/password", encrypted = true)
      )
      jdbcUrl <- OptionT(env("DB_URL"))
        .map(Config.Literal)
        .getOrElse(
          Config.Literal(
            s"jdbc:postgresql://facebookconnector-db-${stage}.c4sgf4vjfwdh.us-east-1.rds.amazonaws.com:5432/facebookconnector"
          )
        )
    } yield Database(
      jdbcUrl = jdbcUrl,
      password = password,
      username = username
    )
  }

  final case class Facebook(appId: Config.Value, appSecret: Config.Value)
  object Facebook {
    def apply(): IO[Facebook] = for {
      appId <- valueOrElse(
        "FACEBOOK_APP_ID",
        "FACEBOOK_APP_ID_SSM",
        encrypted = false,
        Config.ssmParam(s"/prod/connectors/facebook/app_id", encrypted = false)
      )
      appSecret <- valueOrElse(
        "FACEBOOK_APP_SECRET",
        "FACEBOOK_APP_SECRET_SSM",
        encrypted = true,
        Config.ssmParam(s"/prod/connectors/facebook/app_secret", encrypted = true)
      )
    } yield Facebook(appId, appSecret)
  }

  final case class Server(port: Config.Literal)
  object Server {
    def apply(): IO[Server] =
      envOrElse("API_PORT", "8080").map(Config.Literal.apply).map(Server.apply)
  }

  final case class Kms(tokenEncryptionKeyId: Config.Value)
  object Kms {
    def apply(): IO[Kms] = for {
      keyId <- value_!("TOKEN_KMS_KEY_ID", "TOKEN_KMS_KEY_ID_SSM", encrypted = false)
    } yield Kms(keyId)
  }

  final case class NarrativeApi(baseUri: Config.Value)
  object NarrativeApi {
    def apply(stage: Stage): IO[NarrativeApi] =
      envOrElse(
        "NARRATIVE_API_BASE_URI",
        stage match {
          case Stage.Prod => "https://api.narrative.io"
          case _          => "https://api-dev.narrative.io"
        }
      ).map(Config.Literal).map(NarrativeApi(_))
  }

  sealed trait Value
  final case class Literal(value: String) extends Value
  final case class SsmParam(value: String, encrypted: Boolean) extends Value

  def literal(value: String): Value = Literal(value)
  def ssmParam(value: String, encrypted: Boolean): Value = SsmParam(value, encrypted)

  def value(literalEnvVar: String, ssmEnvVar: String, encrypted: Boolean): IO[Option[Config.Value]] = {
    val lit = OptionT(env(literalEnvVar)).map(Config.literal)
    val ssm = OptionT(env(ssmEnvVar)).map(Config.ssmParam(_, encrypted))
    lit.orElse(ssm).value
  }
  def value_!(literalEnvVar: String, ssmEnvVar: String, encrypted: Boolean): IO[Config.Value] =
    OptionT(value(literalEnvVar, ssmEnvVar, encrypted)).getOrRaise(
      new RuntimeException(
        s"required configuration value not provided. one of '${literalEnvVar}' or '${ssmEnvVar}' must be set"
      )
    )

  def valueOrElse(
      literalEnvVar: String,
      ssmEnvVar: String,
      encrypted: Boolean,
      default: => Config.Value
  ): IO[Config.Value] =
    OptionT(value(literalEnvVar, ssmEnvVar, encrypted)).getOrElse(default)

  def env(s: String): IO[Option[String]] = IO(Option(System.getenv(s)))

  def env_!(s: String): IO[String] =
    env(s).map(_.getOrElse(throw new RuntimeException(s"required environment variable not set: '${s}'")))

  def envOrElse(s: String, default: => String): IO[String] = env(s).map(_.getOrElse(default))
}
