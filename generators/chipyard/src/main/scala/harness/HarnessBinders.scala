package chipyard.harness

import chisel3._
import chisel3.util._
import chisel3.experimental.{Analog, BaseModule, DataMirror, Direction}

import org.chipsalliance.cde.config.{Field, Config, Parameters}
import freechips.rocketchip.diplomacy.{LazyModule, LazyModuleImpLike}
import freechips.rocketchip.amba.axi4.{AXI4Bundle, AXI4SlaveNode, AXI4MasterNode, AXI4EdgeParameters}
import freechips.rocketchip.devices.debug._
import freechips.rocketchip.jtag.{JTAGIO}
import freechips.rocketchip.system.{SimAXIMem}
import freechips.rocketchip.subsystem._
import freechips.rocketchip.util._

import sifive.blocks.devices.gpio._
import sifive.blocks.devices.uart._
import sifive.blocks.devices.spi._

import barstools.iocell.chisel._

import testchipip._

import chipyard._
import chipyard.clocking.{HasChipyardPRCI, ClockWithFreq}
import chipyard.iobinders.{GetSystemParameters, JTAGChipIO}

import tracegen.{TraceGenSystemModuleImp}
import icenet.{CanHavePeripheryIceNIC, SimNetwork, NicLoopback, NICKey, NICIOvonly}

import scala.reflect.{ClassTag}

case object HarnessBinders extends Field[HarnessBinderMap](HarnessBinderMapDefault)

object ApplyHarnessBinders {
  def apply(th: HasHarnessInstantiators, sys: LazyModule, portMap: Map[String, Seq[Data]])(implicit p: Parameters): Unit = {
    val pm = portMap.withDefaultValue(Nil)
    p(HarnessBinders).foreach { case (s, f) =>
      f(sys, th, pm(s))
      f(sys.module, th, pm(s))
    }
  }
}

// The ClassTags here are necessary to overcome issues arising from type erasure
class HarnessBinder[T, S <: HasHarnessInstantiators, U <: Data](composer: ((T, S, Seq[U]) => Unit) => (T, S, Seq[U]) => Unit)(implicit systemTag: ClassTag[T], harnessTag: ClassTag[S], portTag: ClassTag[U]) extends Config((site, here, up) => {
  case HarnessBinders => up(HarnessBinders, site) + (systemTag.runtimeClass.toString ->
      ((t: Any, th: HasHarnessInstantiators, ports: Seq[Data]) => {
        val pts = ports.collect({case p: U => p})
        require (pts.length == ports.length, s"Port type mismatch between IOBinder and HarnessBinder: ${portTag}")
        val upfn = up(HarnessBinders, site)(systemTag.runtimeClass.toString)
        (th, t) match {
          case (th: S, system: T) => composer(upfn)(system, th, pts)
          case _ =>
        }
      })
  )
})

class OverrideHarnessBinder[T, S <: HasHarnessInstantiators, U <: Data](fn: => (T, S, Seq[U]) => Unit)
  (implicit tag: ClassTag[T], thtag: ClassTag[S], ptag: ClassTag[U])
    extends HarnessBinder[T, S, U]((upfn: (T, S, Seq[U]) => Unit) => fn)

class ComposeHarnessBinder[T, S <: HasHarnessInstantiators, U <: Data](fn: => (T, S, Seq[U]) => Unit)
  (implicit tag: ClassTag[T], thtag: ClassTag[S], ptag: ClassTag[U])
    extends HarnessBinder[T, S, U]((upfn: (T, S, Seq[U]) => Unit) => (t, th, p) => {
      upfn(t, th, p)
      fn(t, th, p)
    })


class WithGPIOTiedOff extends OverrideHarnessBinder({
  (system: HasPeripheryGPIOModuleImp, th: HasHarnessInstantiators, ports: Seq[Analog]) => {
    ports.foreach { _ <> AnalogConst(0) }
  }
})

// DOC include start: WithUARTAdapter
class WithUARTAdapter extends OverrideHarnessBinder({
  (system: HasPeripheryUARTModuleImp, th: HasHarnessInstantiators, ports: Seq[UARTPortIO]) => {
    UARTAdapter.connect(ports)(system.p)
  }
})
// DOC include end: WithUARTAdapter

class WithSimSPIFlashModel(rdOnly: Boolean = true) extends OverrideHarnessBinder({
  (system: HasPeripherySPIFlashModuleImp, th: HasHarnessInstantiators, ports: Seq[SPIChipIO]) => {
    SimSPIFlashModel.connect(ports, th.harnessBinderReset, rdOnly)(system.p)
  }
})

class WithSimBlockDevice extends OverrideHarnessBinder({
  (system: CanHavePeripheryBlockDevice, th: HasHarnessInstantiators, ports: Seq[ClockedIO[BlockDeviceIO]]) => {
    implicit val p: Parameters = GetSystemParameters(system)
    ports.map { b => SimBlockDevice.connect(b.clock, th.harnessBinderReset.asBool, Some(b.bits)) }
  }
})

class WithBlockDeviceModel extends OverrideHarnessBinder({
  (system: CanHavePeripheryBlockDevice, th: HasHarnessInstantiators, ports: Seq[ClockedIO[BlockDeviceIO]]) => {
    implicit val p: Parameters = GetSystemParameters(system)
    ports.map { b => BlockDeviceModel.connect(Some(b.bits)) }
  }
})

class WithLoopbackNIC extends OverrideHarnessBinder({
  (system: CanHavePeripheryIceNIC, th: HasHarnessInstantiators, ports: Seq[ClockedIO[NICIOvonly]]) => {
    implicit val p: Parameters = GetSystemParameters(system)
    ports.map { n =>
      withClockAndReset(n.clock, th.harnessBinderReset.asBool) {
        NicLoopback.connect(Some(n.bits), p(NICKey))
      }
    }
  }
})

class WithSimNetwork extends OverrideHarnessBinder({
  (system: CanHavePeripheryIceNIC, th: BaseModule with HasHarnessInstantiators, ports: Seq[ClockedIO[NICIOvonly]]) => {
    implicit val p: Parameters = GetSystemParameters(system)
    ports.map { n => SimNetwork.connect(Some(n.bits), n.clock, th.harnessBinderReset.asBool) }
  }
})

class WithSimAXIMem extends OverrideHarnessBinder({
  (system: CanHaveMasterAXI4MemPort, th: HasHarnessInstantiators, ports: Seq[ClockedAndResetIO[AXI4Bundle]]) => {
    val p: Parameters = chipyard.iobinders.GetSystemParameters(system)
    (ports zip system.memAXI4Node.edges.in).map { case (port, edge) =>
      val mem = LazyModule(new SimAXIMem(edge, size=p(ExtMem).get.master.size)(p))
      Module(mem.module).suggestName("mem")
      mem.io_axi4.head <> port.bits
    }
  }
})

class WithBlackBoxSimMem(additionalLatency: Int = 0) extends OverrideHarnessBinder({
  (system: CanHaveMasterAXI4MemPort, th: HasHarnessInstantiators, ports: Seq[ClockedAndResetIO[AXI4Bundle]]) => {
    val p: Parameters = chipyard.iobinders.GetSystemParameters(system)
    (ports zip system.memAXI4Node.edges.in).map { case (port, edge) =>
      // TODO FIX: This currently makes each SimDRAM contain the entire memory space
      val memSize = p(ExtMem).get.master.size
      val memBase = p(ExtMem).get.master.base
      val lineSize = p(CacheBlockBytes)
      val clockFreq = p(MemoryBusKey).dtsFrequency.get
      val mem = Module(new SimDRAM(memSize, lineSize, clockFreq, memBase, edge.bundle)).suggestName("simdram")
      mem.io.axi <> port.bits
      // Bug in Chisel implementation. See https://github.com/chipsalliance/chisel3/pull/1781
      def Decoupled[T <: Data](irr: IrrevocableIO[T]): DecoupledIO[T] = {
        require(DataMirror.directionOf(irr.bits) == Direction.Output, "Only safe to cast produced Irrevocable bits to Decoupled.")
        val d = Wire(new DecoupledIO(chiselTypeOf(irr.bits)))
        d.bits := irr.bits
        d.valid := irr.valid
        irr.ready := d.ready
        d
      }
      if (additionalLatency > 0) {
        withClockAndReset (port.clock, port.reset) {
          mem.io.axi.aw <> (0 until additionalLatency).foldLeft(Decoupled(port.bits.aw))((t, _) => Queue(t, 1, pipe=true))
          mem.io.axi.w  <> (0 until additionalLatency).foldLeft(Decoupled(port.bits.w ))((t, _) => Queue(t, 1, pipe=true))
          port.bits.b   <> (0 until additionalLatency).foldLeft(Decoupled(mem.io.axi.b))((t, _) => Queue(t, 1, pipe=true))
          mem.io.axi.ar <> (0 until additionalLatency).foldLeft(Decoupled(port.bits.ar))((t, _) => Queue(t, 1, pipe=true))
          port.bits.r   <> (0 until additionalLatency).foldLeft(Decoupled(mem.io.axi.r))((t, _) => Queue(t, 1, pipe=true))
        }
      }
      mem.io.clock := port.clock
      mem.io.reset := port.reset
    }
  }
})

class WithSimAXIMMIO extends OverrideHarnessBinder({
  (system: CanHaveMasterAXI4MMIOPort, th: HasHarnessInstantiators, ports: Seq[ClockedAndResetIO[AXI4Bundle]]) => {
    val p: Parameters = chipyard.iobinders.GetSystemParameters(system)
    (ports zip system.mmioAXI4Node.edges.in).map { case (port, edge) =>
      val mmio_mem = LazyModule(new SimAXIMem(edge, size = p(ExtBus).get.size)(p))
      withClockAndReset(port.clock, port.reset) {
        Module(mmio_mem.module).suggestName("mmio_mem")
      }
      mmio_mem.io_axi4.head <> port.bits
    }
  }
})

class WithTieOffInterrupts extends OverrideHarnessBinder({
  (system: HasExtInterruptsModuleImp, th: HasHarnessInstantiators, ports: Seq[UInt]) => {
    ports.foreach { _ := 0.U }
  }
})

class WithTieOffL2FBusAXI extends OverrideHarnessBinder({
  (system: CanHaveSlaveAXI4Port, th: HasHarnessInstantiators, ports: Seq[ClockedIO[AXI4Bundle]]) => {
    ports.foreach({ p =>
      p.bits := DontCare
      p.bits.aw.valid := false.B
      p.bits.w.valid := false.B
      p.bits.b.ready := false.B
      p.bits.ar.valid := false.B
      p.bits.r.ready := false.B
    })
  }
})

class WithSimDebug extends OverrideHarnessBinder({
  (system: HasPeripheryDebug, th: HasHarnessInstantiators, ports: Seq[Data]) => {
    implicit val p: Parameters = GetSystemParameters(system)
    ports.map {
      case d: ClockedDMIIO =>
        val dtm_success = WireInit(false.B)
        when (dtm_success) { th.success := true.B }
        val dtm = Module(new TestchipSimDTM).connect(th.harnessBinderClock, th.harnessBinderReset.asBool, d, dtm_success)
      case j: JTAGChipIO =>
        val dtm_success = WireInit(false.B)
        when (dtm_success) { th.success := true.B }
        val jtag_wire = Wire(new JTAGIO)
        jtag_wire.TDO.data := j.TDO
        jtag_wire.TDO.driven := true.B
        j.TCK := jtag_wire.TCK
        j.TMS := jtag_wire.TMS
        j.TDI := jtag_wire.TDI
        val jtag = Module(new SimJTAG(tickDelay=3))
        jtag.connect(jtag_wire, th.harnessBinderClock, th.harnessBinderReset.asBool, ~(th.harnessBinderReset.asBool), dtm_success)
    }
  }
})

class WithTiedOffDebug extends OverrideHarnessBinder({
  (system: HasPeripheryDebug, th: HasHarnessInstantiators, ports: Seq[Data]) => {
    ports.map {
      case j: JTAGChipIO =>
        j.TCK := true.B.asClock
        j.TMS := true.B
        j.TDI := true.B
      case d: ClockedDMIIO =>
        d.dmi.req.valid := false.B
        d.dmi.req.bits  := DontCare
        d.dmi.resp.ready := true.B
        d.dmiClock := false.B.asClock
        d.dmiReset := true.B
      case a: ClockedAPBBundle =>
        a.pready := false.B
        a.pslverr := false.B
        a.prdata := 0.U
        a.pduser := DontCare
        a.clock := false.B.asClock
        a.reset := true.B.asAsyncReset
        a.psel := false.B
        a.penable := false.B
    }
  }
})


class WithSerialTLTiedOff extends OverrideHarnessBinder({
  (system: CanHavePeripheryTLSerial, th: HasHarnessInstantiators, ports: Seq[ClockedIO[SerialIO]]) => {
    implicit val p = chipyard.iobinders.GetSystemParameters(system)
    ports.map({ port =>
      val bits = port.bits
      if (DataMirror.directionOf(port.clock) == Direction.Input) {
        port.clock := false.B.asClock
      }
      port.bits.out.ready := false.B
      port.bits.in.valid := false.B
      port.bits.in.bits := DontCare
    })
  }
})

class WithSimTSIOverSerialTL extends OverrideHarnessBinder({
  (system: CanHavePeripheryTLSerial, th: HasHarnessInstantiators, ports: Seq[ClockedIO[SerialIO]]) => {
    implicit val p = chipyard.iobinders.GetSystemParameters(system)
    ports.map({ port =>
      val bits = port.bits
      if (DataMirror.directionOf(port.clock) == Direction.Input) {
        port.clock := th.harnessBinderClock
      }
      val ram = TSIHarness.connectRAM(system.serdesser.get, bits, th.harnessBinderReset)
      val success = SimTSI.connect(Some(ram.module.io.tsi), th.harnessBinderClock, th.harnessBinderReset.asBool)
      when (success) { th.success := true.B }
    })
  }
})

class WithSimUARTToUARTTSI extends OverrideHarnessBinder({
  (system: CanHavePeripheryUARTTSI, th: HasHarnessInstantiators, ports: Seq[UARTTSIIO]) => {
    implicit val p = chipyard.iobinders.GetSystemParameters(system)
    require(ports.size <= 1)
    ports.map { port => {
      UARTAdapter.connect(Seq(port.uart),
        baudrate=port.uartParams.initBaudRate,
        clockFrequency=th.getHarnessBinderClockFreqHz.toInt,
        forcePty=true)
      assert(!port.dropped)
    }}
  }
})

class WithSimTSIToUARTTSI extends OverrideHarnessBinder({
  (system: CanHavePeripheryUARTTSI, th: HasHarnessInstantiators, ports: Seq[UARTTSIIO]) => {
    implicit val p = chipyard.iobinders.GetSystemParameters(system)
    require(ports.size <= 1)
    ports.map({ port =>
      val freq = th.getHarnessBinderClockFreqHz.toInt
      val uart_to_serial = Module(new UARTToSerial(freq, port.uartParams))
      val serial_width_adapter = Module(new SerialWidthAdapter(8, TSI.WIDTH))
      val success = SimTSI.connect(Some(TSIIO(serial_width_adapter.io.wide)), th.harnessBinderClock, th.harnessBinderReset)
      when (success) { th.success := true.B }
      assert(!uart_to_serial.io.dropped)
      serial_width_adapter.io.narrow.flipConnect(uart_to_serial.io.serial)
      uart_to_serial.io.uart.rxd := port.uart.txd
      port.uart.rxd := uart_to_serial.io.uart.txd
    })
  }
})


class WithTraceGenSuccess extends OverrideHarnessBinder({
  (system: TraceGenSystemModuleImp, th: HasHarnessInstantiators, ports: Seq[Bool]) => {
    ports.map { p => when (p) { th.success := true.B } }
  }
})

class WithCospike extends ComposeHarnessBinder({
  (system: CanHaveTraceIOModuleImp, th: HasHarnessInstantiators, ports: Seq[TraceOutputTop]) => {
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
    ports.map { p => p.traces.zipWithIndex.map(t => SpikeCosim(t._1, t._2, cfg)) }
  }
})


class WithCustomBootPinPlusArg extends OverrideHarnessBinder({
  (system: CanHavePeripheryCustomBootPin, th: HasHarnessInstantiators, ports: Seq[Bool]) => {
    val pin = PlusArg("custom_boot_pin", width=1)
    ports.foreach(_ := pin)
  }
})


class WithClockAndResetFromHarness extends OverrideHarnessBinder({
  (system: HasChipyardPRCI, th: HasHarnessInstantiators, ports: Seq[Data]) => {
    implicit val p = GetSystemParameters(system)
    val clocks = ports.collect { case c: ClockWithFreq => c }
// DOC include start: HarnessClockInstantiatorEx
    ports.map ({
      case c: ClockWithFreq => {
        val clock = th.harnessClockInstantiator.requestClockMHz(s"clock_${c.freqMHz.toInt}MHz", c.freqMHz)
        c.clock := clock
      }
      case r: AsyncReset => r := th.referenceReset.asAsyncReset
    })
// DOC include end: HarnessClockInstantiatorEx
  }
})
