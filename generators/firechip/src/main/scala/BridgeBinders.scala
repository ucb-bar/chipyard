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

import testchipip.{CanHavePeripherySerialModuleImp, CanHavePeripheryBlockDeviceModuleImp}
import icenet.CanHavePeripheryIceNICModuleImp

import junctions.{NastiKey, NastiParameters}
import midas.models.{FASEDBridge, AXI4EdgeSummary, CompleteConfig}
import midas.targetutils.{MemModelAnnotation}
import firesim.bridges._
import firesim.configs.MemModelKey
import tracegen.HasTraceGenTilesModuleImp

import boom.common.{BoomTile}

import chipyard.iobinders.{IOBinders, RegisterIOBinder}
import chipyard.HasBoomAndRocketTilesModuleImp

class WithSerialBridge extends RegisterIOBinder({
  (c, r, s, target: CanHavePeripherySerialModuleImp) => target.serial.map(s => SerialBridge(s)(target.p)).toSeq
})

class WithNICBridge extends RegisterIOBinder({
  (c, r, s, target: CanHavePeripheryIceNICModuleImp) => target.net.map(n => NICBridge(n)(target.p)).toSeq
})

class WithUARTBridge extends RegisterIOBinder({
  (c, r, s, target: HasPeripheryUARTModuleImp) => target.uart.map(u => UARTBridge(u)(target.p)).toSeq
})

class WithBlockDeviceBridge extends RegisterIOBinder({
  (c, r, s, target: CanHavePeripheryBlockDeviceModuleImp) => target.bdev.map(b => BlockDevBridge(b, target.reset.toBool)(target.p)).toSeq
})

class WithFASEDBridge extends RegisterIOBinder({
  (c, r, s, t: CanHaveMasterAXI4MemPortModuleImp) => {
    implicit val p = t.p
    (t.mem_axi4 zip t.outer.memAXI4Node).flatMap({ case (io, node) =>
      (io zip node.in).map({ case (axi4Bundle, (_, edge)) =>
        val nastiKey = NastiParameters(axi4Bundle.r.bits.data.getWidth,
                                       axi4Bundle.ar.bits.addr.getWidth,
                                       axi4Bundle.ar.bits.id.getWidth)
        FASEDBridge(axi4Bundle, t.reset.toBool,
          CompleteConfig(p(firesim.configs.MemModelKey), nastiKey, Some(AXI4EdgeSummary(edge))))
      })
    }).toSeq
  }
})

class WithTracerVBridge extends RegisterIOBinder({
  (c, r, s, target: HasTraceIOImp) => Seq(TracerVBridge(target.traceIO)(target.p))
})

class WithTraceGenBridge extends RegisterIOBinder({
  (c, r, s, target: HasTraceGenTilesModuleImp) => Seq(GroundTestBridge(target.success)(target.p))
})

class WithFireSimMultiCycleRegfile extends RegisterIOBinder({
  (c, r, s, target: HasBoomAndRocketTilesModuleImp) => {
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
    }
    Nil
  }
})



// Shorthand to register all of the provided bridges above
class WithDefaultFireSimBridges extends Config(
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
