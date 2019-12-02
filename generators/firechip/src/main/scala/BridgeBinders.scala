//See LICENSE for license details.

package firesim.firesim

import chisel3._
import chisel3.experimental.annotate

import freechips.rocketchip.config.{Field, Config, Parameters}
import freechips.rocketchip.diplomacy.{LazyModule}
import freechips.rocketchip.devices.debug.HasPeripheryDebugModuleImp
import freechips.rocketchip.subsystem.{CanHaveMasterAXI4MemPortModuleImp}
import freechips.rocketchip.tile.{RocketTile}
import sifive.blocks.devices.uart.HasPeripheryUARTModuleImp

import testchipip.{CanHavePeripherySerialModuleImp, CanHavePeripheryBlockDeviceModuleImp, CanHaveTraceIOModuleImp}
import icenet.CanHavePeripheryIceNICModuleImp

import junctions.{NastiKey, NastiParameters}
import midas.models.{FASEDBridge, AXI4EdgeSummary, CompleteConfig}
import midas.targetutils.{MemModelAnnotation}
import firesim.bridges._
import firesim.configs.MemModelKey
import tracegen.HasTraceGenTilesModuleImp
import ariane.ArianeTile

import boom.common.{BoomTile}

import chipyard.iobinders.{IOBinders, OverrideIOBinder, ComposeIOBinder}
import chipyard.HasChipyardTilesModuleImp

object MainMemoryConsts {
  val regionNamePrefix = "MainMemory"
  def globalName(): String = s"${regionNamePrefix}_${NodeIdx()}"
}

class WithSerialBridge extends OverrideIOBinder({
  (c, r, s, target: CanHavePeripherySerialModuleImp) =>
    target.serial.map(s => SerialBridge(target.clock, s, MainMemoryConsts.globalName)(target.p)).toSeq
})

class WithNICBridge extends OverrideIOBinder({
  (c, r, s, target: CanHavePeripheryIceNICModuleImp) =>
    target.net.map(n => NICBridge(target.clock, n)(target.p)).toSeq
})

class WithUARTBridge extends OverrideIOBinder({
  (c, r, s, target: HasPeripheryUARTModuleImp) =>
    target.uart.map(u => UARTBridge(target.clock, u)(target.p)).toSeq
})

class WithBlockDeviceBridge extends OverrideIOBinder({
  (c, r, s, target: CanHavePeripheryBlockDeviceModuleImp) =>
    target.bdev.map(b => BlockDevBridge(target.clock, b, target.reset.toBool)(target.p)).toSeq
})

// Assign a unique name to each target memory space, consisting of one or more
// memory channels. In the multi-node case, serial widgets can then disambiguate
// each memory region using this string instead of relying on the assumption
// the target has a single memory channel.
object MemoryRegionNames {
  var idx = -1
  def getName(): String = {
    idx += 1
    s"memory_${idx}"
  }
}

class WithFASEDBridge extends OverrideIOBinder ({
  (c, r, s, t: CanHaveMasterAXI4MemPortModuleImp) => {
    implicit val p = t.p
    (t.mem_axi4 zip t.outer.memAXI4Node).flatMap({ case (io, node) =>
      (io zip node.in).map({ case (axi4Bundle, (_, edge)) =>
        val nastiKey = NastiParameters(axi4Bundle.r.bits.data.getWidth,
                                       axi4Bundle.ar.bits.addr.getWidth,
                                       axi4Bundle.ar.bits.id.getWidth)
        FASEDBridge(t.clock, axi4Bundle, t.reset.toBool,
          CompleteConfig(p(firesim.configs.MemModelKey),
                         nastiKey,
                         Some(AXI4EdgeSummary(edge)),
                         Some(MainMemoryConsts.globalName)))
      })
    }).toSeq
  }
})

class WithTracerVBridge extends OverrideIOBinder({
  (c, r, s, target: CanHaveTraceIOModuleImp) => target.traceIO match {
    case Some(t) => t.traces.map(tileTrace => TracerVBridge(tileTrace)(target.p))
    case None    => Nil
  }
})


class WithTraceGenBridge extends OverrideIOBinder({
  (c, r, s, target: HasTraceGenTilesModuleImp) =>
    Seq(GroundTestBridge(target.clock, target.success)(target.p))
})

class WithFireSimMultiCycleRegfile extends ComposeIOBinder({
  (c, r, s, target: HasChipyardTilesModuleImp) => {
    target.outer.tiles.map {
      case r: RocketTile => {
        annotate(MemModelAnnotation(r.module.core.rocketImpl.rf.rf))
        r.module.fpuOpt.foreach(fpu => annotate(MemModelAnnotation(fpu.fpuImpl.regfile)))
      }
      case b: BoomTile => {
        val core = b.module.core
        core.iregfile match {
          case irf: boom.exu.RegisterFileSynthesizable => annotate(MemModelAnnotation(irf.regfile))
          case _ => Nil
        }
        if (core.fp_pipeline != null) core.fp_pipeline.fregfile match {
          case frf: boom.exu.RegisterFileSynthesizable => annotate(MemModelAnnotation(frf.regfile))
          case _ => Nil
        }
      }
      case a: ArianeTile => Nil
    }
    Nil
  }
})



// Shorthand to register all of the provided bridges above
class WithDefaultFireSimBridges extends Config(
  new chipyard.iobinders.WithGPIOTiedOff ++
  new chipyard.iobinders.WithTiedOffDebug ++
  new chipyard.iobinders.WithTieOffInterrupts ++
  new WithSerialBridge ++
  new WithNICBridge ++
  new WithUARTBridge ++
  new WithBlockDeviceBridge ++
  new WithFASEDBridge ++
  new WithFireSimMultiCycleRegfile ++
  new WithTracerVBridge
)
