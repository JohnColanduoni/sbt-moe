package sbtmoe

import java.io.{File => _, _}
import java.nio.file.{Files, Path}
import java.util.function.Consumer
import java.util.jar.{JarFile, JarOutputStream}

import org.objectweb.asm.{AnnotationVisitor, ClassReader, ClassVisitor, Opcodes}
import sbt.Keys.TaskStreams
import sbt._

import scala.collection.JavaConversions._
import scala.collection.{SeqView, mutable}

private object JarProcessing {
  def preregister(inputs: Seq[File], outputPath: File): Unit = {
    IO.createDirectory(outputPath.getParentFile)

    val preregisterWriter = new FileWriter(outputPath)
    try {
      inputs.foreach { input =>
        if(input.isDirectory) {
          Files.walk(input.toPath)
            .iterator()
            .filter { _.getFileName.endsWith(".class") }
            .foreach { classFile =>
              val classStream = new FileInputStream(classFile.toFile)
              try {
                hasPreregisterAnnotation(classStream) match {
                  case Some(className) =>
                    preregisterWriter
                      .append(className)
                      .append('\n')
                  case _ =>
                }
              } finally {
                classStream.close()
              }
            }
        } else {
          val jar = new JarFile(input)
          try {
            jar.entries().foreach { entry =>
              if(entry.getName.endsWith(".class")) {
                val classStream = jar.getInputStream(entry)
                try {
                  hasPreregisterAnnotation(classStream) match {
                    case Some(className) =>
                      preregisterWriter
                        .append(className)
                        .append('\n')
                    case _ =>
                  }
                } finally {
                  classStream.close()
                }
              }
            }
          } finally {
            jar.close()
          }
        }
      }
    } finally {
      preregisterWriter.close()
    }
  }

  private def hasPreregisterAnnotation(stream: InputStream): Option[String] = {
    var hasAnnotation = false
    var className: Option[String] = None

    val classReader = new ClassReader(stream)
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

    if(hasAnnotation) {
      className
    } else {
      None
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

  def aggregateClassFiles(inputPaths: Seq[File], outputPath: File, streams: TaskStreams): Unit = {
    val buffer = new Array[Byte](4096)
    val existingEntries = mutable.Set.empty[String]

    IO.createDirectory(outputPath)

    inputPaths.foreach { input =>
      val jar = new JarFile(input)
      try {
        jar.entries().foreach { entry =>
          if(entry.getName.endsWith(".class")) {
            val classInputStream = jar.getInputStream(entry)
            try {
              val classOutputPath = outputPath / entry.getName
              IO.createDirectory(classOutputPath.getParentFile)
              val classOutputStream = new FileOutputStream(classOutputPath)
              try {
                var moreBytes = true
                while(moreBytes) {
                  val bytesRead = classInputStream.read(buffer)
                  if(bytesRead > 0) {
                    classOutputStream.write(buffer, 0, bytesRead)
                  } else {
                    moreBytes = false
                  }
                }
              } finally {
                classOutputStream.close()
              }
            } finally {
              classInputStream.close()
            }
          }
        }
      } finally {
        jar.close()
      }
    }
  }

  // Maps directories in a classpath to a list of class files
  def classpathToFileList(classpath: Iterable[File]): Iterable[File] = {
    classpath.view.flatMap { file =>
      if(file.isDirectory) {
        Files.walk(file.toPath).iterator().map { _.toFile }
      } else {
        Seq(file)
      }
    }.toIterable
  }
}
