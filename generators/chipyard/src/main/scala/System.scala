//******************************************************************************
// Copyright (c) 2019 - 2019, The Regents of the University of California (Regents).
// All Rights Reserved. See LICENSE and LICENSE.SiFive for license details.
//------------------------------------------------------------------------------

package chipyard

import chisel3._

import freechips.rocketchip.config.{Parameters, Field}
import freechips.rocketchip.subsystem._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.devices.tilelink._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.util.{DontTouch}
import freechips.rocketchip.util._

// ---------------------------------------------------------------------
// Base system that uses the debug test module (dtm) to bringup the core
// ---------------------------------------------------------------------

/**
 * Base top with periphery devices and ports, and a BOOM + Rocket subsystem
 */
class ChipyardSystem(implicit p: Parameters) extends ChipyardSubsystem
  with HasAsyncExtInterrupts
  with CanHaveMasterTLMemPort // export TL port for outer memory
  with CanHaveSlaveTLExtPort
  with CanHaveMasterTLExtPort
  with CanHaveMasterAXI4MemPort // expose AXI port for outer mem
  with CanHaveMasterAXI4MMIOPort
  with CanHaveSlaveAXI4Port
{

  val bootROM  = p(BootROMLocated(location)).map { BootROM.attach(_, this, CBUS) }
  val maskROMs = p(MaskROMLocated(location)).map { MaskROM.attach(_, this, CBUS) }
  override lazy val module = new ChipyardSystemModule(this)
}

/**
 * Base top module implementation with periphery devices and ports, and a BOOM + Rocket subsystem
 */
class ChipyardSystemModule[+L <: ChipyardSystem](_outer: L) extends ChipyardSubsystemModuleImp(_outer)
  with HasRTCModuleImp
  with HasExtInterruptsModuleImp
  with DontTouch

// ------------------------------------
// TL Mem Port Mixin
// ------------------------------------

// Similar to ExtMem but instantiates a TL mem port
case object ExtTLMem extends Field[Option[MemoryPortParams]](None)

/** Adds a port to the system intended to master an TL DRAM controller. */
trait CanHaveMasterTLMemPort { this: BaseSubsystem =>

  require(!(p(ExtTLMem).nonEmpty && p(ExtMem).nonEmpty),
    "Can only have 1 backing memory port. Use ExtTLMem for a TL memory port or ExtMem for an AXI memory port.")

  private val memPortParamsOpt = p(ExtTLMem)
  private val portName = "tl_mem"
  private val device = new MemoryDevice
  private val idBits = memPortParamsOpt.map(_.master.idBits).getOrElse(1)

  val memTLNode = TLManagerNode(memPortParamsOpt.map({ case MemoryPortParams(memPortParams, nMemoryChannels, _) =>
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

case object ExtTLBus extends Field[Option[MasterPortParams]](None)
case object ExtTLIn extends Field[Option[SlavePortParams]](None)


trait CanHaveMasterTLExtPort { this: BaseSubsystem =>
  private val extMasterTLPortParamsOpt = p(ExtTLBus)
  private val portName = "ext_master_port_tl"
  private val device = new SimpleBus(portName.kebab, Nil)

  val extTLNode = TLManagerNode(
    extMasterTLPortParamsOpt.map(params =>
      TLSlavePortParameters.v1(
        managers = Seq(TLSlaveParameters.v1(
          address            = AddressSet.misaligned(params.base, params.size),
          resources          = device.ranges,
          executable         = params.executable,
          supportsGet        = TransferSizes(1, sbus.blockBytes),
          supportsPutFull    = TransferSizes(1, sbus.blockBytes),
          supportsPutPartial = TransferSizes(1, sbus.blockBytes))),
        beatBytes = params.beatBytes)).toSeq)

  extMasterTLPortParamsOpt.map { params =>
    sbus.coupleTo(s"port_named_$portName") {
      (extTLNode
        := TLBuffer()
        := TLSourceShrinker(1 << params.idBits)
        := TLWidthWidget(sbus.beatBytes)
        := _ )
    }
  }

  val ext_master_tl = InModuleBody {
    extTLNode.out.foreach { case (_, edge) => println(edge.prettySourceMapping(s"TL EXT Port")) }
    extTLNode.makeIOs()
  }
}

trait CanHaveSlaveTLExtPort { this: BaseSubsystem =>
  private val extSlaveTLPortParamsOpt = p(ExtTLIn)
  private val portName = "ext_slave_port_tl"

  val l2FrontendTLNode = TLClientNode(
    extSlaveTLPortParamsOpt.map(params =>
      TLMasterPortParameters.v1(
        clients = Seq(TLMasterParameters.v1(
          name     = portName.kebab,
          sourceId = IdRange(0, 1 << params.idBits))))).toSeq)

  extSlaveTLPortParamsOpt.map { params =>
    sbus.coupleFrom(s"port_named_$portName") {
      ( _
        := TLSourceShrinker(1 << params.sourceBits)
        := TLWidthWidget(params.beatBytes)
        := l2FrontendTLNode )
    }
  }

  val l2_frontend_bus_tl = InModuleBody { l2FrontendTLNode.makeIOs() }
}
