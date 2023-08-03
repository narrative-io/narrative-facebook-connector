// Added the plugins suffix to the names to try and limit the amount of warnings that sbt spews
// for now they seem (?) to be completely gone
// "Multiple resolvers having different access mechanism configured with same name 'Narrative Releases'"
resolvers ++= Seq(
  "Narrative Releases plugins" at "s3://s3.amazonaws.com/narrative-artifact-releases"
)

addSbtPlugin("io.narrative" % "common-build" % "4.0.0-SNAPSHOT")
addDependencyTreePlugin

addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.11.0")
