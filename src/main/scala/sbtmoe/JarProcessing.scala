package sbtmoe

import java.io.{File, FileOutputStream, FileWriter}
import java.util.jar.{JarFile, JarOutputStream}
import java.util.zip.ZipEntry

import org.objectweb.asm.{AnnotationVisitor, ClassReader, ClassVisitor, Opcodes}
import sbt.IO
import sbt.Keys.{TaskStreams, streams}
import sbtmoe.Keys.{moeOutputPath, startupProviderInputs}

import scala.collection.JavaConversions._
import scala.collection.mutable

private object JarProcessing {
  def preregister(inputs: Seq[File], outputPath: File): Unit = {
    IO.createDirectory(outputPath.getParentFile)

    val preregisterWriter = new FileWriter(outputPath)
    try {
      inputs.foreach { input =>
        val jar = new JarFile(input)
        try {
          jar.entries().foreach { entry =>
            if(entry.getName.endsWith(".class")) {
              var hasAnnotation = false
              var className: Option[String] = None

              val classStream = jar.getInputStream(entry)
              try {
                val classReader = new ClassReader(jar.getInputStream(entry))
                classReader.accept(
                  new ClassVisitor(Opcodes.ASM5) {
                    override def visitAnnotation(
                      desc: String,
                      visible: Boolean
                    ): AnnotationVisitor = {
                      if(desc == "Lorg/moe/natj/general/ann/RegisterOnStartup;")
                        hasAnnotation = true

                      super.visitAnnotation(desc, visible)
                    }

                    override def visit(
                      version: Int,
                      access: Int,
                      name: String,
                      signature: String,
                      superName: String,
                      interfaces: Array[String]
                    ): Unit = {
                      className = Some(name)
                      super.visit(version, access, name, signature, superName, interfaces)
                    }
                  }, 0
                )
              } finally {
                classStream.close()
              }

              if(hasAnnotation) {
                preregisterWriter
                  .append(className.getOrElse {throw new RuntimeException("Failed to determine name of class file")})
                  .append('\n')
              }
            }
          }
        } finally {
          jar.close()
        }
      }
    } finally {
      preregisterWriter.close()
    }
  }

  def aggregateResources(inputPaths: Seq[File], outputPath: File, streams: TaskStreams): Unit = {
    val buffer = new Array[Byte](4096)
    val existingEntries = mutable.Set.empty[String]

    IO.createDirectory(outputPath.getParentFile)

    val outputJar = new JarOutputStream(new FileOutputStream(outputPath))
    try {
      val preregisterWriter = new FileWriter(outputPath)
      try {
        inputPaths.foreach { input =>
          val jar = new JarFile(input)
          try {
            jar.entries().foreach { entry =>
              if(!entry.getName.endsWith(".class")) {
                if(existingEntries.contains(entry.getName)) {
                  streams.log.warn(s"Duplicate JAR entry ${entry.getName}")
                } else {
                  val resourceInputStream = jar.getInputStream(entry)
                  outputJar.putNextEntry(entry)

                  try {
                    var moreBytes = true
                    while(moreBytes) {
                      val bytesRead = resourceInputStream.read(buffer)
                      if(bytesRead > 0) {
                        outputJar.write(buffer, 0, bytesRead)
                      } else {
                        moreBytes = false
                      }
                    }
                  } finally {
                    resourceInputStream.close()
                  }

                  existingEntries += entry.getName
                }
              }
            }
          } finally {
            jar.close()
          }
        }
      } finally {
        preregisterWriter.close()
      }
    } finally {
      outputJar.close()
    }
  }
}
