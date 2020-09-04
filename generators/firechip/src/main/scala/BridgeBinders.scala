//See LICENSE for license details.

package firesim.firesim

import chisel3._
import chisel3.experimental.annotate

import freechips.rocketchip.config.{Field, Config, Parameters}
import freechips.rocketchip.diplomacy.{LazyModule}
import freechips.rocketchip.devices.debug.{Debug, HasPeripheryDebugModuleImp}
import freechips.rocketchip.amba.axi4.{AXI4Bundle}
import freechips.rocketchip.subsystem.{CanHaveMasterAXI4MemPort, HasExtInterruptsModuleImp, BaseSubsystem, HasTilesModuleImp}
import freechips.rocketchip.tile.{RocketTile}
import sifive.blocks.devices.uart._

import testchipip._
import icenet.{CanHavePeripheryIceNICModuleImp, SimNetwork, NicLoopback, NICKey, NICIOvonly}

import junctions.{NastiKey, NastiParameters}
import midas.models.{FASEDBridge, AXI4EdgeSummary, CompleteConfig}
import midas.targetutils.{MemModelAnnotation}
import firesim.bridges._
import firesim.configs.MemModelKey
import tracegen.{TraceGenSystemModuleImp}
import ariane.ArianeTile

import boom.common.{BoomTile}
import barstools.iocell.chisel._
import chipyard.iobinders.{IOBinders, OverrideIOBinder, ComposeIOBinder, GetSystemParameters, IOCellKey}
import chipyard.{HasHarnessSignalReferences}
import chipyard.harness._

object MainMemoryConsts {
  val regionNamePrefix = "MainMemory"
  def globalName = s"${regionNamePrefix}_${NodeIdx()}"
}

trait Unsupported {
  require(false, "We do not support this IOCell type")
}

class FireSimAnalogIOCell extends RawModule with AnalogIOCell with Unsupported
class FireSimDigitalGPIOCell extends RawModule with DigitalGPIOCell with Unsupported
class FireSimDigitalInIOCell extends RawModule with DigitalInIOCell { io.i := io.pad }
class FireSimDigitalOutIOCell extends RawModule with DigitalOutIOCell { io.pad := io.o }

case class FireSimIOCellParams() extends IOCellTypeParams {
  def analog() = Module(new FireSimAnalogIOCell)
  def gpio()   = Module(new FireSimDigitalGPIOCell)
  def input()  = Module(new FireSimDigitalInIOCell)
  def output() = Module(new FireSimDigitalOutIOCell)
}

class WithFireSimIOCellModels extends Config((site, here, up) => {
  case IOCellKey => FireSimIOCellParams()
})

class WithSerialBridge extends OverrideHarnessBinder({
  (system: CanHavePeripherySerial, th: HasHarnessSignalReferences, ports: Seq[Data]) => {
    val clock = ports.collectFirst({case c: Clock => c})
    val p: Parameters = chipyard.iobinders.GetSystemParameters(system)
    ports.filter(_.isInstanceOf[SerialIO]).map {
      case s: SerialIO => withClockAndReset(clock.get, th.harnessReset) {
        SerialBridge(clock.get, s, MainMemoryConsts.globalName)(p)
      }
      case _ =>
    }
    Nil
  }
})

class WithNICBridge extends OverrideHarnessBinder({
  (system: CanHavePeripheryIceNICModuleImp, th: HasHarnessSignalReferences, ports: Seq[Data]) => {
    val clock = ports.collectFirst({case c: Clock => c})
    ports.map {
      case p: NICIOvonly => withClockAndReset(clock.get, th.harnessReset) { NICBridge(clock.get, p)(system.p) }
      case _ =>
    }
    Nil
  }
})

class WithUARTBridge extends OverrideHarnessBinder({
  (system: HasPeripheryUARTModuleImp, th: HasHarnessSignalReferences, ports: Seq[Data]) =>
    ports.map { case p: UARTPortIO => UARTBridge(th.harnessClock, p)(system.p) }; Nil
})

class WithBlockDeviceBridge extends OverrideHarnessBinder({
  (system: CanHavePeripheryBlockDeviceModuleImp, th: HasHarnessSignalReferences, ports: Seq[Data]) => {
    val clock = ports.collectFirst({case c: Clock => c})
    ports.map {
      case p: BlockDeviceIO => BlockDevBridge(clock.get, p, th.harnessReset.toBool)(system.p)
      case _ =>
    }
    Nil
  }
})

class WithFASEDBridge extends OverrideHarnessBinder({
  (system: CanHaveMasterAXI4MemPort, th: HasHarnessSignalReferences, ports: Seq[Data]) => {
    implicit val p: Parameters = GetSystemParameters(system)
    val clock = ports.collectFirst({case c: Clock => c})
    val axi4_ports = ports.collect { case p: AXI4Bundle => p }
    (axi4_ports zip system.memAXI4Node.edges.in).map { case (axi4: AXI4Bundle, edge) =>
      val nastiKey = NastiParameters(axi4.r.bits.data.getWidth,
                                     axi4.ar.bits.addr.getWidth,
                                     axi4.ar.bits.id.getWidth)
      system match {
        case s: BaseSubsystem => FASEDBridge(clock.get, axi4, th.harnessReset.asBool,
          CompleteConfig(p(firesim.configs.MemModelKey),
                         nastiKey,
                         Some(AXI4EdgeSummary(edge)),
                         Some(MainMemoryConsts.globalName)))
        case _ => throw new Exception("Attempting to attach FASED Bridge to misconfigured design")
      }
    }
    Nil
  }
})

class WithTracerVBridge extends ComposeHarnessBinder({
  (system: CanHaveTraceIOModuleImp, th: HasHarnessSignalReferences, ports: Seq[Data]) => {
    ports.map { case p: TraceOutputTop => p.traces.map(tileTrace =>
      withClockAndReset(tileTrace.clock, tileTrace.reset) { TracerVBridge(tileTrace)(system.p) }
    )}
  }
})

class WithDromajoBridge extends ComposeHarnessBinder({
  (system: CanHaveTraceIOModuleImp, th: HasHarnessSignalReferences, ports: Seq[Data]) =>
    ports.map { case p: TraceOutputTop => p.traces.map(tileTrace => DromajoBridge(tileTrace)(system.p)) }; Nil
})


class WithTraceGenBridge extends OverrideHarnessBinder({
  (system: TraceGenSystemModuleImp, th: HasHarnessSignalReferences, ports: Seq[Data]) =>
    ports.map { case p: Bool => GroundTestBridge(th.harnessClock, p)(system.p) }; Nil
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
        }
        if (core.fp_pipeline != null) core.fp_pipeline.fregfile match {
          case frf: boom.exu.RegisterFileSynthesizable => annotate(MemModelAnnotation(frf.regfile))
        }
      }
      case _ =>
    }
    (Nil, Nil)
  }
})

// Shorthand to register all of the provided bridges above
class WithDefaultFireSimBridges extends Config(
  new WithSerialBridge ++
  new WithNICBridge ++
  new WithUARTBridge ++
  new WithBlockDeviceBridge ++
  new WithFASEDBridge ++
  new WithFireSimMultiCycleRegfile ++
  new WithTracerVBridge ++
  new WithFireSimIOCellModels
)
