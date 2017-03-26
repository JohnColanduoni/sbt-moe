import sbt.Keys._

resolvers += Resolver.jcenterRepo

val sharedSettings = Seq(
  organization := "com.hevylight",
  version := "0.1.0",
  bintrayOrganization := Some("hevylight")
)

val packageTemplates = taskKey[Seq[File]]("Packages XCode project templates")

lazy val plugin = (project in file("."))
  .settings(sharedSettings)
  .settings(
    name := "sbt-moe",
    sbtPlugin := true,
    libraryDependencies += "org.ow2.asm" % "asm-all" % "5.0.3",
    exportJars := true,
    packageTemplates in Compile := {
      val resourceManagedPath = resourceManaged.value

      val outputFile = resourceManagedPath / "main/sbtmoe/single-view-ios.zip"
      IO.zip(Path.allSubpaths(file("xcode-templates/single-view-ios")), outputFile)
      Seq(outputFile)
    },
    resourceGenerators in Compile <+= (packageTemplates in Compile)
  )
  // SBT tests
  .settings(
    scriptedSettings,
    sbtTestDirectory <<= baseDirectory (_ / "sbt-test"),
    scriptedLaunchOpts ++= {
      Seq("-Dplugin.version=" + version.value, "-Dsbt.log.noformat=true")
    },
    scriptedBufferLog := false
  )
  .settings(
    bintrayRepository := "sbt-plugins",
    licenses += ("Apache-2.0", url("https://www.apache.org/licenses/LICENSE-2.0")),
    publishMavenStyle := false
  )
