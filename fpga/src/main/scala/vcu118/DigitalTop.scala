package chipyard.fpga.vcu118

import chisel3._

import freechips.rocketchip.subsystem._
import freechips.rocketchip.system._
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.devices.tilelink._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.util.{DontTouch}

import chipyard.{DigitalTop, DigitalTopModule, ChipyardSubsystem, ChipyardSubsystemModuleImp}

// ------------------------------------
// VCU118 DigitalTop
// ------------------------------------

class VCU118DigitalTop(implicit p: Parameters) extends VCU118ChipyardSystem
  with testchipip.CanHaveTraceIO // Enables optionally adding trace IO
  with testchipip.CanHaveBackingScratchpad // Enables optionally adding a backing scratchpad
  with testchipip.CanHavePeripheryBlockDevice // Enables optionally adding the block device
  with testchipip.CanHavePeripheryTLSerial // Enables optionally adding the backing memory and serial adapter
  with sifive.blocks.devices.uart.HasPeripheryUART // Enables optionally adding the sifive UART
  with sifive.blocks.devices.gpio.HasPeripheryGPIO // Enables optionally adding the sifive GPIOs
  with sifive.blocks.devices.spi.HasPeripherySPIFlash // Enables optionally adding the sifive SPI flash controller
  with sifive.blocks.devices.spi.HasPeripherySPI // Enables optionally adding the sifive SPI port
  with icenet.CanHavePeripheryIceNIC // Enables optionally adding the IceNIC for FireSim
  with chipyard.example.CanHavePeripheryInitZero // Enables optionally adding the initzero example widget
  with chipyard.example.CanHavePeripheryGCD // Enables optionally adding the GCD example widget
  with chipyard.example.CanHavePeripheryStreamingFIR // Enables optionally adding the DSPTools FIR example widget
  with chipyard.example.CanHavePeripheryStreamingPassthrough // Enables optionally adding the DSPTools streaming-passthrough example widget
  with nvidia.blocks.dla.CanHavePeripheryNVDLA // Enables optionally having an NVDLA
{
  override lazy val module = new VCU118DigitalTopModule(this)
}

class VCU118DigitalTopModule[+L <: VCU118DigitalTop](l: L) extends VCU118ChipyardSystemModule(l)
  with testchipip.CanHaveTraceIOModuleImp
  with sifive.blocks.devices.uart.HasPeripheryUARTModuleImp
  with sifive.blocks.devices.gpio.HasPeripheryGPIOModuleImp
  with sifive.blocks.devices.spi.HasPeripherySPIFlashModuleImp
  with sifive.blocks.devices.spi.HasPeripherySPIModuleImp
  with chipyard.example.CanHavePeripheryGCDModuleImp
  with freechips.rocketchip.util.DontTouch

// ------------------------------------
// VCU118 Chipyard System
// ------------------------------------

class VCU118ChipyardSystem(implicit p: Parameters) extends ChipyardSubsystem
  with HasAsyncExtInterrupts
  with CanHaveMasterTLMemPort // expose a TL port for outer memory (replaces AXI outer port)
  with CanHaveMasterAXI4MMIOPort
  with CanHaveSlaveAXI4Port
{

  val bootROM  = p(BootROMLocated(location)).map { BootROM.attach(_, this, CBUS) }
  val maskROMs = p(MaskROMLocated(location)).map { MaskROM.attach(_, this, CBUS) }
  override lazy val module = new VCU118ChipyardSystemModule(this)
}

class VCU118ChipyardSystemModule[+L <: VCU118ChipyardSystem](_outer: L) extends ChipyardSubsystemModuleImp(_outer)
  with HasRTCModuleImp
  with HasExtInterruptsModuleImp
  with DontTouch

// ------------------------------------
// VCU118 Mem Port Mixin
// ------------------------------------

/** Adds a port to the system intended to master an TL DRAM controller. */
trait CanHaveMasterTLMemPort { this: BaseSubsystem =>
  private val memPortParamsOpt = p(ExtMem)
  private val portName = "tl_mem"
  private val device = new MemoryDevice
  private val idBits = memPortParamsOpt.map(_.master.idBits).getOrElse(1)

  val memTLNode = TLManagerNode(memPortParamsOpt.map({ case MemoryPortParams(memPortParams, nMemoryChannels) =>
    Seq.tabulate(nMemoryChannels) { channel =>
      val base = AddressSet.misaligned(memPortParams.base, memPortParams.size)
      val filter = AddressSet(channel * mbus.blockBytes, ~((nMemoryChannels-1) * mbus.blockBytes))

     TLSlavePortParameters.v1(
       managers = Seq(TLSlaveParameters.v1(
         address            = base.flatMap(_.intersect(filter)),
         resources          = device.reg,
         regionType         = RegionType.UNCACHED, // cacheable
         executable         = true,
         supportsGet        = TransferSizes(1, mbus.blockBytes),
         supportsPutFull    = TransferSizes(1, mbus.blockBytes),
         supportsPutPartial = TransferSizes(1, mbus.blockBytes))),
         beatBytes = memPortParams.beatBytes)
   }
 }).toList.flatten)

 mbus.coupleTo(s"memory_controller_port_named_$portName") {
   (memTLNode
     :*= TLBuffer()
     :*= TLSourceShrinker(1 << idBits)
     :*= TLWidthWidget(mbus.beatBytes)
     :*= _)
  }

  val mem_tl = InModuleBody { memTLNode.makeIOs() }
}
