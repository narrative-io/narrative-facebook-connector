package io.narrative.connectors.facebook

import cats.effect.IO
import io.narrative.connectors.facebook.Config._
import io.narrative.microframework.config.Stage

final case class Config(
    dbUrlSsm: DatabaseUrlSsm,
    facebookTokenSsm: FacebookTokenSsm,
    stage: Stage
)
object Config {

  def apply(): IO[Config] = for {
    stage <- envOrElse("STAGE", "dev").map(Stage.apply)
    dbUrlSsm = DatabaseUrlSsm(stage)
    facebookTokenSsm <- FacebookTokenSsm()
  } yield Config(
    dbUrlSsm = dbUrlSsm,
    facebookTokenSsm = facebookTokenSsm,
    stage = stage
  )

  final case class DatabaseUrlSsm(value: String) extends AnyVal
  object DatabaseUrlSsm {
    def apply(stage: Stage): DatabaseUrlSsm = stage match {
      case Stage.Dev             => ???
      case Stage.Demo            => ???
      case Stage.Prod            => ???
      case Stage.Custom("local") => ???
    }
  }
  final case class FacebookTokenSsm(value: String) extends AnyVal
  object FacebookTokenSsm {
    def apply(): IO[FacebookTokenSsm] =
      envOrElse("FACEBOOK_APP_TOKEN_SSM", "/prod/delivery/facebook/app_secret").map(FacebookTokenSsm.apply)
  }

  def env(s: String): IO[Option[String]] = IO(Option(System.getenv(s)))

  def env_!(s: String): IO[String] =
    env(s).map(_.getOrElse(throw new RuntimeException(s"required environment variable not set: '${s}'")))

  def envOrElse(s: String, default: => String): IO[String] = env(s).map(_.getOrElse(default))
}
