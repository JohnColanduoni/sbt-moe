package sbtmoe

import sbt._
import sbt.Keys._

object Keys {
  val IOS = config("ios")
  val IOSDeviceDebug = config("iosDevDebug")
  val IOSDeviceRelease = config("iosDevRelease")
  val IOSSimDebug = config("iosSimDebug")
  val IOSSimRelease = config("iosSimRelease")

  val moeSdkPath = taskKey[File]("Path to Multi-OS Engine SDK")
  val moeOutputPath = settingKey[File]("Path to store intermediate Multi-OS Engine build files")

  // XCode Project Keys
  val xcodeProjectName = settingKey[String]("Name of application in XCode project")
  val xcodeOrganizationName = settingKey[String]("Organization in XCode project")
  val xcodeOrganizationIdentifier = settingKey[String]("Organization Identifier in XCode project")
  val xcodeProjectDir = settingKey[File]("Path to XCode project directory")
  val xcodeProjectPath = settingKey[File]("Path to XCode project")
  val xcodeGenProject = taskKey[File]("Generates an initial XCode project")

  // Proguard Keys
  val proguardInputs = TaskKey[Seq[File]]("moeProguardInputs", "Input jars to ProGuard")
  val proguardLibraries = TaskKey[Seq[File]]("moeProguardLibraries", "Library jars to pass to ProGuard")
  val proguardOptions = SettingKey[Seq[String]]("moeProguardOptions", "Extra options to pass to ProGuard")
  val proguard = TaskKey[File]("moeProguard", "Execute ProGuard on project classes")

  // Retrolambda keys
  val retrolambda = TaskKey[File]("moeRetrolambda", "Execute retrolambda")

  // Dex keys
  val dexInputs = TaskKey[Seq[File]]("moeDexInputs", "Input classes to be converted to DEX")
  val dex = TaskKey[File]("moeDex", "Convert project classes to DEX format")
  val postDexFiles = TaskKey[Seq[File]]("moePostDexFiles", "Dex files to be passed to post-dex stages when building for iOS")
  val postDexResourceFiles = TaskKey[Seq[File]]("moePostDexResourceFiles", "JAR files to be passed to post-dex resource stages when building for iOS")
  val postDexClasspath = TaskKey[Seq[File]]("moePostDexClasspath", "JAR files to be passed to post-dex class stages when building for iOS")

  // Dex2OAT keys
  val dex2oat = taskKey[Map[sbtmoe.InstructionSet, Tasks.Dex2OATOutput]]("Performs AOT compilation for Android Runtime (Debug configuration)")
  val oatOptimize = settingKey[Boolean]("Optimize dex2oat generated code")
  val oatDebugInfo = settingKey[Boolean]("Include debugging information in dex2oat generated code")
  val oatImageBase = settingKey[Map[sbtmoe.InstructionSet, Long]]("Indicates the image base to use for each instruction set")
  val oatArchitectures = settingKey[Set[sbtmoe.InstructionSet]]("Instruction sets to compile native code for")

  // StartupProvider keys
  val startupProvider = taskKey[File]("Identifies classes that require pre-registration")
  val startupProviderInputs = taskKey[Seq[File]]("Inputs to scan for pre-registration requirements")

  // Resource packager keys
  val packageResources = taskKey[File]("Packages Java resources for application")
  val packageResourcesInputs = taskKey[Seq[File]]("Inputs to copy Java resources from")

  val xcodeSetup = taskKey[Unit]("prepares for an Xcode build")

  // Native build keys
  val xcodeProjectConfiguration = settingKey[String]("XCode project configuration (e.g. Debug, Release)")
  val moeTargetSDK = settingKey[TargetSDK]("Platform SDK")
  val appBundle = taskKey[Unit]("Builds native application bundle")

  type InstructionSet = sbtmoe.InstructionSet
  val InstructionSet = sbtmoe.InstructionSet

  val moeLibraries = Seq(
    (unmanagedJars in Compile) ++= {
      Seq((moeSdkPath in IOS).value / "sdk" / "moe-ios.jar").classpath
    }
  )

  private val debugSettings = Seq(
    xcodeProjectConfiguration := "Debug",
    oatOptimize := false,
    oatDebugInfo := true
  )

  private val releaseSettings = Seq(
    xcodeProjectConfiguration := "Release",
    oatOptimize := true,
    oatDebugInfo := false
  )

  private val deviceSettings = Seq(
    moeTargetSDK := TargetSDK.IPhoneOS,
    oatArchitectures := Set(InstructionSet.ARM, InstructionSet.ARM64)
  )

  private val simulatorSettings = Seq(
    moeTargetSDK := TargetSDK.IPhoneSim,
    oatArchitectures := Set(InstructionSet.X86, InstructionSet.X86_64)
  )

  private[sbtmoe] val defaultSettings = Seq(
    moeOutputPath := target.value / "moe",

    xcodeProjectName := name.value,
    xcodeOrganizationName := organizationName.value,
    xcodeOrganizationIdentifier := organization.value,
    xcodeProjectDir := baseDirectory.value / "xcode",
    xcodeProjectPath := xcodeProjectDir.value / (xcodeProjectName.value + ".xcodeproj"),

    proguardOptions := Nil,

    oatImageBase := Map(
      InstructionSet.ARM -> 0x10000000L,
      InstructionSet.ARM64 -> 0x10000000L,
      InstructionSet.X86 -> 0x40000000L,
      InstructionSet.X86_64 -> 0x40000000L
    )
  ) ++
    inConfig(IOSSimDebug)(debugSettings) ++ inConfig(IOSDeviceDebug)(debugSettings) ++
    inConfig(IOSSimRelease)(releaseSettings) ++ inConfig(IOSDeviceRelease)(releaseSettings) ++
    inConfig(IOSSimDebug)(simulatorSettings) ++ inConfig(IOSSimRelease)(simulatorSettings) ++
    inConfig(IOSDeviceDebug)(deviceSettings) ++ inConfig(IOSDeviceRelease)(deviceSettings)
}
