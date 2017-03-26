(mainClass in Compile) := Some("testy.Main")
autoScalaLibrary := false
(javacOptions in Compile) ++= Seq("-source", "6", "-target", "6")

enablePlugins(MOEPlugin)

moeLibraries
