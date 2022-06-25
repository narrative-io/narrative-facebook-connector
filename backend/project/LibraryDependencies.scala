import sbt._

object LibraryDependencies {
  object NarrativeBackend {
    val version = "16.1.4"
    val `narrative-common-ssm` = "io.narrative" %% "common-ssm" % version
    val `narrative-common-catsretry` = "io.narrative" %% "common-catsretry" % version
    val `narrative-common-doobie-testkit` = "io.narrative" %% "common-doobie-testkit" % version
    val `narrative-common-testcontainers` = "io.narrative" %% "common-testcontainers" % version
    val `narrative-microframework-config` = "io.narrative" %% "microframework-config" % version
  }

  object NarrativeDB {
    val `shared-db-migrations` = "io.narrative" %% "shared-db-migrations" % "0.1.77"
  }

  object Aws {
    val version = "1.12.170"
    //    val version = "1.11.676"
    val `aws-java-sdk-ssm` = "com.amazonaws" % "aws-java-sdk-ssm" % version
    val `aws-java-sdk-sts` = "com.amazonaws" % "aws-java-sdk-sts" % version
    val `aws-java-sdk-core` = "com.amazonaws" % "aws-java-sdk-core" % version
  }

  object Cats {
    val `cats-core` = "org.typelevel" %% "cats-core" % "2.8.0"
    val `cats-effect` = "org.typelevel" %% "cats-effect" % "2.5.4"
  }

  object CatsRetry {
    val version = "2.1.1"
    val `cats-retry` = "com.github.cb372" %% "cats-retry" % version
  }

  object Circe {
    val version = "0.14.1"
    val `circe-core` = "io.circe" %% "circe-core" % version
    val `circe-generic` = "io.circe" %% "circe-generic" % version
    val `circe-generic-extras` = "io.circe" %% "circe-generic-extras" % version
    val `circe-parser` = "io.circe" %% "circe-parser" % version
    val `circe-literal` = "io.circe" %% "circe-literal" % version
  }

  object CirceFs2 {
    val version = "0.13.0"
    val `circe-fs2` = "io.circe" %% "circe-fs2" % version
  }

  object Doobie {
    val version = "0.9.0"
    val `doobie-core` = "org.tpolecat" %% "doobie-core" % version
    val `doobie-postgres` = "org.tpolecat" %% "doobie-postgres" % version
    val `doobie-hikari` = "org.tpolecat" %% "doobie-hikari" % version
  }

  object Fs2 {
    val version = "2.4.4"
    val `fs2-core` = "co.fs2" %% "fs2-core" % version
    val `fs2-io` = "co.fs2" %% "fs2-io" % version
  }

  object Http4s {
    val version = "0.22.7"
    val `http4s-core` = "org.http4s" %% "http4s-dsl" % version
    val `http4s-dsl` = "org.http4s" %% "http4s-dsl" % version
    val `http4s-circe` = "org.http4s" %% "http4s-circe" % version
    val `http4s-server` = "org.http4s" %% "http4s-server" % version
    val `http4s-blaze-server` = "org.http4s" %% "http4s-blaze-server" % version
    val `http4s-blaze-client` = "org.http4s" %% "http4s-blaze-client" % version
  }

  object Jackson {
    val version = "2.11.2"
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
    val `logstash-logback` = "net.logstash.logback" % "logstash-logback-encoder" % "6.6"
    val `logback-classic` = "ch.qos.logback" % "logback-classic" % "1.2.3"
    val `logback-access` = "ch.qos.logback" % "logback-access" % "1.2.3"
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
