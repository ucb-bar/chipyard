package chipyard

import chisel3._

import freechips.rocketchip.subsystem._
import freechips.rocketchip.system._
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.devices.tilelink._

// ------------------------------------
// BOOM and/or Rocket Top Level Systems
// ------------------------------------

// DOC include start: DigitalTop
class DigitalTop(implicit p: Parameters) extends System
  with testchipip.CanHaveTraceIO // Enables optionally adding trace IO
  with testchipip.CanHaveBackingScratchpad // Enables optionally adding a backing scratchpad
  with testchipip.CanHavePeripheryBlockDevice // Enables optionally adding the block device
  with testchipip.CanHavePeripherySerial // Enables optionally adding the TSI serial-adapter and port
  with sifive.blocks.devices.uart.HasPeripheryUART // Enables optionally adding the sifive UART
  with sifive.blocks.devices.gpio.HasPeripheryGPIO // Enables optionally adding the sifive GPIOs
  with icenet.CanHavePeripheryIceNIC // Enables optionally adding the IceNIC for FireSim
  with chipyard.example.CanHavePeripheryInitZero // Enables optionally adding the initzero example widget
  with chipyard.example.CanHavePeripheryGCD // Enables optionally adding the GCD example widget
{
  override lazy val module = new DigitalTopModule(this)
}

class DigitalTopModule[+L <: DigitalTop](l: L) extends SystemModule(l)
  with testchipip.CanHaveTraceIOModuleImp
  with testchipip.CanHavePeripheryBlockDeviceModuleImp
  with testchipip.CanHavePeripherySerialModuleImp
  with sifive.blocks.devices.uart.HasPeripheryUARTModuleImp
  with sifive.blocks.devices.gpio.HasPeripheryGPIOModuleImp
  with icenet.CanHavePeripheryIceNICModuleImp
  with chipyard.example.CanHavePeripheryGCDModuleImp
  with freechips.rocketchip.util.DontTouch
// DOC include end: DigitalTop

class MemBladeTop(implicit p: Parameters) extends System
  with testchipip.CanHaveTraceIO
  with testchipip.CanHavePeripheryBlockDevice
  with testchipip.CanHavePeripherySerial
  with sifive.blocks.devices.uart.HasPeripheryUART
  with memblade.manager.HasPeripheryMemBlade
{
  override lazy val module = new MemBladeTopModule(this)
}

class MemBladeTopModule(outer: MemBladeTop) extends SystemModule(outer)
  with testchipip.CanHaveTraceIOModuleImp
  with testchipip.CanHavePeripheryBlockDeviceModuleImp
  with testchipip.CanHavePeripherySerialModuleImp
  with sifive.blocks.devices.uart.HasPeripheryUARTModuleImp
  with memblade.manager.HasPeripheryMemBladeModuleImpValidOnly

class RemoteMemClientTop(implicit p: Parameters) extends System
  with testchipip.CanHaveTraceIO
  with testchipip.CanHavePeripheryBlockDevice
  with testchipip.CanHavePeripherySerial
  with sifive.blocks.devices.uart.HasPeripheryUART
  with memblade.client.HasPeripheryRemoteMemClient
  with testchipip.HasPeripheryMemBench
{
  override lazy val module = new RemoteMemClientTopModule(this)
}

class RemoteMemClientTopModule(outer: RemoteMemClientTop) extends SystemModule(outer)
  with testchipip.CanHaveTraceIOModuleImp
  with testchipip.CanHavePeripheryBlockDeviceModuleImp
  with testchipip.CanHavePeripherySerialModuleImp
  with sifive.blocks.devices.uart.HasPeripheryUARTModuleImp
  with memblade.client.HasPeripheryRemoteMemClientModuleImpValidOnly

class DRAMCacheTop(implicit p: Parameters) extends Subsystem
  with memblade.prefetcher.HasMiddleManBusTopology
  with freechips.rocketchip.subsystem.HasAsyncExtInterrupts
  with freechips.rocketchip.subsystem.CanHaveMasterAXI4MemPort
  with freechips.rocketchip.subsystem.CanHaveMasterAXI4MMIOPort
  with freechips.rocketchip.subsystem.CanHaveSlaveAXI4Port
  with freechips.rocketchip.devices.tilelink.HasPeripheryBootROM
  with testchipip.CanHaveTraceIO
  with testchipip.CanHavePeripheryBlockDevice
  with testchipip.CanHavePeripherySerial
  with sifive.blocks.devices.uart.HasPeripheryUART
  with testchipip.HasPeripheryMemBench
  with memblade.cache.HasPeripheryDRAMCache
{
  override lazy val module = new DRAMCacheTopModule(this)
}

class DRAMCacheTopModule(outer: DRAMCacheTop) extends SubsystemModuleImp(outer)
  with freechips.rocketchip.subsystem.HasRTCModuleImp
  with freechips.rocketchip.subsystem.HasExtInterruptsModuleImp
  with freechips.rocketchip.devices.tilelink.HasPeripheryBootROMModuleImp
  with freechips.rocketchip.util.DontTouch
  with testchipip.CanHaveTraceIOModuleImp
  with testchipip.CanHavePeripheryBlockDeviceModuleImp
  with testchipip.CanHavePeripherySerialModuleImp
  with sifive.blocks.devices.uart.HasPeripheryUARTModuleImp
  with memblade.cache.HasPeripheryDRAMCacheModuleImpValidOnly
