package chipyard.fpga.vcu118

import chisel3._

import freechips.rocketchip.subsystem._
import freechips.rocketchip.system._
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.devices.tilelink._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.tilelink._

import chipyard.{DigitalTop, DigitalTopModule}

// ------------------------------------
// VCU118 DigitalTop
// ------------------------------------

class VCU118DigitalTop(implicit p: Parameters) extends DigitalTop
  with sifive.blocks.devices.spi.HasPeripherySPI
  with CanHaveMasterTLMemPort
{
  override lazy val module = new VCU118DigitalTopModule(this)
}

class VCU118DigitalTopModule[+L <: VCU118DigitalTop](l: L) extends DigitalTopModule(l)
  with sifive.blocks.devices.spi.HasPeripherySPIModuleImp

/** Adds a TileLink port to the system intended to master an MMIO device bus */
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
