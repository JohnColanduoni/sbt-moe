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

      XCode
        .generateProject(
          projectPath,
          xcodeProjectName.value,
          xcodeOrganizationName.value,
          xcodeOrganizationIdentifier.value,
          (mainClass in Compile).value.getOrElse(throw new RuntimeException("A main class must be specified")),
          streams.value
        )

      projectPath
    },
    proguardInputs := {
      val dependencies = (dependencyClasspath in Compile).value
      val projectJar = (packageBin in Compile).value
      dependencies.map {_.data}.filter { _.getName != "moe-ios.jar" } :+ projectJar
    },
    proguardLibraries := {
      val sdkPath = (moeSdkPath in IOS).value

      Seq(sdkPath / "sdk" / "moe-core.jar", sdkPath / "sdk" / "moe-ios.jar")
    },
    proguard := {
      val strms = streams.value

      val sdkPath = (moeSdkPath in IOS).value
      val proguardJar = sdkPath / "tools" / "proguard.jar"
      val sdkProguardConfig = sdkPath / "tools" / "proguard.cfg"

      val inputs = proguardInputs.value
      val libraries = proguardLibraries.value
      val output = moeOutputPath.value / "build" / "proguarded.jar"

      val cached = FileFunction.cached(
        strms.cacheDirectory / "moe-proguard",
        inStyle = FilesInfo.lastModified,
        outStyle = FilesInfo.exists) { _ =>
        strms.log.info(s"Using ProGuard to process ${inputs.map {_.getName}.mkString(", ")}")
        Proguard.process(proguardJar, inputs, libraries, output, Seq(sdkProguardConfig), streams.value)
        Set(output)
      }

      val fileDependencies = Set.newBuilder[File]
      fileDependencies += proguardJar
      fileDependencies += sdkProguardConfig
      fileDependencies ++= inputs
      fileDependencies ++= libraries
      cached(fileDependencies.result())

      output
    },
    retrolambda := {
      val strms = streams.value

      val sdkPath = (moeSdkPath in IOS).value
      val retrolambdaJar = sdkPath / "tools" / "retrolambda.jar"

      val moeOutPath = moeOutputPath.value
      val retrolambdaInputDir = moeOutPath / "build" / "retrolambda" / "input"
      val retrolambdaOutputDir = moeOutPath / "build" / "retrolambda" / "output"
      val inputs = Seq(proguard.value)
      val classpath = proguardLibraries.value

      val cached = FileFunction.cached(
        strms.cacheDirectory / "moe-retrolambda",
        inStyle = FilesInfo.lastModified,
        outStyle = FilesInfo.exists) { _ =>

        IO.delete(retrolambdaInputDir)
        IO.delete(retrolambdaOutputDir)

        strms.log.info(s"Extracting proguarded class files to $retrolambdaInputDir")
        JarProcessing.aggregateClassFiles(inputs, retrolambdaInputDir, strms)

        strms.log.info(s"Running retrolambda on class files")
        Retrolambda.convert(
          retrolambdaJar,
          retrolambdaOutputDir,
          retrolambdaInputDir,
          retrolambdaInputDir +: classpath,
          defaultMethods = true,
          natjSupport = true
        )

        retrolambdaOutputDir.***.get.toSet
      }

      val fileDependencies = Set.newBuilder[File]
      fileDependencies += retrolambdaJar
      fileDependencies ++= JarProcessing.classpathToFileList(inputs)
      fileDependencies ++= JarProcessing.classpathToFileList(classpath)
      cached(fileDependencies.result())

      retrolambdaOutputDir
    },
    dexInputs := {
      Seq(retrolambda.value)
    },
    dex := {
      val strms = streams.value
      val inputs = dexInputs.value
      val dexOutputPath = moeOutputPath.value / "build" / "dex.jar"
      val dexJar = (moeSdkPath in IOS).value / "tools" / "dx.jar"

      val cached = FileFunction.cached(
        strms.cacheDirectory / "moe-dex",
        inStyle = FilesInfo.lastModified,
        outStyle = FilesInfo.exists) { _ =>
        strms.log.info(s"Dexing the following jars: ${inputs.map {_.getName}.mkString(", ")}")
        IO.createDirectory(dexOutputPath.getParentFile)
        Dex.dex(dexJar, dexOutputPath, inputs, strms, core = true)

        Set(dexOutputPath)
      }

      val fileDependencies = Set.newBuilder[File]
      fileDependencies += dexJar
      fileDependencies ++= JarProcessing.classpathToFileList(inputs)
      cached(fileDependencies.result())

      dexOutputPath
    }
  )

  val moeBuildTasks = Seq(
    postDexFiles := {
      val sdkPath = (moeSdkPath in IOS).value
      Seq(sdkPath / "sdk" / "moe-core.dex", sdkPath / "sdk" / "moe-ios-retro-dex.jar", (dex in IOS).value)
    },
    postDexResourceFiles := {
      val sdkPath = (moeSdkPath in IOS).value
      (sdkPath / "sdk" / "moe-core.jar") :: (sdkPath / "sdk" / "moe-ios.jar") :: (proguard in IOS).value :: Nil
    },
    postDexClasspath := {
      val sdkPath = (moeSdkPath in IOS).value
      (sdkPath / "sdk" / "moe-core.jar") +: (sdkPath / "sdk" / "moe-ios.jar") +: (dexInputs in IOS).value
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

        val cached = FileFunction.cached(
          strms.cacheDirectory / s"moe-dex2oat-${arch.xcodeName}-debugInfo_$debugInfo-optimize_$optimize",
          inStyle = FilesInfo.lastModified,
          outStyle = FilesInfo.exists) { _ =>
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

            Set(oatOutputPath, imageOutputPath)
          }

        val fileDependencies = Set.newBuilder[File]
        fileDependencies += dex2oatExec
        fileDependencies += imageClassesFile
        fileDependencies ++= JarProcessing.classpathToFileList(inputDex)
        cached(fileDependencies.result())

        outputFiles += (arch -> Dex2OATOutput(oatOutputPath, imageOutputPath))
      }

      outputFiles.result()
    },
    // The dex inputs will contain retrolambda output, which does not include resources, so we replace it with the
    // post-proguard jar
    packageResourcesInputs := postDexResourceFiles.value,
    packageResources := {
      val strms = streams.value

      val inputs = packageResourcesInputs.value
      val outputPath = moeOutputPath.value / "build" / "application.jar"
      val configName = configuration.value.name

      val cached = FileFunction.cached(
        strms.cacheDirectory / s"moe-resources-$configName",
        inStyle = FilesInfo.lastModified,
        outStyle = FilesInfo.exists) { _ =>

        strms.log(s"Aggregating Java resources from ${inputs.mkString(",")}")
        JarProcessing.aggregateResources(inputs, outputPath, strms)

        Set(outputPath)
      }

      cached(JarProcessing.classpathToFileList(inputs).toSet)

      outputPath
    },
    startupProviderInputs := postDexClasspath.value,
    startupProvider := {
      val strms = streams.value

      val inputs = startupProviderInputs.value
      val outputPath = moeOutputPath.value / "build" / "preregister.txt"
      val configName = configuration.value.name

      val cached = FileFunction.cached(
        strms.cacheDirectory / s"moe-preregister-$configName",
        inStyle = FilesInfo.lastModified,
        outStyle = FilesInfo.exists) { _ =>

        strms.log(s"Finding classes requiring preregistration from ${inputs.mkString(",")}")
        JarProcessing.preregister(inputs, outputPath)

        Set(outputPath)
      }

      cached(JarProcessing.classpathToFileList(inputs).toSet)

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
      if(!frameworkLinkPath.exists()) {
        Files.createSymbolicLink(frameworkLinkPath.toPath, frameworkSrcPath.toPath)
      }

      XCode.build(projectPath, projectConfig, TargetSDK.IPhoneSim, streams.value)
    }
  )

  case class Dex2OATOutput(oatFile: File, imageFile: File)

}
