import sbt._

object LibraryDependencies {

  object NarrativeBackend {
    val version = "13.37.2"

    val `narrative-common-s3` =
      "io.narrative" %% "common-s3" % version

    val `narrative-common-catsretry` =
      "io.narrative" %% "common-catsretry" % version

    val `narrative-common-doobie-testkit` =
      "io.narrative" %% "common-doobie-testkit" % version

    val `narrative-common-testcontainers` =
      "io.narrative" %% "common-testcontainers" % version

  }

  object NarrativeDB {
    val `shared-db-migrations` = "io.narrative" %% "shared-db-migrations" % "0.1.77"
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

  object Http4s {
    val version = "0.22.7"
    val `http4s-core` = "org.http4s" %% "http4s-dsl" % version
    val `http4s-dsl` = "org.http4s" %% "http4s-dsl" % version
    val `http4s-circe` = "org.http4s" %% "http4s-circe" % version
    val `http4s-server` = "org.http4s" %% "http4s-server" % version
    val `http4s-blaze-server` = "org.http4s" %% "http4s-blaze-server" % version
    val `http4s-blaze-client` = "org.http4s" %% "http4s-blaze-client" % version
  }

  object Doobie {
    val version = "0.9.0"
    val `doobie-core` = "org.tpolecat" %% "doobie-core" % version
    val `doobie-postgres` = "org.tpolecat" %% "doobie-postgres" % version
    val `doobie-hikari` = "org.tpolecat" %% "doobie-hikari" % version
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

  object ScalaTest {
    val version = "3.2.0"
    val `scalatest` = "org.scalatest" %% "scalatest" % version
  }

  object ScalaMock {
    val version = "5.1.0"
    val `scalamock` = "org.scalamock" %% "scalamock" % version

  }

  object Cats {
    val `cats-core` = "org.typelevel" %% "cats-core" % "2.6.1"
    val `cats-effect` = "org.typelevel" %% "cats-effect" % "2.5.4"
  }

  object CatsRetry {
    val version = "2.1.1"
    val `cats-retry` = "com.github.cb372" %% "cats-retry" % version
  }

  object Fs2 {
    val version = "2.4.4"
    val `fs2-core` = "co.fs2" %% "fs2-core" % version
    val `fs2-io` = "co.fs2" %% "fs2-io" % version
  }

  object Aws {
    val version = "1.11.977"
//    val version = "1.11.676"
    val `aws-java-sdk-ssm` = "com.amazonaws" % "aws-java-sdk-ssm" % version
    val `aws-java-sdk-sts` = "com.amazonaws" % "aws-java-sdk-sts" % version
    val `aws-s3` = "com.amazonaws" % "aws-java-sdk-s3" % version
    val `aws-lambda-java-core` = "com.amazonaws" % "aws-lambda-java-core" % "1.2.1"
    val `aws-java-sdk-dynamodb` = "com.amazonaws" % "aws-java-sdk-dynamodb" % version
    val `aws-java-sdk-core` = "com.amazonaws" % "aws-java-sdk-core" % version
  }

  object AwsV2 {
    val version = "2.17.89"
    val `sqs` = "software.amazon.awssdk" % "sqs" % version
  }

  object Parquet4s {
    val version = "1.9.4"
    val `parquet4s-fs2` = "com.github.mjakubowski84" %% "parquet4s-fs2" % version
  }

  object Spark {
    val `hadoop-version` = "3.2.1"
    val `hadoop-client` = withCommonHadoopExclusions("org.apache.hadoop" % "hadoop-client" % `hadoop-version`)
    val `hadoop-common` = withCommonHadoopExclusions("org.apache.hadoop" % "hadoop-common" % `hadoop-version`)
    val `hadoop-aws` = withCommonHadoopExclusions("org.apache.hadoop" % "hadoop-aws" % `hadoop-version`)
    val `hadoop-auth` = withCommonHadoopExclusions("org.apache.hadoop" % "hadoop-auth" % `hadoop-version`)
    val `hadoop-mapreduce` = withCommonHadoopExclusions("org.apache.hadoop" % "hadoop-mapreduce" % `hadoop-version`)
    val `hadoop-mapreduce-client-core` = withCommonHadoopExclusions(
      "org.apache.hadoop" % "hadoop-mapreduce-client-core" % `hadoop-version`
    )

    val `netty-version` = "4.1.50.Final"
    val `netty-all` = "io.netty" % "netty-all" % `netty-version`

    def withCommonHadoopExclusions(moduleId: ModuleID): ModuleID = {
      val exclusions = Seq(
        "log4j" -> "log4j",
        "org.slf4j" -> "slf4j-log4j12",
        "org.slf4j" -> "slf4j-simple",
        "com.google.guava" -> "guava",
        "com.twitter" -> "algebird-core",
        "javax.servlet" -> "servlet-api",
        "com.amazonaws" -> "aws-java-sdk",
        "com.amazonaws" -> "aws-java-sdk-bundle"
      )

      val orgExclusions = Seq("org.apache.hadoop", "io.netty")

      exclusions.foldLeft(moduleId) { case (dependency, (group, artifact)) =>
        dependency
          .exclude(group, artifact)
          .excludeAll(orgExclusions.map(ExclusionRule(_)): _*)
      }
    }
  }

  object Squants {
    val version = "1.6.0"
    val `squants` = "org.typelevel" %% "squants" % version
  }

}
