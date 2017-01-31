package sbtmoe

import sbt._

object MOEPlugin extends AutoPlugin {
  val autoImport = Keys

  override def projectSettings: Seq[Def.Setting[_]] = Keys.defaultSettings ++ inConfig(Keys.IOS)(Tasks.moeGenTasks) ++
    inConfig(Keys.IOSSimDebug)(Tasks.moeBuildTasks) ++ inConfig(Keys.IOSSimRelease)(Tasks.moeBuildTasks) ++
    inConfig(Keys.IOSDeviceDebug)(Tasks.moeBuildTasks) ++ inConfig(Keys.IOSDeviceRelease)(Tasks.moeBuildTasks)
}
