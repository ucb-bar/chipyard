package example

import chisel3._

import freechips.rocketchip.subsystem._
import freechips.rocketchip.system._
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.devices.tilelink._
import freechips.rocketchip.util.DontTouch

import testchipip._

import utilities.{System, SystemModule}

import sifive.blocks.devices.gpio._

// ------------------------------------
// BOOM and/or Rocket Top Level Systems
// ------------------------------------

class Top(implicit p: Parameters) extends System
  with HasNoDebug
  with HasPeripherySerial {
  override lazy val module = new TopModule(this)
}

class TopModule[+L <: Top](l: L) extends SystemModule(l)
  with HasNoDebugModuleImp
  with HasPeripherySerialModuleImp
  with DontTouch

//---------------------------------------------------------------------------------------------------------
// DOC include start: TopWithPWMTL

class TopWithPWMTL(implicit p: Parameters) extends Top
  with HasPeripheryPWMTL {
  override lazy val module = new TopWithPWMTLModule(this)
}

class TopWithPWMTLModule(l: TopWithPWMTL) extends TopModule(l)
  with HasPeripheryPWMTLModuleImp

// DOC include end: TopWithPWMTL
//---------------------------------------------------------------------------------------------------------

class TopWithPWMAXI4(implicit p: Parameters) extends Top
  with HasPeripheryPWMAXI4 {
  override lazy val module = new TopWithPWMAXI4Module(this)
}

class TopWithPWMAXI4Module(l: TopWithPWMAXI4) extends TopModule(l)
  with HasPeripheryPWMAXI4ModuleImp

//---------------------------------------------------------------------------------------------------------

class TopWithGCD(implicit p: Parameters) extends Top
  with HasPeripheryGCD {
  override lazy val module = new TopWithGCDModule(this)
}

class TopWithGCDModule(l: TopWithGCD) extends TopModule(l)
  with HasPeripheryGCDModuleImp

//---------------------------------------------------------------------------------------------------------

class TopWithBlockDevice(implicit p: Parameters) extends Top
  with HasPeripheryBlockDevice {
  override lazy val module = new TopWithBlockDeviceModule(this)
}

class TopWithBlockDeviceModule(l: TopWithBlockDevice) extends TopModule(l)
  with HasPeripheryBlockDeviceModuleImp

//---------------------------------------------------------------------------------------------------------

class TopWithGPIO(implicit p: Parameters) extends Top
  with HasPeripheryGPIO {
  override lazy val module = new TopWithGPIOModule(this)
}

class TopWithGPIOModule(l: TopWithGPIO)
  extends TopModule(l)
  with HasPeripheryGPIOModuleImp

//---------------------------------------------------------------------------------------------------------

class TopWithDTM(implicit p: Parameters) extends System
{
  override lazy val module = new TopWithDTMModule(this)
}

class TopWithDTMModule[+L <: TopWithDTM](l: L) extends SystemModule(l)

//---------------------------------------------------------------------------------------------------------
// DOC include start: TopWithInitZero
class TopWithInitZero(implicit p: Parameters) extends Top
    with HasPeripheryInitZero {
  override lazy val module = new TopWithInitZeroModuleImp(this)
}

class TopWithInitZeroModuleImp(l: TopWithInitZero) extends TopModule(l)
  with HasPeripheryInitZeroModuleImp
// DOC include end: TopWithInitZero
