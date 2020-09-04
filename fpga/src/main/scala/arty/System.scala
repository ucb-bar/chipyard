// See LICENSE for license details.
package sifive.freedom.everywhere.e300artydevkit

import Chisel._

import freechips.rocketchip.config._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.devices.debug._
import freechips.rocketchip.devices.tilelink._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.system._

import sifive.blocks.devices.mockaon._
import sifive.blocks.devices.gpio._
import sifive.blocks.devices.pwm._
import sifive.blocks.devices.spi._
import sifive.blocks.devices.uart._
import sifive.blocks.devices.i2c._

//-------------------------------------------------------------------------
// E300ArtyDevKitSystem
//-------------------------------------------------------------------------

class E300ArtyDevKitSystem(implicit p: Parameters) extends RocketSubsystem
    with HasPeripheryDebug
    with HasPeripheryMockAON
    with chipyard.example.CanHavePeripheryGCD
    with HasPeripheryUART
    with HasPeripherySPIFlash
    with HasPeripherySPI
    with HasPeripheryGPIO
    with HasPeripheryPWM
    with HasPeripheryI2C {
  val bootROM  = p(BootROMLocated(location)).map { BootROM.attach(_, this, CBUS) }
  val maskROMs = p(MaskROMLocated(location)).map { MaskROM.attach(_, this, CBUS) }

  val maskROMResetVectorSourceNode = BundleBridgeSource[UInt]()
  tileResetVectorNexusNode := maskROMResetVectorSourceNode

  override lazy val module = new E300ArtyDevKitSystemModule(this)
}

class E300ArtyDevKitSystemModule[+L <: E300ArtyDevKitSystem](_outer: L)
  extends RocketSubsystemModuleImp(_outer)
    with HasPeripheryDebugModuleImp
    with chipyard.example.CanHavePeripheryGCDModuleImp
    with HasPeripheryUARTModuleImp
    with HasPeripherySPIModuleImp
    with HasPeripheryGPIOModuleImp
    with HasPeripherySPIFlashModuleImp
    with HasPeripheryMockAONModuleImp
    with HasPeripheryPWMModuleImp
    with HasPeripheryI2CModuleImp {

  // connect reset vector to 1st MaskROM
  _outer.maskROMResetVectorSourceNode.bundle := p(MaskROMLocated(_outer.location))(0).address.U
}
