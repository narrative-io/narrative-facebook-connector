import scala.collection.generic.SeqForwarder
import scala.collection.{AbstractSeq, LinearSeq, SeqProxy, SeqViewLike, immutable, mutable}

import LibraryDependencies.{NarrativeBackend, _}

import _root_.io.narrative.build.CommonRepositories._

ThisBuild / resolvers ++= Seq("Narrative Releases" at "s3://s3.amazonaws.com/narrative-artifact-releases")
ThisBuild / publishTo := {
  if (version.value.contains("SNAPSHOT")) Some(NarrativeSnapshots)
  else Some(NarrativeReleases)
}
ThisBuild / exportPipelining := false
ThisBuild / usePipelining := true

name := "narrative-facebook-connector"

val commonSettings = Seq(
  scalaVersion := "2.12.16",
  scalacOptions ++= Seq(
    "-language:postfixOps"
  ),
  addCompilerPlugin("org.typelevel" % "kind-projector" % "0.13.0" cross CrossVersion.full),
  addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1"),
  // Avoid unit test errors like:
  // "com.fasterxml.jackson.databind.JsonMappingException: Scala module 2.9.10 requires Jackson Databind version >= 2.9.0 and < 2.10.0"
  // And allows us to bring in a newer version of Jackson for dependencies that require them (e.g.
  // logback-logstash-encoder) while explicitly overriding the ancient jackson 2.6 dependency in the AWS SDK that it
  // clings to for Java 1.6 support. Can be moved from commonSettings to something more specific if this project
  // ever grows to the point that we have independent dependency graphs that needs different versions of Jackson.
  dependencyOverrides ++= Jackson.overrides
)

lazy val `api` = project
  .enablePlugins(AwsFargateDockerPlugin)
  .enablePlugins(JavaAppPackaging)
  .enablePlugins(DockerPlugin)
  .enablePlugins(EcrPlugin)
  .configs(IntegrationTest)
  .settings(Defaults.itSettings)
  .settings(commonSettings)
  .settings(
    fargateAppPort := 8081,
    fargateMainClass := "io.narrative.s3connector.Server",
    fargateImageName := s"narrative-s3-connector/api"
  )
  .settings(
    libraryDependencies ++= Logging.applicationLoggingDependencies
  )
  .settings(
    libraryDependencies ++= Seq(
      Aws.`aws-java-sdk-ssm`,
      Aws.`aws-java-sdk-sts`,
      Doobie.`doobie-core`,
      Doobie.`doobie-postgres`,
      Http4s.`http4s-core`,
      Http4s.`http4s-dsl`,
      Http4s.`http4s-circe`,
      Http4s.`http4s-server`,
      Http4s.`http4s-blaze-server`,
      NarrativeBackend.`narrative-common-catsretry`,
      ScalaTest.`scalatest` % "test",
      ScalaMock.`scalamock` % "test"
    )
  )
  .dependsOn(`stores`)

lazy val `stores` = project
  .settings(commonSettings)
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
      ScalaTest.`scalatest` % "test"
    ) ++ Logging.libraryLoggingDependencies
  )
