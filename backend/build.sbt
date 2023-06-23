import LibraryDependencies._

import _root_.io.narrative.build.CommonRepositories._

ThisBuild / resolvers ++= Seq("Narrative Releases" at "s3://s3.amazonaws.com/narrative-artifact-releases")
ThisBuild / resolvers ++= Seq("Narrative Snapshots" at "s3://s3.amazonaws.com/narrative-artifact-snapshots")
ThisBuild / publishTo := {
  if (version.value.contains("SNAPSHOT")) Some(NarrativeSnapshots)
  else Some(NarrativeReleases)
}
ThisBuild / exportPipelining := false
ThisBuild / usePipelining := true

name := "narrative-facebook-connector"

val commonSettings = Seq(
  scalacOptions ++= Seq(
    "-language:postfixOps"
  ),
  addCompilerPlugin("org.typelevel" % "kind-projector" % "0.13.2" cross CrossVersion.full),
  addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1"),
  // Avoid unit test errors like:
  // "com.fasterxml.jackson.databind.JsonMappingException: Scala module 2.9.10 requires Jackson Databind version >= 2.9.0 and < 2.10.0"
  // And allows us to bring in a newer version of Jackson for dependencies that require them (e.g.
  // logback-logstash-encoder) while explicitly overriding the ancient jackson 2.6 dependency in the AWS SDK that it
  // clings to for Java 1.6 support. Can be moved from commonSettings to something more specific if this project
  // ever grows to the point that we have independent dependency graphs that needs different versions of Jackson.
  dependencyOverrides ++= Jackson.overrides
)

lazy val extraJavaOpts = Seq(
  "-J-verbose:gc",
  // JVM >= 17
  // java.lang.IllegalAccessError: class org.apache.spark.storage.StorageUtils$ (in unnamed module @0xc4e84f7) cannot access class sun.nio.ch.DirectBuffer (in module java.base) because module java.base does not export sun.nio.ch to unnamed module @0xc4e84f7
  // https://github.com/apache/spark/blob/v3.3.0/launcher/src/main/java/org/apache/spark/launcher/JavaModuleOptions.java
  "-J-XX:+IgnoreUnrecognizedVMOptions",
  "-J--add-opens=java.base/java.lang=ALL-UNNAMED",
  "-J--add-opens=java.base/java.lang.invoke=ALL-UNNAMED",
  "-J--add-opens=java.base/java.lang.reflect=ALL-UNNAMED",
  "-J--add-opens=java.base/java.io=ALL-UNNAMED",
  "-J--add-opens=java.base/java.net=ALL-UNNAMED",
  "-J--add-opens=java.base/java.nio=ALL-UNNAMED",
  "-J--add-opens=java.base/java.util=ALL-UNNAMED",
  "-J--add-opens=java.base/java.util.concurrent=ALL-UNNAMED",
  "-J--add-opens=java.base/java.util.concurrent.atomic=ALL-UNNAMED",
  "-J--add-opens=java.base/sun.nio.ch=ALL-UNNAMED",
  "-J--add-opens=java.base/sun.nio.cs=ALL-UNNAMED",
  "-J--add-opens=java.base/sun.security.action=ALL-UNNAMED",
  "-J--add-opens=java.base/sun.util.calendar=ALL-UNNAMED",
  "-J--add-opens=java.security.jgss/sun.security.krb5=ALL-UNNAMED"
)

lazy val `api` = project
  .enablePlugins(AwsFargateDockerPlugin)
  .enablePlugins(JavaAppPackaging)
  .enablePlugins(DockerPlugin)
  .enablePlugins(EcrPlugin)
  .configs(IntegrationTest)
  .settings(Defaults.itSettings)
  .settings(commonSettings)
  .settings(enableQuickPublish := true)
  .settings(
    fargateAppPort := 8081,
    fargateMainClass := "io.narrative.connectors.facebook.Server",
    fargateImageName := "narrative-facebook-connector/api",
    Universal / javaOptions ++= extraJavaOpts
  )
  .settings(
    libraryDependencies ++= Logging.applicationLoggingDependencies
  )
  .settings(
    libraryDependencies ++= Seq(
      Aws.`aws-java-sdk-kms`,
      Aws.`aws-java-sdk-ssm`,
      Doobie.`doobie-hikari`,
      Http4s.`http4s-core`,
      Http4s.`http4s-dsl`,
      Http4s.`http4s-circe`,
      Http4s.`http4s-server`,
      Http4s.`http4s-blaze-server`,
      NarrativeBackend.`narrative-common-ssm`,
      NarrativeBackend.`narrative-microframework-config`,
      ScalaTest.`scalatest` % "test"
    )
  )
  .dependsOn(`services`)

lazy val `worker` = project
  .enablePlugins(AwsFargateDockerPlugin)
  .enablePlugins(JavaAppPackaging)
  .enablePlugins(DockerPlugin)
  .enablePlugins(EcrPlugin)
  .configs(IntegrationTest)
  .settings(Defaults.itSettings)
  .settings(commonSettings)
  .settings(enableQuickPublish := true)
  .settings(
    fargateAppPort := 8081,
    fargateMainClass := "io.narrative.connectors.facebook.Main",
    fargateImageName := "narrative-facebook-connector/worker",
    Universal / javaOptions ++= extraJavaOpts
  )
  .settings(
    libraryDependencies ++= Logging.applicationLoggingDependencies
  )
  .settings(
    libraryDependencies ++= Seq(
      Aws.`aws-java-sdk-kms`,
      Aws.`aws-java-sdk-ssm`,
      Doobie.`doobie-hikari`,
      Http4s.`http4s-core`,
      Http4s.`http4s-dsl`,
      Http4s.`http4s-circe`,
      NarrativeBackend.`narrative-common-ssm`,
      NarrativeBackend.`narrative-microframework-config`,
      NarrativeConnectorFramework.`connector-framework-core`,
      Circe.`circe-literal` % "test",
      ScalaTest.`scalatest` % "test"
    )
  )
  .dependsOn(`services`)

lazy val `services` = project
  .settings(commonSettings)
  .settings(
    // no need to publish, no consumers of this library
    publish := {}
  )
  .settings(
    libraryDependencies ++= Seq(
      Aws.`aws-java-sdk-kms`,
      Facebook.`facebook-java-business-sdk`,
      Http4s.`http4s-blaze-client`,
      Http4s.`http4s-circe`,
      NarrativeBackend.`narrative-common-catsretry`,
      NarrativeBackend.`narrative-microframework-config`,
      Circe.`circe-literal` % "test",
      ScalaTest.`scalatest` % "test"
    )
  )
  .dependsOn(`stores`)

lazy val `stores` = project
  .settings(commonSettings)
  .settings(
    // no need to publish, no consumers of this library
    publish := {}
  )
  .settings(
    libraryDependencies ++= Seq(
      Cats.`cats-effect`,
      Cats.`cats-core`,
      Doobie.`doobie-core`,
      Doobie.`doobie-postgres`,
      Circe.`circe-core`,
      Circe.`circe-generic`,
      Circe.`circe-generic-extras`,
      Circe.`circe-parser`,
      NarrativeConnectorFramework.`connector-framework-core`,
      ScalaTest.`scalatest` % "test"
    ) ++ Logging.libraryLoggingDependencies
  )
