package sbtmoe

sealed abstract class TargetSDK {
  private[sbtmoe] def xcodeName: String
  private[sbtmoe] final def name: String = xcodeName

  override def toString: String = name
}

object TargetSDK {
  case object IPhoneOS extends TargetSDK {
    def xcodeName = "iphoneos"
  }

  case object IPhoneSim extends TargetSDK {
    def xcodeName = "iphonesimulator"
  }
}
