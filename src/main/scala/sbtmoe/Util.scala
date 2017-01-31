package sbtmoe

import sbt._

private object Util {
  def javaexec(jar: File, jvmArgs: Seq[String] = Nil)(programArgs: String*): ProcessBuilder = {
    Process((("java" +: jvmArgs) :+ "-jar" :+ jar.absolutePath) ++ programArgs)
  }
}
