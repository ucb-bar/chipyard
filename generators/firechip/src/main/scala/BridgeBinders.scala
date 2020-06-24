//See LICENSE for license details.

package firesim.firesim

import chisel3._
import chisel3.experimental.annotate

import freechips.rocketchip.config.{Field, Config, Parameters}
import freechips.rocketchip.diplomacy.{LazyModule}
import freechips.rocketchip.devices.debug.{Debug, HasPeripheryDebugModuleImp}
import freechips.rocketchip.subsystem.{CanHaveMasterAXI4MemPort, HasExtInterruptsModuleImp, BaseSubsystem, HasTilesModuleImp}
import freechips.rocketchip.tile.{RocketTile}
import sifive.blocks.devices.uart.HasPeripheryUARTModuleImp
import sifive.blocks.devices.gpio.{HasPeripheryGPIOModuleImp}

import testchipip.{CanHavePeripherySerialModuleImp, CanHavePeripheryBlockDeviceModuleImp}
import icenet.CanHavePeripheryIceNICModuleImp

import junctions.{NastiKey, NastiParameters}
import midas.models.{FASEDBridge, AXI4EdgeSummary, CompleteConfig}
import midas.targetutils.{MemModelAnnotation}
import firesim.bridges._
import firesim.configs.MemModelKey
import tracegen.{TraceGenSystemModuleImp}
import ariane.ArianeTile

import boom.common.{BoomTile}

import chipyard.iobinders.{IOBinders, OverrideIOBinder, ComposeIOBinder}
import testchipip.{CanHaveTraceIOModuleImp}

object MainMemoryConsts {
  val regionNamePrefix = "MainMemory"
  def globalName = s"${regionNamePrefix}_${NodeIdx()}"
}

class WithSerialBridge extends OverrideIOBinder({
  (system: CanHavePeripherySerialModuleImp) =>
    system.serial.foreach(s => SerialBridge(system.clock, s, MainMemoryConsts.globalName)(system.p)); Nil
})

class WithNICBridge extends OverrideIOBinder({
  (system: CanHavePeripheryIceNICModuleImp) =>
    system.net.foreach(n => NICBridge(system.clock, n)(system.p)); Nil
})

class WithUARTBridge extends OverrideIOBinder({
  (system: HasPeripheryUARTModuleImp) =>
    system.uart.foreach(u => UARTBridge(system.clock, u)(system.p)); Nil
})

class WithBlockDeviceBridge extends OverrideIOBinder({
  (system: CanHavePeripheryBlockDeviceModuleImp) =>
    system.bdev.foreach(b => BlockDevBridge(system.clock, b, system.reset.toBool)(system.p)); Nil
})


class WithFASEDBridge extends OverrideIOBinder({
  (system: CanHaveMasterAXI4MemPort with BaseSubsystem) => {
    implicit val p = system.p
    (system.mem_axi4 zip system.memAXI4Node.in).foreach({ case (axi4, (_, edge)) =>
      val nastiKey = NastiParameters(axi4.r.bits.data.getWidth,
                                     axi4.ar.bits.addr.getWidth,
                                     axi4.ar.bits.id.getWidth)
      FASEDBridge(system.module.clock, axi4, system.module.reset.toBool,
        CompleteConfig(p(firesim.configs.MemModelKey),
                       nastiKey,
                       Some(AXI4EdgeSummary(edge)),
                       Some(MainMemoryConsts.globalName)))
    })
    Nil
  }
})

class WithTracerVBridge extends ComposeIOBinder({
  (system: CanHaveTraceIOModuleImp) =>
    system.traceIO.foreach(_.traces.map(tileTrace => TracerVBridge(tileTrace)(system.p))); Nil
})



class WithDromajoBridge extends ComposeIOBinder({
  (system: CanHaveTraceIOModuleImp) => {
    system.traceIO.foreach(_.traces.map(tileTrace => DromajoBridge(tileTrace)(system.p))); Nil
  }
})


class WithTraceGenBridge extends OverrideIOBinder({
  (system: TraceGenSystemModuleImp) =>
    GroundTestBridge(system.clock, system.success)(system.p); Nil
})

class WithFireSimMultiCycleRegfile extends ComposeIOBinder({
  (system: HasTilesModuleImp) => {
    system.outer.tiles.map {
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
      case _ =>
    }
    Nil
  }
})

class WithTiedOffSystemGPIO extends OverrideIOBinder({
  (system: HasPeripheryGPIOModuleImp) =>
    system.gpio.foreach(_.pins.foreach(_.i.ival := false.B)); Nil
})

class WithTiedOffSystemDebug extends OverrideIOBinder({
  (system: HasPeripheryDebugModuleImp) => {
    Debug.tieoffDebug(system.debug, system.resetctrl, Some(system.psd))(system.p)
    // tieoffDebug doesn't actually tie everything off :/
    system.debug.foreach { d =>
      d.clockeddmi.foreach({ cdmi => cdmi.dmi.req.bits := DontCare })
      d.dmactiveAck := DontCare
    }
    Nil
  }
})

class WithTiedOffSystemInterrupts extends OverrideIOBinder({
  (system: HasExtInterruptsModuleImp) =>
    system.interrupts := 0.U; Nil
})


// Shorthand to register all of the provided bridges above
class WithDefaultFireSimBridges extends Config(
  new WithTiedOffSystemGPIO ++
  new WithTiedOffSystemDebug ++
  new WithTiedOffSystemInterrupts ++
  new WithSerialBridge ++
  new WithNICBridge ++
  new WithUARTBridge ++
  new WithBlockDeviceBridge ++
  new WithFASEDBridge ++
  new WithFireSimMultiCycleRegfile ++
  new WithTracerVBridge
)
