package sbtmoe

sealed abstract class InstructionSet {
  private[sbtmoe] def dex2oatName: String
  private[sbtmoe] def xcodeName: String
  private[sbtmoe] final def name: String = dex2oatName
  private[sbtmoe] def targetSdk: TargetSDK

  override def toString: String = name
}

object InstructionSet {
  case object ARM extends InstructionSet {
    def dex2oatName = "arm"
    def xcodeName = "arm"
    def targetSdk = TargetSDK.IPhoneOS
  }
  case object ARM64 extends InstructionSet {
    def dex2oatName = "arm64"
    def xcodeName = "arm64"
    def targetSdk = TargetSDK.IPhoneOS
  }
  case object X86 extends InstructionSet {
    def dex2oatName = "x86"
    def xcodeName = "i386"
    def targetSdk = TargetSDK.IPhoneSim
  }
  case object X86_64 extends InstructionSet {
    def dex2oatName = "x86_64"
    def xcodeName = "x86_64"
    def targetSdk = TargetSDK.IPhoneSim
  }
}
