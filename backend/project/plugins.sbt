import scala.jdk.CollectionConverters.*

import com.amazonaws.auth._
import com.amazonaws.auth.profile._
import com.amazonaws.auth.profile.internal._
import com.amazonaws.profile.path._

// Relies on several conventions we use
// 1. SSO profiles map to profiles whose name start with t- (for Team). e.g. t-engineering-704349335716.
//    These profiles use the credential_process directive. We use ProfileProcessCredentialsProvider to load these.
// 2. The roles that are assumed using awsume are called either admin, sudo, or build.
//    We do not currently use "build" but hardcoding this one in case we want to restrict permissions in the future.
//    We use ProfileCredentialsProvider to load these.
s3CredentialsProvider := { (bucket: String) =>
  val awsumeBuildProfileNames = Set("build").map("autoawsume-" + _)
  val awsumeAdminProfileNames = Set("admin", "sudo").map("autoawsume-" + _)

  val awsumeBuildProfiles = {

    val credentials = Option(
      AwsProfileFileLocationProvider.DEFAULT_CREDENTIALS_LOCATION_PROVIDER.getLocation
    ).filter(_.exists())
      .map(
        new ProfilesConfigFile(
          _
        ).getAllBasicProfiles.asScala
      )
      .getOrElse(Map.empty[String, BasicProfile])

    credentials.filter(c => awsumeBuildProfileNames.contains(c._1))
  }

  val teamProfiles = {
    val profiles = Option(
      AwsProfileFileLocationProvider.DEFAULT_CONFIG_LOCATION_PROVIDER.getLocation
    ).filter(_.exists())
      .map(
        new ProfilesConfigFile(
          _
        ).getAllBasicProfiles.asScala
      )
      .getOrElse(Map.empty[String, BasicProfile])

    profiles.filter(_._1.startsWith("profile t-"))
  }
  val awsumeAdminProfiles = {
    val credentials = Option(
      AwsProfileFileLocationProvider.DEFAULT_CREDENTIALS_LOCATION_PROVIDER.getLocation
    ).filter(_.exists())
      .map(
        new ProfilesConfigFile(
          _
        ).getAllBasicProfiles.asScala
      )
      .getOrElse(Map.empty[String, BasicProfile])

    credentials.filter(c => awsumeAdminProfileNames.contains(c._1))
  }

  val awsumeBuildCredentialProviders =
    awsumeBuildProfiles.keys.map(new ProfileCredentialsProvider(_)).toList
  val profileProcessCredentialProviders =
    teamProfiles.values.map(new ProfileProcessCredentialsProvider(_)).toList
  val awsumeAdminCredentialProviders =
    awsumeAdminProfiles.keys.map(new ProfileCredentialsProvider(_)).toList
  val defaultCredentialProviders =
    List(DefaultAWSCredentialsProviderChain.getInstance())

  sLog.value.info(
    s"awsumeBuildProfiles = ${awsumeBuildProfiles.map(_._1).mkString(",")}. " +
      s"teamProfiles = ${teamProfiles.map(_._1).mkString(",")}. " +
      s"awsumeAdminProfiles = ${awsumeAdminProfiles.map(_._1).mkString(",")}, "
  )
  new AWSCredentialsProviderChain(
    (awsumeBuildCredentialProviders ++ profileProcessCredentialProviders ++ awsumeAdminCredentialProviders ++ defaultCredentialProviders): _*
  )
}

// Added the plugins suffix to the names to try and limit the amount of warnings that sbt spews
// for now they seem (?) to be completely gone
// "Multiple resolvers having different access mechanism configured with same name 'Narrative Releases'"
resolvers ++= Seq(
  "Narrative Releases plugins" at "s3://s3.amazonaws.com/narrative-artifact-releases"
)

addSbtPlugin("io.narrative" % "common-build" % "4.0.7")
addDependencyTreePlugin

addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.12.0")
addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % "0.12.1")
