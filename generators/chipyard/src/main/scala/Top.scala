package chipyard

import chisel3._

import freechips.rocketchip.subsystem._
import freechips.rocketchip.system._
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.devices.tilelink._
import freechips.rocketchip.util.DontTouch

import testchipip._

import sifive.blocks.devices.gpio._
import sifive.blocks.devices.uart._

import icenet.{CanHavePeripheryIceNIC, CanHavePeripheryIceNICModuleImp}

// ------------------------------------
// BOOM and/or Rocket Top Level Systems
// ------------------------------------

// DOC include start: Top
class Top(implicit p: Parameters) extends System
  with CanHavePeripheryUARTAdapter // Enables optionally adding the UART print adapter
  with HasPeripheryUART // Enables optionally adding the sifive UART
  with HasPeripheryGPIO // Enables optionally adding the sifive GPIOs
  with CanHavePeripheryBlockDevice // Enables optionally adding the block device
  with CanHavePeripheryInitZero // Enables optionally adding the initzero example widget
  with CanHavePeripheryGCD // Enables optionally adding the GCD example widget
  with CanHavePeripherySerial // Enables optionally adding the TSI serial-adapter and port
  with CanHavePeripheryIceNIC // Enables optionally adding the IceNIC for FireSim
  with CanHaveBackingScratchpad // Enables optionally adding a backing scratchpad
{
  override lazy val module = new TopModule(this)
}

class TopModule[+L <: Top](l: L) extends SystemModule(l)
  with HasPeripheryGPIOModuleImp
  with HasPeripheryUARTModuleImp
  with CanHavePeripheryBlockDeviceModuleImp
  with CanHavePeripheryGCDModuleImp
  with CanHavePeripherySerialModuleImp
  with CanHavePeripheryIceNICModuleImp
  with CanHavePeripheryUARTAdapterModuleImp
  with DontTouch
// DOC include end: Top
