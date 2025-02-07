// See LICENSE for license details.

package firechip.chip

import chisel3._

import org.chipsalliance.cde.config.{Config}
import freechips.rocketchip.diplomacy.{LazyModule}
import freechips.rocketchip.subsystem._
import sifive.blocks.devices.uart._
import testchipip.serdes.{DecoupledExternalSyncPhitIO}
import testchipip.tsi.{SerialRAM}

import chipyard.iocell._
import chipyard.iobinders._
import chipyard._
import chipyard.harness._

import firechip.bridgestubs._

import firesim.lib.bridges.{FASEDBridge, CompleteConfig}
import firesim.lib.nasti.{NastiIO, NastiParameters}

object MainMemoryConsts {
  val regionNamePrefix = "MainMemory"
  def globalName(chipId: Int) = s"${regionNamePrefix}_$chipId"
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

class WithTSIBridgeAndHarnessRAMOverSerialTL extends HarnessBinder({
  case (th: FireSim, port: SerialTLPort, chipId: Int) => {
    port.io match {
      case io: DecoupledExternalSyncPhitIO => {
        io.clock_in := th.harnessBinderClock
        val ram = Module(LazyModule(new SerialRAM(port.serdesser, port.params)(port.serdesser.p)).module)
        ram.io.ser.in <> io.out
        io.in <> ram.io.ser.out

        // This assumes that:
        // If ExtMem for the target is defined, then FASED bridge will be attached
        // If FASED bridge is attached, loadmem widget is present
        val hasMainMemory = th.chipParameters(chipId)(ExtMem).isDefined
        val mainMemoryName = Option.when(hasMainMemory)(MainMemoryConsts.globalName(chipId))
        TSIBridge(th.harnessBinderClock, ram.io.tsi.get, mainMemoryName, th.harnessBinderReset.asBool)(th.p)
      }
    }
  }
})

class WithDMIBridge extends HarnessBinder({
  case (th: FireSim, port: DMIPort, chipId: Int) => {
    // This assumes that:
    // If ExtMem for the target is defined, then FASED bridge will be attached
    // If FASED bridge is attached, loadmem widget is present

    val hasMainMemory = th.chipParameters(th.p(MultiChipIdx))(ExtMem).isDefined
    val mainMemoryName = Option.when(hasMainMemory)(MainMemoryConsts.globalName(th.p(MultiChipIdx)))
    val nDMIAddrBits = port.io.dmi.req.bits.addr.getWidth
    DMIBridge(th.harnessBinderClock, port.io, mainMemoryName, th.harnessBinderReset.asBool, nDMIAddrBits)(th.p)
  }
})

class WithNICBridge extends HarnessBinder({
  case (th: FireSim, port: NICPort, chipId: Int) => {
    NICBridge(port.io.clock, port.io.bits)(th.p)
  }
})

class WithUARTBridge extends HarnessBinder({
  case (th: FireSim, port: UARTPort, chipId: Int) =>
    val uartSyncClock = th.harnessClockInstantiator.requestClockMHz("uart_clock", port.freqMHz)
    UARTBridge(uartSyncClock, port.io, th.harnessBinderReset.asBool, port.freqMHz)(th.p)
})

class WithBlockDeviceBridge extends HarnessBinder({
  case (th: FireSim, port: BlockDevicePort, chipId: Int) => {
    BlockDevBridge(port.io.clock, port.io.bits, th.harnessBinderReset.asBool)
  }
})


class WithFASEDBridge extends HarnessBinder({
  case (th: FireSim, port: AXI4MemPort, chipId: Int) => {
    val nastiParams = NastiParameters(port.io.bits.r.bits.data.getWidth,
                                   port.io.bits.ar.bits.addr.getWidth,
                                   port.io.bits.ar.bits.id.getWidth)
    val nastiIo = Wire(new NastiIO(nastiParams))
    AXI4NastiAssigner.toNasti(nastiIo, port.io.bits)
    FASEDBridge(port.io.clock, nastiIo, th.harnessBinderReset.asBool,
      CompleteConfig(
        nastiParams,
        Some(CreateAXI4EdgeSummary(port.edge)),
        Some(MainMemoryConsts.globalName(chipId))))
  }
})

class WithTracerVBridge extends HarnessBinder({
  case (th: FireSim, port: TracePort, chipId: Int) => {
    port.io.traces.map(tileTrace => TracerVBridge(tileTrace)(th.p))
  }
})

class WithCospikeBridge extends HarnessBinder({
  case (th: FireSim, port: TracePort, chipId: Int) => {
    port.io.traces.zipWithIndex.map(t => CospikeBridge(t._1, t._2, port.cosimCfg))
  }
})

class WithSuccessBridge extends HarnessBinder({
  case (th: FireSim, port: SuccessPort, chipId: Int) => {
    GroundTestBridge(th.harnessBinderClock, port.io)
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
