package sbtmoe

import java.io.{File => _, _}
import java.util.regex.{Matcher, Pattern}
import java.util.zip.ZipInputStream

import sbt._

private object XCode {
  def build(projectPath: File, configuration: String, targetSDK: TargetSDK, strms: sbt.Keys.TaskStreams): Unit = {
    val args = Seq.newBuilder[String]

    args += "xcrun"
    args += "xcodebuild"

    args += "-sdk"
    args += targetSDK.xcodeName

    args += "-configuration"
    args += configuration

    args += "-project"
    args += projectPath.absolutePath

    val ret = Process(args.result()).!

    if(ret != 0)
      throw new RuntimeException(s"xcrun xcodebuild failed with return code $ret")
  }

  def buildIPA(projectPath: File, ipaPath: File, strms: sbt.Keys.TaskStreams): Unit = {
    val args = Seq.newBuilder[String]

    args += "xcrun"

    args += "-sdk"
    args += "iphoneos"

    args += "PackageApplication"

    args += "-v"
    args += projectPath.absolutePath

    args += "-o"
    args += ipaPath.absolutePath

    val ret = Process(args.result()).!

    if(ret != 0)
      throw new RuntimeException(s"xcrun PackageApplication failed with return code $ret")
  }

  def generateProject(
    projectPath: File,
    projectName: String,
    orgName: String,
    orgIdentifier: String,
    mainClass: String,
    strms: sbt.Keys.TaskStreams
  ): Unit = {
    IO.createDirectory(projectPath)

    val archiveStream = Option(this.getClass.getResourceAsStream("single-view-ios.zip"))
      .getOrElse(throw new RuntimeException("Missing template in package"))
    try {
      val zip = new ZipInputStream(archiveStream)

      var entry = zip.getNextEntry
      while(entry != null) {
        val entryNameBuilder = new StringBuilder(entry.getName)
        replace(entryNameBuilder, projectName, orgName, orgIdentifier, mainClass)

        if(entry.isDirectory) {
          IO.createDirectory(projectPath / entryNameBuilder.toString())
        } else {
          val outputPath = projectPath / entryNameBuilder.toString()
          IO.createDirectory(outputPath.getParentFile)

          val outputStream = new FileOutputStream(outputPath)
          try {
            val outputWriter = new BufferedWriter(new OutputStreamWriter(outputStream))

            val lineBuffer = new StringBuilder
            val inputReader = new BufferedReader(new InputStreamReader(zip))
            var line = inputReader.readLine()
            while(line != null) {
              lineBuffer.clear()
              lineBuffer.append(line)
              replace(lineBuffer, projectName, orgName, orgIdentifier, mainClass)
              outputWriter.write(lineBuffer.toString())
              outputWriter.newLine()

              line = inputReader.readLine()
            }

            outputWriter.flush()
          } finally {
            outputStream.close()
          }
        }

        entry = zip.getNextEntry
      }
    } finally {
      archiveStream.close()
    }
  }

  private def replace(
    buffer: StringBuilder,
    projectName: String,
    orgName: String,
    orgIdentifier: String,
    mainClass: String
  ): Unit = {
    replaceAll(buffer, projectNameMatcher, projectName)
    replaceAll(buffer, orgNameMatcher, orgName)
    replaceAll(buffer, orgIdentMatcher, orgIdentifier)
    replaceAll(buffer, mainClassMatcher, mainClass)
  }

  private def replaceAll(buffer: StringBuilder, matcher: Matcher, replacement: String): Unit = {
    matcher.reset(buffer)

    var i = 0
    while(matcher.find(i)) {
      buffer.replace(matcher.start(), matcher.end(), replacement)
      i = matcher.start() + replacement.length
    }
  }

  private val projectNamePattern = Pattern.compile("__PRODUCT_NAME__")
  private val projectNameMatcher = projectNamePattern.matcher("")
  private val orgNamePattern = Pattern.compile("__ORG_NAME__")
  private val orgNameMatcher = orgNamePattern.matcher("")
  private val orgIdentPattern = Pattern.compile("__ORG_IDENT__")
  private val orgIdentMatcher = orgIdentPattern.matcher("")
  private val mainClassPattern = Pattern.compile("__MAIN_CLASS__")
  private val mainClassMatcher = mainClassPattern.matcher("")
}
