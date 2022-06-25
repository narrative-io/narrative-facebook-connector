import sbt.Keys._
import sbt._
import sbtrelease.ReleasePlugin.autoImport._
import sbtassembly.AssemblyPlugin.autoImport._
import sbtassembly.MergeStrategy

object ReleaseSettings {
  def releaseIndividually(p: ProjectReference) = Seq(
    releaseUseGlobalVersion := false,
    releaseVersionFile := file((p / baseDirectory).value + "/version.sbt"),
    releaseTagName := {
      val versionInThisBuild = (ThisBuild / version).value
      val versionValue = (p / version).value
      s"${(p / name).value}-v${if ((p / releaseUseGlobalVersion).value) versionInThisBuild
      else versionValue}"
    }
  )
}
