package chipyard

import chisel3._
import freechips.rocketchip.config.{Field, Parameters}
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.amba.axi4._
import freechips.rocketchip.util._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.prci.{ClockGroup, ClockSinkDomain, ClockParameters}

import testchipip.ClockedAndResetIO

case object DRAMCrossingTypeKey extends Field[ClockCrossingType](NoCrossing)
case object DRAMControllerFrequencyMHzKey extends Field[Option[Double]](None)

/** Adds a port to the system intended to master an AXI4 DRAM controller. */
trait CanHaveFlexiblyClockedMasterAXI4MemPort { this: BaseSubsystem =>
  private val memPortParamsOpt = p(ExtMem)
  private val portName = "axi4"
  private val device = new MemoryDevice
  private val idBits = memPortParamsOpt.map(_.master.idBits).getOrElse(1)

  val dramControllerDomainWrapper = LazyModule(new ClockSinkDomain(
    name = Some("dram_controller"),
    take = p(DRAMControllerFrequencyMHzKey).map {f => ClockParameters(f) }))

  dramControllerDomainWrapper.clockNode := (p(DRAMCrossingTypeKey) match {
    case _: SynchronousCrossing =>
      mbus.fixedClockNode
    case _: RationalCrossing =>
      mbus.clockNode
    case _: AsynchronousCrossing =>
      // TODO: determine how we wish to handle the asyncClockGroup
      //val dramClockGroup = ClockGroup()
      //dramClockGroup := asyncClockGroupsNode
      //dramClockGroup
      mbus.clockNode
  })

  val memAXI4Node = AXI4SlaveNode(memPortParamsOpt.map({ case MemoryPortParams(memPortParams, nMemoryChannels) =>
    Seq.tabulate(nMemoryChannels) { channel =>
      val base = AddressSet.misaligned(memPortParams.base, memPortParams.size)
      val filter = AddressSet(channel * mbus.blockBytes, ~((nMemoryChannels-1) * mbus.blockBytes))

      AXI4SlavePortParameters(
        slaves = Seq(AXI4SlaveParameters(
          address       = base.flatMap(_.intersect(filter)),
          resources     = device.reg,
          regionType    = RegionType.UNCACHED, // cacheable
          executable    = true,
          supportsWrite = TransferSizes(1, mbus.blockBytes),
          supportsRead  = TransferSizes(1, mbus.blockBytes),
          interleavedId = Some(0))), // slave does not interleave read responses
        beatBytes = memPortParams.beatBytes)
    }
  }).toList.flatten)

  val dramDomainTLNode = dramControllerDomainWrapper { (memAXI4Node
    :*= AXI4UserYanker()
    :*= AXI4IdIndexer(idBits)
    :*= TLToAXI4())
  }

  mbus.coupleTo(s"memory_controller_port_named_$portName") {
    (dramControllerDomainWrapper.crossIn(dramDomainTLNode)(ValName("dram_crossing"))(p(DRAMCrossingTypeKey))
      :*= TLWidthWidget(mbus.beatBytes)
      :*= _)
  }

  val mem_axi4 = InModuleBody {
    for ((bundle, edge) <- memAXI4Node.in) yield {
      val io = chisel3.experimental.IO(new ClockedAndResetIO(bundle.cloneType)).suggestName("axi4_mem")
      io.bits <> bundle
      io.clock := dramControllerDomainWrapper.module.clock
      io.reset := dramControllerDomainWrapper.module.reset
      io
    }
  }
}


