//See LICENSE for license details.

package firesim.firesim

import chisel3._
import chisel3.experimental.annotate
import chisel3.experimental.{DataMirror, Direction}
import chisel3.util.experimental.BoringUtils

import org.chipsalliance.cde.config.{Field, Config, Parameters}
import freechips.rocketchip.diplomacy.{LazyModule}
import freechips.rocketchip.devices.debug.{Debug, HasPeripheryDebug, ExportDebug, DMI, ClockedDMIIO, DebugModuleKey}
import freechips.rocketchip.amba.axi4.{AXI4Bundle}
import freechips.rocketchip.subsystem._
import freechips.rocketchip.tile.{RocketTile}
import freechips.rocketchip.prci.{ClockBundle, ClockBundleParameters}
import freechips.rocketchip.util.{ResetCatchAndSync}
import sifive.blocks.devices.uart._

import testchipip._
import icenet.{CanHavePeripheryIceNIC, SimNetwork, NicLoopback, NICKey, NICIOvonly}

import junctions.{NastiKey, NastiParameters}
import midas.models.{FASEDBridge, AXI4EdgeSummary, CompleteConfig}
import midas.targetutils.{MemModelAnnotation, EnableModelMultiThreadingAnnotation}
import firesim.bridges._
import firesim.configs.MemModelKey
import tracegen.{TraceGenSystemModuleImp}
import cva6.CVA6Tile

import boom.common.{BoomTile}
import barstools.iocell.chisel._
import chipyard.iobinders.{IOBinders, OverrideIOBinder, ComposeIOBinder, GetSystemParameters, IOCellKey, JTAGChipIO}
import chipyard._
import chipyard.harness._

object MainMemoryConsts {
  val regionNamePrefix = "MainMemory"
  def globalName()(implicit p: Parameters) = s"${regionNamePrefix}_${p(MultiChipIdx)}"
}

trait Unsupported {
  require(false, "We do not support this IOCell type")
}

class FireSimAnalogIOCell extends RawModule with AnalogIOCell with Unsupported {
  val io = IO(new AnalogIOCellBundle)
}
class FireSimDigitalGPIOCell extends RawModule with DigitalGPIOCell with Unsupported {
  val io = IO(new DigitalGPIOCellBundle)
}
class FireSimDigitalInIOCell extends RawModule with DigitalInIOCell {
  val io = IO(new DigitalInIOCellBundle)
  io.i := io.pad
}
class FireSimDigitalOutIOCell extends RawModule with DigitalOutIOCell {
  val io = IO(new DigitalOutIOCellBundle)
  io.pad := io.o
}

case class FireSimIOCellParams() extends IOCellTypeParams {
  def analog() = Module(new FireSimAnalogIOCell)
  def gpio()   = Module(new FireSimDigitalGPIOCell)
  def input()  = Module(new FireSimDigitalInIOCell)
  def output() = Module(new FireSimDigitalOutIOCell)
}

class WithFireSimIOCellModels extends Config((site, here, up) => {
  case IOCellKey => FireSimIOCellParams()
})

class WithTSIBridgeAndHarnessRAMOverSerialTL extends OverrideHarnessBinder({
  (system: CanHavePeripheryTLSerial, th: FireSim, ports: Seq[ClockedIO[SerialIO]]) => {
    ports.map { port =>
      implicit val p = GetSystemParameters(system)
      val bits = port.bits
      port.clock := th.harnessBinderClock
      val ram = TSIHarness.connectRAM(system.serdesser.get, bits, th.harnessBinderReset)
      TSIBridge(th.harnessBinderClock, ram.module.io.tsi, p(ExtMem).map(_ => MainMemoryConsts.globalName), th.harnessBinderReset.asBool)
    }
    Nil
  }
})

class WithDMIBridge extends OverrideHarnessBinder({
  (system: HasPeripheryDebug, th: FireSim, ports: Seq[Data]) => {
    implicit val p = GetSystemParameters(system)
    ports.map {
      case d: ClockedDMIIO =>
        DMIBridge(th.harnessBinderClock, d, p(ExtMem).map(_ => MainMemoryConsts.globalName), th.harnessBinderReset.asBool, p(DebugModuleKey).get.nDMIAddrSize)
      // Required: Do not support debug module w. JTAG until FIRRTL stops emitting @(posedge ~clock)
      case j: JTAGChipIO => require(false)
    }
    Nil
  }
})

class WithNICBridge extends OverrideHarnessBinder({
  (system: CanHavePeripheryIceNIC, th: FireSim, ports: Seq[ClockedIO[NICIOvonly]]) => {
    val p: Parameters = GetSystemParameters(system)
    ports.map { n => NICBridge(n.clock, n.bits)(p) }
    Nil
  }
})

class WithUARTBridge extends OverrideHarnessBinder({
  (system: HasPeripheryUARTModuleImp, th: FireSim, ports: Seq[UARTPortIO]) =>
    val uartSyncClock = Wire(Clock())
    uartSyncClock := false.B.asClock
    val pbusClockNode = system.outer.asInstanceOf[HasTileLinkLocations].locateTLBusWrapper(PBUS).fixedClockNode
    val pbusClock = pbusClockNode.in.head._1.clock
    BoringUtils.bore(pbusClock, Seq(uartSyncClock))
    ports.map { p => UARTBridge(uartSyncClock, p, th.harnessBinderReset.asBool)(system.p) }; Nil
})

class WithBlockDeviceBridge extends OverrideHarnessBinder({
  (system: CanHavePeripheryBlockDevice, th: FireSim, ports: Seq[ClockedIO[BlockDeviceIO]]) => {
    implicit val p: Parameters = GetSystemParameters(system)
    ports.map { b => BlockDevBridge(b.clock, b.bits, th.harnessBinderReset.asBool) }
    Nil
  }
})


class WithFASEDBridge extends OverrideHarnessBinder({
  (system: CanHaveMasterAXI4MemPort, th: FireSim, ports: Seq[ClockedAndResetIO[AXI4Bundle]]) => {
    implicit val p: Parameters = GetSystemParameters(system)
    (ports zip system.memAXI4Node.edges.in).map { case (axi4, edge) =>
      val nastiKey = NastiParameters(axi4.bits.r.bits.data.getWidth,
                                     axi4.bits.ar.bits.addr.getWidth,
                                     axi4.bits.ar.bits.id.getWidth)
      system match {
        case s: BaseSubsystem => FASEDBridge(axi4.clock, axi4.bits, axi4.reset.asBool,
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
  (system: CanHaveTraceIOModuleImp, th: FireSim, ports: Seq[TraceOutputTop]) => {
    ports.map { p => p.traces.map(tileTrace => TracerVBridge(tileTrace)(system.p)) }
    Nil
  }
})

class WithCospikeBridge extends ComposeHarnessBinder({
  (system: CanHaveTraceIOModuleImp, th: FireSim, ports: Seq[TraceOutputTop]) => {
    implicit val p = chipyard.iobinders.GetSystemParameters(system)
    val chipyardSystem = system.asInstanceOf[ChipyardSystemModule[_]].outer.asInstanceOf[ChipyardSystem]
    val tiles = chipyardSystem.tiles
    val cfg = SpikeCosimConfig(
      isa = tiles.headOption.map(_.isaDTS).getOrElse(""),
      vlen = tiles.headOption.map(_.tileParams.core.vLen).getOrElse(0),
      priv = tiles.headOption.map(t => if (t.usingUser) "MSU" else if (t.usingSupervisor) "MS" else "M").getOrElse(""),
      mem0_base = p(ExtMem).map(_.master.base).getOrElse(BigInt(0)),
      mem0_size = p(ExtMem).map(_.master.size).getOrElse(BigInt(0)),
      pmpregions = tiles.headOption.map(_.tileParams.core.nPMPs).getOrElse(0),
      nharts = tiles.size,
      bootrom = chipyardSystem.bootROM.map(_.module.contents.toArray.mkString(" ")).getOrElse(""),
      has_dtm = p(ExportDebug).protocols.contains(DMI) // assume that exposing clockeddmi means we will connect SimDTM
    )
    ports.map { p => p.traces.zipWithIndex.map(t => CospikeBridge(t._1, t._2, cfg)) }
  }
})

class WithTraceGenBridge extends OverrideHarnessBinder({
  (system: TraceGenSystemModuleImp, th: FireSim, ports: Seq[Bool]) =>
    ports.map { p => GroundTestBridge(th.harnessBinderClock, p)(system.p) }; Nil
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

class WithFireSimFAME5 extends ComposeIOBinder({
  (system: HasTilesModuleImp) => {
    system.outer.tiles.map {
      case b: BoomTile =>
        annotate(EnableModelMultiThreadingAnnotation(b.module))
      case r: RocketTile =>
        annotate(EnableModelMultiThreadingAnnotation(r.module))
      case _ => Nil
    }
    (Nil, Nil)
  }
})

// Shorthand to register all of the provided bridges above
class WithDefaultFireSimBridges extends Config(
  new WithTSIBridgeAndHarnessRAMOverSerialTL ++
  new WithDMIBridge ++
  new WithNICBridge ++
  new WithUARTBridge ++
  new WithBlockDeviceBridge ++
  new WithFASEDBridge ++
  new WithFireSimMultiCycleRegfile ++
  new WithFireSimFAME5 ++
  new WithTracerVBridge ++
  new WithFireSimIOCellModels
)

// Shorthand to register all of the provided mmio-only bridges above
class WithDefaultMMIOOnlyFireSimBridges extends Config(
  new WithTSIBridgeAndHarnessRAMOverSerialTL ++
  new WithDMIBridge ++
  new WithUARTBridge ++
  new WithBlockDeviceBridge ++
  new WithFASEDBridge ++
  new WithFireSimMultiCycleRegfile ++
  new WithFireSimFAME5 ++
  new WithFireSimIOCellModels
)
