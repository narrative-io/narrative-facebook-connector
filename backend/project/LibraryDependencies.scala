import sbt._

object LibraryDependencies {
  object NarrativeBackend {
    val version = "21.1.1"
    val `narrative-common-ssm` = "io.narrative" %% "common-ssm" % version
    val `narrative-common-catsretry` = "io.narrative" %% "common-catsretry" % version
    val `narrative-common-doobie-testkit` = "io.narrative" %% "common-doobie-testkit" % version
    val `narrative-common-testcontainers` = "io.narrative" %% "common-testcontainers" % version
    val `narrative-microframework-config` = "io.narrative" %% "microframework-config" % version
  }

  object NarrativeDB {
    val `shared-db-migrations` = "io.narrative" %% "shared-db-migrations" % "0.2.0"
  }

  object NarrativeConnectorFramework {
    val version = "0.2.4"

    val `connector-framework-core` =
      "io.narrative" %% "connector-framework-core" % version
  }

  object Aws {
    val version = "1.12.656"
    val `aws-java-sdk-core` = "com.amazonaws" % "aws-java-sdk-core" % version
    val `aws-java-sdk-kms` = "com.amazonaws" % "aws-java-sdk-kms" % version
    val `aws-java-sdk-ssm` = "com.amazonaws" % "aws-java-sdk-ssm" % version
    val `aws-java-sdk-cloudwatch` = "com.amazonaws" % "aws-java-sdk-cloudwatch" % version
  }

  object Cats {
    val `cats-core` = "org.typelevel" %% "cats-core" % "2.9.0"
    val `cats-effect` = "org.typelevel" %% "cats-effect" % "3.5.1"
  }

  object CatsRetry {
    val version = "3.1.0"
    val `cats-retry` = "com.github.cb372" %% "cats-retry" % version
  }

  object Circe {
    val version = "0.14.5"
    val `circe-core` = "io.circe" %% "circe-core" % version
    val `circe-generic` = "io.circe" %% "circe-generic" % version
    val `circe-generic-extras` = "io.circe" %% "circe-generic-extras" % "0.14.3"
    val `circe-literal` = "io.circe" %% "circe-literal" % version
    val `circe-parser` = "io.circe" %% "circe-parser" % version
  }

  object CirceFs2 {
    // TODO: migrate to https://fs2-data.gnieh.org/documentation/json/libraries/#circe
    val version = "0.14.1"
    val `circe-fs2` = "io.circe" %% "circe-fs2" % version
  }

  object Doobie {
    val version = "1.0.0-RC4"
    val `doobie-core` = "org.tpolecat" %% "doobie-core" % version
    val `doobie-postgres` = "org.tpolecat" %% "doobie-postgres" % version
    val `doobie-hikari` = "org.tpolecat" %% "doobie-hikari" % version
  }

  object Facebook {
    val version = "17.0.0"
    val `facebook-java-business-sdk` = "com.facebook.business.sdk" % "facebook-java-business-sdk" % version
  }

  object Fs2 {
    val version = "3.7.0"
    val `fs2-core` = "co.fs2" %% "fs2-core" % version
    val `fs2-io` = "co.fs2" %% "fs2-io" % version
  }

  object Http4s {
    val version = "0.23.22"
    val `http4s-core` = "org.http4s" %% "http4s-dsl" % version
    val `http4s-dsl` = "org.http4s" %% "http4s-dsl" % version
    val `http4s-circe` = "org.http4s" %% "http4s-circe" % version
    val `http4s-server` = "org.http4s" %% "http4s-server" % version
    val `http4s-ember-server` = "org.http4s" %% "http4s-ember-server" % version
    val `http4s-ember-client` = "org.http4s" %% "http4s-ember-client" % version
  }

  object Jackson {
    val version = "2.15.2"
    def overrides = Seq(
      "com.fasterxml.jackson.core" % "jackson-databind" % version,
      "com.fasterxml.jackson.core" % "jackson-annotations" % version,
      "com.fasterxml.jackson.core" % "jackson-core" % version,
      "com.fasterxml.jackson.module" % "jackson-module-jaxb-annotations" % version,
      "com.fasterxml.jackson.module" %% "jackson-module-scala" % version,
      "com.fasterxml.jackson.module" % "jackson-module-paranamer" % version,
      "com.fasterxml.jackson.dataformat" % "jackson-dataformat-cbor" % version
    )
  }

  object Logging {
    val `janino` = "org.codehaus.janino" % "janino" % "2.7.8"
    // logstash-logback is the Datadog-recommended library for defining a JSON log formatter.
    // Has runtime deps on logback-access, jackson 2.10+, and slf4j-api.
    val `logstash-logback` = "net.logstash.logback" % "logstash-logback-encoder" % "7.4"
    val `logback-classic` = "ch.qos.logback" % "logback-classic" % "1.4.11"
    val `logback-access` = "ch.qos.logback" % "logback-access" % "1.4.11"
    val `scala-logging` = "com.typesafe.scala-logging" %% "scala-logging" % "3.9.2"
    val `log4j-over-slf4j` = "org.slf4j" % "log4j-over-slf4j" % "1.7.30"
    val `slf4j-api` = "org.slf4j" % "slf4j-api" % "1.7.30"
    val `jcl-over-slf4j` = "org.slf4j" % "jcl-over-slf4j" % "1.7.30"

    val abstractLoggingDependencies = List(
      Logging.`slf4j-api`,
      Logging.`scala-logging`,
      Logging.`jcl-over-slf4j`,
      Logging.`log4j-over-slf4j`
    )
    val concreteLoggingDependencies = List(
      Logging.`janino`,
      Logging.`logstash-logback`,
      Logging.`logback-classic`,
      Logging.`logback-access`
    )
    val applicationLoggingDependencies = abstractLoggingDependencies ++ concreteLoggingDependencies
    val libraryLoggingDependencies = abstractLoggingDependencies ++ (concreteLoggingDependencies.map(_ % Test))
  }

  object ScalaTest {
    val version = "3.2.0"
    val `scalatest` = "org.scalatest" %% "scalatest" % version
  }

  object ScalaMock {
    val version = "5.1.0"
    val `scalamock` = "org.scalamock" %% "scalamock" % version
  }
}
