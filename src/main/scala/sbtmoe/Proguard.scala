package sbtmoe

import sbt._

object Proguard {
  def process(
    proguardJar: File,
    injars: Seq[File],
    libraryjars: Seq[File],
    outjar: File,
    cfgFiles: Seq[File],
    streams: sbt.Keys.TaskStreams
  ): Unit = {
    val args = Seq.newBuilder[String]

    args += "-injars"
    args += injars.view.map {_.absolutePath}.mkString(":")

    args += "-libraryjars"
    args += libraryjars.view.map {_.absolutePath}.mkString(":")

    args += "-outjars"
    args += outjar.absolutePath

    args ++= cfgFiles.map {"@" + _.absolutePath}

    val ret = Util.javaexec(proguardJar)(args.result(): _*).run(
      new ProcessLogger {
        def error(s: => String) = {
          streams.log.error(s)
        }

        def buffer[T](f: => T) = f

        def info(s: => String) = {}
      }
    ).exitValue()

    if(ret != 0)
      throw new RuntimeException(s"Proguard failed with return code $ret")
  }
}
