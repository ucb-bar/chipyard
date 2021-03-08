package chipyard.harness

import chisel3._
import chisel3.util._
import chisel3.experimental.{Analog, BaseModule, DataMirror, Direction}

import freechips.rocketchip.config.{Field, Config, Parameters}
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

import chipyard.{HasHarnessSignalReferences, HarnessClockInstantiatorKey}
import chipyard.iobinders.GetSystemParameters

import tracegen.{TraceGenSystemModuleImp}
import icenet.{CanHavePeripheryIceNIC, SimNetwork, NicLoopback, NICKey, NICIOvonly}

import scala.reflect.{ClassTag}

case object HarnessBinders extends Field[Map[String, (Any, HasHarnessSignalReferences, Seq[Data]) => Unit]](
  Map[String, (Any, HasHarnessSignalReferences, Seq[Data]) => Unit]().withDefaultValue((t: Any, th: HasHarnessSignalReferences, d: Seq[Data]) => ())
)

object ApplyHarnessBinders {
  def apply(th: HasHarnessSignalReferences, sys: LazyModule, portMap: Map[String, Seq[Data]])(implicit p: Parameters): Unit = {
    val pm = portMap.withDefaultValue(Nil)
    p(HarnessBinders).foreach { case (s, f) =>
      f(sys, th, pm(s))
      f(sys.module, th, pm(s))
    }
  }
}

// The ClassTags here are necessary to overcome issues arising from type erasure
class HarnessBinder[T, S <: HasHarnessSignalReferences, U <: Data](composer: ((T, S, Seq[U]) => Unit) => (T, S, Seq[U]) => Unit)(implicit systemTag: ClassTag[T], harnessTag: ClassTag[S], portTag: ClassTag[U]) extends Config((site, here, up) => {
  case HarnessBinders => up(HarnessBinders, site) + (systemTag.runtimeClass.toString ->
      ((t: Any, th: HasHarnessSignalReferences, ports: Seq[Data]) => {
        val pts = ports.collect({case p: U => p})
        require (pts.length == ports.length, s"Port type mismatch between IOBinder and HarnessBinder: ${portTag}")
        val upfn = up(HarnessBinders, site)(systemTag.runtimeClass.toString)
        th match {
          case th: S =>
            t match {
              case system: T => composer(upfn)(system, th, pts)
              case _ =>
            }
          case _ =>
        }
      })
  )
})

class OverrideHarnessBinder[T, S <: HasHarnessSignalReferences, U <: Data](fn: => (T, S, Seq[U]) => Unit)
  (implicit tag: ClassTag[T], thtag: ClassTag[S], ptag: ClassTag[U])
    extends HarnessBinder[T, S, U]((upfn: (T, S, Seq[U]) => Unit) => fn)

class ComposeHarnessBinder[T, S <: HasHarnessSignalReferences, U <: Data](fn: => (T, S, Seq[U]) => Unit)
  (implicit tag: ClassTag[T], thtag: ClassTag[S], ptag: ClassTag[U])
    extends HarnessBinder[T, S, U]((upfn: (T, S, Seq[U]) => Unit) => (t, th, p) => {
      upfn(t, th, p)
      fn(t, th, p)
    })


class WithGPIOTiedOff extends OverrideHarnessBinder({
  (system: HasPeripheryGPIOModuleImp, th: HasHarnessSignalReferences, ports: Seq[Analog]) => {
    ports.foreach { _ <> AnalogConst(0) }
  }
})

// DOC include start: WithUARTAdapter
class WithUARTAdapter extends OverrideHarnessBinder({
  (system: HasPeripheryUARTModuleImp, th: HasHarnessSignalReferences, ports: Seq[UARTPortIO]) => {
    UARTAdapter.connect(ports)(system.p)
  }
})
// DOC include end: WithUARTAdapter

class WithSimSPIFlashModel(rdOnly: Boolean = true) extends OverrideHarnessBinder({
  (system: HasPeripherySPIFlashModuleImp, th: HasHarnessSignalReferences, ports: Seq[SPIChipIO]) => {
    SimSPIFlashModel.connect(ports, th.harnessReset, rdOnly)(system.p)
  }
})

class WithSimBlockDevice extends OverrideHarnessBinder({
  (system: CanHavePeripheryBlockDevice, th: HasHarnessSignalReferences, ports: Seq[ClockedIO[BlockDeviceIO]]) => {
    implicit val p: Parameters = GetSystemParameters(system)
    ports.map { b => SimBlockDevice.connect(b.clock, th.harnessReset.asBool, Some(b.bits)) }
  }
})

class WithBlockDeviceModel extends OverrideHarnessBinder({
  (system: CanHavePeripheryBlockDevice, th: HasHarnessSignalReferences, ports: Seq[ClockedIO[BlockDeviceIO]]) => {
    implicit val p: Parameters = GetSystemParameters(system)
    ports.map { b => withClockAndReset(b.clock, th.harnessReset) { BlockDeviceModel.connect(Some(b.bits)) } }
  }
})

class WithLoopbackNIC extends OverrideHarnessBinder({
  (system: CanHavePeripheryIceNIC, th: HasHarnessSignalReferences, ports: Seq[ClockedIO[NICIOvonly]]) => {
    implicit val p: Parameters = GetSystemParameters(system)
    ports.map { n =>
      withClockAndReset(n.clock, th.harnessReset) {
        NicLoopback.connect(Some(n.bits), p(NICKey))
      }
    }
  }
})

class WithSimNetwork extends OverrideHarnessBinder({
  (system: CanHavePeripheryIceNIC, th: BaseModule with HasHarnessSignalReferences, ports: Seq[ClockedIO[NICIOvonly]]) => {
    implicit val p: Parameters = GetSystemParameters(system)
    ports.map { n => SimNetwork.connect(Some(n.bits), n.clock, th.harnessReset.asBool) }
  }
})

class WithSimAXIMem extends OverrideHarnessBinder({
  (system: CanHaveMasterAXI4MemPort, th: HasHarnessSignalReferences, ports: Seq[ClockedAndResetIO[AXI4Bundle]]) => {
    val p: Parameters = chipyard.iobinders.GetSystemParameters(system)
    (ports zip system.memAXI4Node.edges.in).map { case (port, edge) =>
      val mem = LazyModule(new SimAXIMem(edge, size=p(ExtMem).get.master.size)(p))
      withClockAndReset(port.clock, port.reset) {
        Module(mem.module).suggestName("mem")
      }
      mem.io_axi4.head <> port.bits
    }
  }
})

class WithSimAXIMemOverSerialTL extends OverrideHarnessBinder({
  (system: CanHavePeripheryTLSerial, th: HasHarnessSignalReferences, ports: Seq[ClockedIO[SerialIO]]) => {
    implicit val p = chipyard.iobinders.GetSystemParameters(system)

    p(SerialTLKey).map({ sVal =>
      // currently only the harness AXI port supports a passthrough clock
      require(sVal.axiMemOverSerialTLParams.isDefined)
      val axiDomainParams = sVal.axiMemOverSerialTLParams.get

      val memFreq: Double = axiDomainParams.axiClockParams match {
        case Some(clkParams) => clkParams.clockFreqMHz * 1000000
        case None => {
          // get freq. from what the master of the serial link specifies
          system.asInstanceOf[HasTileLinkLocations].locateTLBusWrapper(p(SerialTLAttachKey).masterWhere).dtsFrequency.get.toDouble
        }
      }

      ports.map({ port =>
        val harnessMultiClockAXIRAM = SerialAdapter.connectHarnessMultiClockAXIRAM(
          system.serdesser.get,
          port,
          p(HarnessClockInstantiatorKey).getClockBundle("mem_over_serial_tl_clock", memFreq),
          th.harnessReset)
        val success = SerialAdapter.connectSimSerial(harnessMultiClockAXIRAM.module.io.tsi_ser, port.clock, th.harnessReset.asBool)
        when (success) { th.success := true.B }

        // connect SimDRAM from the AXI port coming from the harness multi clock axi ram
        (harnessMultiClockAXIRAM.mem_axi4 zip harnessMultiClockAXIRAM.memNode.edges.in).map { case (axi_port, edge) =>
          val memSize = sVal.memParams.size
          val lineSize = p(CacheBlockBytes)
          val mem = Module(new SimDRAM(memSize, lineSize, BigInt(memFreq.toInt), edge.bundle)).suggestName("simdram")
          mem.io.axi <> axi_port.bits
          mem.io.clock := axi_port.clock
          mem.io.reset := axi_port.reset
        }
      })
    })
  }
})

class WithBlackBoxSimMem(additionalLatency: Int = 0) extends OverrideHarnessBinder({
  (system: CanHaveMasterAXI4MemPort, th: HasHarnessSignalReferences, ports: Seq[ClockedAndResetIO[AXI4Bundle]]) => {
    val p: Parameters = chipyard.iobinders.GetSystemParameters(system)
    (ports zip system.memAXI4Node.edges.in).map { case (port, edge) =>
      val memSize = p(ExtMem).get.master.size
      val lineSize = p(CacheBlockBytes)
      val clockFreq = p(MemoryBusKey).dtsFrequency.get
      val mem = Module(new SimDRAM(memSize, lineSize, clockFreq, edge.bundle)).suggestName("simdram")
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
  (system: CanHaveMasterAXI4MMIOPort, th: HasHarnessSignalReferences, ports: Seq[ClockedAndResetIO[AXI4Bundle]]) => {
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
  (system: HasExtInterruptsModuleImp, th: HasHarnessSignalReferences, ports: Seq[UInt]) => {
    ports.foreach { _ := 0.U }
  }
})

class WithTieOffL2FBusAXI extends OverrideHarnessBinder({
  (system: CanHaveSlaveAXI4Port, th: HasHarnessSignalReferences, ports: Seq[ClockedIO[AXI4Bundle]]) => {
    ports.foreach({ p => p := DontCare; p.bits.tieoff() })
  }
})

class WithSimDebug extends OverrideHarnessBinder({
  (system: HasPeripheryDebug, th: HasHarnessSignalReferences, ports: Seq[Data]) => {
    implicit val p: Parameters = GetSystemParameters(system)
    ports.map {
      case d: ClockedDMIIO =>
        val dtm_success = WireInit(false.B)
        when (dtm_success) { th.success := true.B }
        val dtm = Module(new SimDTM).connect(th.harnessClock, th.harnessReset.asBool, d, dtm_success)
      case j: JTAGIO =>
        val dtm_success = WireInit(false.B)
        when (dtm_success) { th.success := true.B }
        val jtag = Module(new SimJTAG(tickDelay=3)).connect(j, th.harnessClock, th.harnessReset.asBool, ~(th.harnessReset.asBool), dtm_success)
    }
  }
})

class WithTiedOffDebug extends OverrideHarnessBinder({
  (system: HasPeripheryDebug, th: HasHarnessSignalReferences, ports: Seq[Data]) => {
    ports.map {
      case j: JTAGIO =>
        j.TCK := true.B.asClock
        j.TMS := true.B
        j.TDI := true.B
        j.TRSTn.foreach { r => r := true.B }
      case d: ClockedDMIIO =>
        d.dmi.req.valid := false.B
        d.dmi.req.bits  := DontCare
        d.dmi.resp.ready := true.B
        d.dmiClock := false.B.asClock
        d.dmiReset := true.B
      case a: ClockedAPBBundle =>
        a.tieoff()
        a.clock := false.B.asClock
        a.reset := true.B.asAsyncReset
        a.psel := false.B
        a.penable := false.B
    }
  }
})


class WithSerialAdapterTiedOff extends OverrideHarnessBinder({
  (system: CanHavePeripheryTLSerial, th: HasHarnessSignalReferences, ports: Seq[ClockedIO[SerialIO]]) => {
    implicit val p = chipyard.iobinders.GetSystemParameters(system)
    ports.map({ port =>
      val ram = SerialAdapter.connectHarnessRAM(system.serdesser.get, port, th.harnessReset)
      SerialAdapter.tieoff(ram.module.io.tsi_ser)
    })
  }
})

class WithSimSerial extends OverrideHarnessBinder({
  (system: CanHavePeripheryTLSerial, th: HasHarnessSignalReferences, ports: Seq[ClockedIO[SerialIO]]) => {
    implicit val p = chipyard.iobinders.GetSystemParameters(system)
    ports.map({ port =>
      val ram = SerialAdapter.connectHarnessRAM(system.serdesser.get, port, th.harnessReset)
      val success = SerialAdapter.connectSimSerial(ram.module.io.tsi_ser, port.clock, th.harnessReset.asBool)
      when (success) { th.success := true.B }
    })
  }
})

class WithTraceGenSuccess extends OverrideHarnessBinder({
  (system: TraceGenSystemModuleImp, th: HasHarnessSignalReferences, ports: Seq[Bool]) => {
    ports.map { p => when (p) { th.success := true.B } }
  }
})

class WithSimDromajoBridge extends ComposeHarnessBinder({
  (system: CanHaveTraceIOModuleImp, th: HasHarnessSignalReferences, ports: Seq[TraceOutputTop]) => {
    ports.map { p => p.traces.map(tileTrace => SimDromajoBridge(tileTrace)(system.p)) }
  }
})
