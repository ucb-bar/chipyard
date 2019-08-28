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

class TopWithPWMTL(implicit p: Parameters) extends Top
  with HasPeripheryPWMTL {
  override lazy val module = new TopWithPWMTLModule(this)
}

class TopWithPWMTLModule(l: TopWithPWMTL) extends TopModule(l)
  with HasPeripheryPWMTLModuleImp

//---------------------------------------------------------------------------------------------------------

class TopWithPWMAXI4(implicit p: Parameters) extends Top
  with HasPeripheryPWMAXI4 {
  override lazy val module = new TopWithPWMAXI4Module(this)
}

class TopWithPWMAXI4Module(l: TopWithPWMAXI4) extends TopModule(l)
  with HasPeripheryPWMAXI4ModuleImp

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
