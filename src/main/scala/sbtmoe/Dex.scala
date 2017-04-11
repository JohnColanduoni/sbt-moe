package sbtmoe

import sbt._

private object Dex {
  def dex(
    dexJar: File,
    output: File,
    inputs: Seq[File],
    streams: sbt.Keys.TaskStreams,
    core: Boolean = false,
    multidex: Boolean = false
  ): Unit = {
    val args = Seq.newBuilder[String]

    args += "--dex"
    args += "--output=" + output.absolutePath

    if(core)
      args += "--core-library"

    if(multidex)
      args += "--multi-dex"

    args ++= inputs.view.map {_.absolutePath}

    val ret = Util.javaexec(dexJar)(args.result(): _*).!

    if(ret != 0)
      throw new RuntimeException(s"Dexing failed with return code $ret")
  }
}
