//See LICENSE for license details.

package firesim.firesim

import chisel3._

import freechips.rocketchip.config.{Field, Config}
import freechips.rocketchip.diplomacy.{LazyModule}
import freechips.rocketchip.devices.debug.HasPeripheryDebugModuleImp
import freechips.rocketchip.subsystem.{CanHaveMasterAXI4MemPortModuleImp}
import sifive.blocks.devices.uart.HasPeripheryUARTModuleImp

import testchipip.{CanHavePeripherySerialModuleImp, CanHavePeripheryBlockDeviceModuleImp}
import icenet.HasPeripheryIceNICModuleImpValidOnly

import junctions.{NastiKey, NastiParameters}
import midas.models.{FASEDBridge, AXI4EdgeSummary, CompleteConfig}
import firesim.bridges._
import firesim.configs.MemModelKey
import firesim.util.RegisterBridgeBinder
import tracegen.HasTraceGenTilesModuleImp

import memblade.cache.{HasDRAMCacheNoNICModuleImp, HasPeripheryDRAMCacheModuleImpValidOnly}
import memblade.client.HasPeripheryRemoteMemClientModuleImpValidOnly
import memblade.manager.HasPeripheryMemBladeModuleImpValidOnly

class WithTiedOffDebug extends RegisterBridgeBinder({ case target: HasPeripheryDebugModuleImp =>
  target.debug.foreach(_.clockeddmi.foreach({ cdmi =>
    cdmi.dmi.req.valid := false.B
    cdmi.dmi.req.bits := DontCare
    cdmi.dmi.resp.ready := false.B
    cdmi.dmiClock := false.B.asClock
    cdmi.dmiReset := false.B
  }))
  Seq()
})

class WithSerialBridge extends RegisterBridgeBinder({
  case target: CanHavePeripherySerialModuleImp => Seq(SerialBridge(target.serial.get)(target.p))
})

class WithNICBridge extends RegisterBridgeBinder({
  case target: HasPeripheryIceNICModuleImpValidOnly => Seq(NICBridge(target.net)(target.p))
})

class WithUARTBridge extends RegisterBridgeBinder({
  case target: HasPeripheryUARTModuleImp => target.uart.map(u => UARTBridge(u)(target.p))
})

class WithBlockDeviceBridge extends RegisterBridgeBinder({
  case target: CanHavePeripheryBlockDeviceModuleImp => Seq(BlockDevBridge(target.bdev.get, target.reset.toBool)(target.p))
})

class WithFASEDBridge extends RegisterBridgeBinder({
  case t: CanHaveMasterAXI4MemPortModuleImp =>
    implicit val p = t.p
    (t.mem_axi4 zip t.outer.memAXI4Node).flatMap({ case (io, node) =>
      (io zip node.in).map({ case (axi4Bundle, (_, edge)) =>
        val nastiKey = NastiParameters(axi4Bundle.r.bits.data.getWidth,
                                       axi4Bundle.ar.bits.addr.getWidth,
                                       axi4Bundle.ar.bits.id.getWidth)
        val lastChannel = axi4Bundle == io.last
        FASEDBridge(axi4Bundle, t.reset.toBool,
          CompleteConfig(
            p(firesim.configs.MemModelKey),
            nastiKey,
            Some(AXI4EdgeSummary(edge)),
            lastChannel))
      })
    }).toSeq
})

class WithTracerVBridge extends RegisterBridgeBinder({
  case target: HasTraceIOImp => TracerVBridge(target.traceIO)(target.p)
})

class WithTraceGenBridge extends RegisterBridgeBinder({
  case target: HasTraceGenTilesModuleImp =>
    Seq(GroundTestBridge(target.success)(target.p))
})

class WithDRAMCacheBridge extends RegisterBridgeBinder({
  case t: HasPeripheryDRAMCacheModuleImpValidOnly =>
    implicit val p = t.p
    val io = t.cache_axi4
    val node = t.outer.outAXI4Node
    val axiBridges = (io zip node.in).map({ case (axi4Bundle, (_, edge)) =>
      val nastiKey = NastiParameters(axi4Bundle.r.bits.data.getWidth,
                                     axi4Bundle.ar.bits.addr.getWidth,
                                     axi4Bundle.ar.bits.id.getWidth)
      val lastChannel = axi4Bundle == io.last
      FASEDBridge(axi4Bundle, t.reset.toBool,
        CompleteConfig(
          p(firesim.configs.MemModelKey),
          nastiKey,
          Some(AXI4EdgeSummary(edge)),
          lastChannel))
    }).toSeq
    val nicBridge = NICBridge(t.net)
    nicBridge +: axiBridges

  case t: HasDRAMCacheNoNICModuleImp =>
    implicit val p = t.p
    val io = t.cache_axi4
    val node = t.outer.outAXI4Node
    val axiBridges = (io zip node.in).map({ case (axi4Bundle, (_, edge)) =>
      val nastiKey = NastiParameters(axi4Bundle.r.bits.data.getWidth,
                                     axi4Bundle.ar.bits.addr.getWidth,
                                     axi4Bundle.ar.bits.id.getWidth)
      val lastChannel = axi4Bundle == io.last
      FASEDBridge(axi4Bundle, t.reset.toBool,
        CompleteConfig(
          p(firesim.configs.MemModelKey),
          nastiKey,
          Some(AXI4EdgeSummary(edge)),
          lastChannel))
    }).toSeq
    val nicBridge = NICBridge(t.net)
    nicBridge +: axiBridges
})

class WithMemBladeBridge extends RegisterBridgeBinder({
  case t: HasPeripheryMemBladeModuleImpValidOnly =>
    Seq(NICBridge(t.net)(t.p))
})

class WithRemoteMemClientBridge extends RegisterBridgeBinder({
  case t: HasPeripheryRemoteMemClientModuleImpValidOnly =>
    Seq(NICBridge(t.net)(t.p))
})

// Shorthand to register all of the provided bridges above
class WithDefaultFireSimBridges extends Config(
  new WithTiedOffDebug ++
  new WithSerialBridge ++
  new WithNICBridge ++
  new WithUARTBridge ++
  new WithBlockDeviceBridge ++
  new WithFASEDBridge ++
  new WithTracerVBridge
)
