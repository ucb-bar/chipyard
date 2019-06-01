package example

import chisel3._

import freechips.rocketchip.subsystem._
import freechips.rocketchip.system._
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.devices.tilelink._
import freechips.rocketchip.util.DontTouch

import testchipip._

import sifive.blocks.devices.gpio._

// ------------------------------------
// BOOM and/or Rocket Top Level Systems
// ------------------------------------

/**
 * Top level system that extends supports async interrupts, AXI4 ports, a bootrom, and test harness collateral
 */
class BoomRocketTop(implicit p: Parameters) extends boom.system.ExampleBoomAndRocketSystem
  with HasNoDebug
  with HasPeripherySerial {
  override lazy val module = new BoomRocketTopModule(this)
}

/**
 * Top level system implementation that extends supports async interrupts, AXI4 ports, a bootrom, and test harness collateral
 */
class BoomRocketTopModule[+L <: BoomRocketTop](l: L) extends boom.system.ExampleBoomAndRocketSystemModule(l)
  with HasNoDebugModuleImp
  with HasPeripherySerialModuleImp
  with DontTouch

//---------------------------------------------------------------------------------------------------------

/**
 * System that extends the BoomRocketTop system with PWM
 */
class BoomRocketTopWithPWMTL(implicit p: Parameters) extends BoomRocketTop
  with HasPeripheryPWMTL {
  override lazy val module = new BoomRocketTopWithPWMTLModule(this)
}

/**
 * System implementation that extends the BoomRocketTop system implementation with PWM
 */
class BoomRocketTopWithPWMTLModule(l: BoomRocketTopWithPWMTL) extends BoomRocketTopModule(l)
  with HasPeripheryPWMTLModuleImp

//---------------------------------------------------------------------------------------------------------

/**
 * System that extends the BoomRocketTop system with PWM and an AXI4 port
 */
class BoomRocketTopWithPWMAXI4(implicit p: Parameters) extends BoomRocketTop
  with HasPeripheryPWMAXI4 {
  override lazy val module = new BoomRocketTopWithPWMAXI4Module(this)
}

/**
 * System implementation that extends the BoomRocketTop system implementation with PWM and an AXI4 port
 */
class BoomRocketTopWithPWMAXI4Module(l: BoomRocketTopWithPWMAXI4) extends BoomRocketTopModule(l)
  with HasPeripheryPWMAXI4ModuleImp

//---------------------------------------------------------------------------------------------------------

/**
 * System that extends the BoomRocketTop system with a block device
 */
class BoomRocketTopWithBlockDevice(implicit p: Parameters) extends BoomRocketTop
  with HasPeripheryBlockDevice {
  override lazy val module = new BoomRocketTopWithBlockDeviceModule(this)
}

/**
 * System implementation that extends the BoomRocketTop system implementation with a block device
 */
class BoomRocketTopWithBlockDeviceModule(l: BoomRocketTopWithBlockDevice) extends BoomRocketTopModule(l)
  with HasPeripheryBlockDeviceModuleImp

//---------------------------------------------------------------------------------------------------------

/**
 * System that extends the BoomRocketTop system with GPIO ports
 */
class BoomRocketTopWithGPIO(implicit p: Parameters) extends BoomRocketTop
    with HasPeripheryGPIO {
  override lazy val module = new BoomRocketTopWithGPIOModule(this)
}

/**
 * System implementation that extends the BoomRocketTop system implementation with GPIO ports
 */
class BoomRocketTopWithGPIOModule(l: BoomRocketTopWithGPIO)
  extends BoomRocketTopModule(l)
  with HasPeripheryGPIOModuleImp
