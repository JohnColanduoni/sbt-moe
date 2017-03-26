package sbtmoe

import sbt._

object Retrolambda {
  def convert(
    retrolambdaJar: File,
    outputDir: File,
    inputDir: File,
    classpath: Seq[File],
    defaultMethods: Boolean,
    natjSupport: Boolean
  ): Unit = {
    val jvmArgs = Seq.newBuilder[String]

    jvmArgs += s"-Dretrolambda.inputDir=${inputDir.getAbsolutePath}"
    jvmArgs += s"-Dretrolambda.classpath=${classpath.map(_.getAbsolutePath).mkString(java.io.File.pathSeparator)}"
    jvmArgs += s"-Dretrolambda.defaultMethods=$defaultMethods"
    jvmArgs += s"-Dretrolambda.natjSupport=$natjSupport"
    jvmArgs += s"-Dretrolambda.outputDir=${outputDir.getAbsolutePath}"

    val ret = Util.javaexec(retrolambdaJar, jvmArgs.result())().!

    if(ret != 0)
      throw new RuntimeException(s"Retrolambda failed with return code $ret")
  }
}
