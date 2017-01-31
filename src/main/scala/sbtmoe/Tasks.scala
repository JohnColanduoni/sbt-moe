package sbtmoe

import java.nio.file.Files

import sbt.Keys._
import sbt._
import sbtmoe.Keys._

import scala.math.Ordering.Implicits._

object Tasks {
  private val MoeSdkPathRegex = "moe-sdk-(\\d+)\\.(\\d+)\\.(\\d+)".r

  val moeGenTasks = Seq(
    moeSdkPath := {
      val homeMoeDir = file(System.getProperty("user.home")) / ".moe"
      if(homeMoeDir.isDirectory) {
        val maxVersion = homeMoeDir.list.collect {
          case MoeSdkPathRegex(a, b, c) => (a.toInt, b.toInt, c.toInt)
        }.reduceOption {_ max _}
          .getOrElse {
            throw new RuntimeException(
              s"Could not location Multi-OS Engine SDK. Please specify it via '${
                moeSdkPath
                  .key.label
              }'"
            )
          }

        homeMoeDir / s"moe-sdk-${maxVersion._1}.${maxVersion._2}.${maxVersion._3}"
      } else {
        throw new RuntimeException(
          s"Could not location Multi-OS Engine SDK. Please specify it via '${
            moeSdkPath.key
              .label
          }'"
        )
      }
    },

    xcodeGenProject := {
      val projectPath = xcodeProjectDir.value

      val mainClass = organization.value + ".Main"

      XCode
        .generateProject(
          projectPath,
          xcodeProjectName.value,
          xcodeOrganizationName.value,
          xcodeOrganizationIdentifier.value,
          mainClass,
          streams.value
        )

      projectPath
    }
  )

  val moeBuildTasks = Seq(
    dexInputs := {
      val dependencies = (dependencyClasspath in Compile).value
      val projectJar = (packageBin in Compile).value
      dependencies.map {_.data} :+ projectJar
    },
    dex := {
      val strms = streams.value
      val inputs = dexInputs.value
      val dexOutputPath = moeOutputPath.value / "build" / "dex.jar"
      val dexJar = (moeSdkPath in IOS).value / "tools" / "dx.jar"

      IO.createDirectory(dexOutputPath.getParentFile)

      Dex.dex(dexJar, dexOutputPath, inputs, strms, core = true)

      dexOutputPath
    },
    postDexFiles := {
      val sdkPath = (moeSdkPath in IOS).value
      Seq(dex.value, sdkPath / "sdk" / "moe-core.dex", sdkPath / "sdk" / "moe-ios.dex")
    },
    postDexJarFiles := {
      val sdkPath = (moeSdkPath in IOS).value
      (sdkPath / "sdk" / "moe-ios.jar") +:  (sdkPath / "sdk" / "moe-core.jar") +: dexInputs.value
    },
    dex2oat := {
      val strms = streams.value
      val sdkPath = (moeSdkPath in IOS).value
      val dex2oatExec = sdkPath / "tools" / "dex2oat"

      val inputDex = postDexFiles.value
      val imageClassesFile = sdkPath / "tools" / "preloaded-classes"

      val archs = oatArchitectures.value
      val configuration = xcodeProjectConfiguration.value
      val imageBase = oatImageBase.value
      val debugInfo = oatDebugInfo.value
      val optimize = oatOptimize.value

      val outputFiles = Map.newBuilder[InstructionSet, Dex2OATOutput]

      archs.foreach { arch =>
        val oatOutputPath = moeOutputPath.value / "build" / arch.targetSdk.xcodeName / arch.xcodeName / configuration / "application.oat"
        val imageOutputPath = moeOutputPath.value / "build" / arch.targetSdk.xcodeName / arch.xcodeName / configuration / "image.art"

        IO.createDirectory(oatOutputPath.getParentFile)
        IO.createDirectory(imageOutputPath.getParentFile)

        Dex2OAT
          .compile(
            dex2oatExec,
            inputDex,
            oatOutputPath,
            imageOutputPath,
            imageClassesFile,
            arch,
            debugInfo = debugInfo,
            optimize = optimize,
            Some(imageBase(arch)),
            strms
          )

        outputFiles += (arch -> Dex2OATOutput(oatOutputPath, imageOutputPath))
      }

      outputFiles.result()
    },
    packageResourcesInputs := postDexJarFiles.value,
    packageResources := {
      val inputs = packageResourcesInputs.value
      val outputPath = moeOutputPath.value / "build" / "application.jar"

      JarProcessing.aggregateResources(inputs, outputPath, streams.value)

      outputPath
    },
    startupProviderInputs := postDexJarFiles.value,
    startupProvider := {
      val inputs = startupProviderInputs.value
      val outputPath = moeOutputPath.value / "build" / "preregister.txt"

      JarProcessing.preregister(inputs, outputPath)

      outputPath
    },
    appBundle := {
      dex2oat.value
      startupProvider.value
      packageResources.value

      val projectPath = xcodeProjectPath.value
      val target = targetSDK.value
      val projectConfig = xcodeProjectConfiguration.value
      val frameworkLinkPath = moeOutputPath.value / s"build/${target.xcodeName}/MOE.framework"
      val frameworkSrcPath = (moeSdkPath in IOS).value / s"sdk/${target.xcodeName}/MOE.framework"
      IO.createDirectory(frameworkLinkPath.getParentFile)
      Files.createSymbolicLink(frameworkLinkPath.toPath, frameworkSrcPath.toPath)

      XCode.build(projectPath, projectConfig, TargetSDK.IPhoneSim, streams.value)
    }
  )

  case class Dex2OATOutput(oatFile: File, imageFile: File)

}
