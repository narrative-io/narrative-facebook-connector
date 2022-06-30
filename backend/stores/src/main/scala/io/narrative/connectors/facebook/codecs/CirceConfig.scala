package io.narrative.connectors.facebook.codecs

import io.circe.generic.extras.Configuration

object CirceConfig {
  implicit val config: Configuration =
    Configuration.default.copy(
      transformMemberNames = Configuration.snakeCaseTransformation.andThen(_.toLowerCase),
      transformConstructorNames = Configuration.snakeCaseTransformation.andThen(_.toLowerCase)
    )
}
