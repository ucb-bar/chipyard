package chipyard.harness

import chisel3._
import chisel3.experimental.{Analog, BaseModule}

import freechips.rocketchip.config.{Field, Config, Parameters}
import freechips.rocketchip.diplomacy.{LazyModule, LazyModuleImpLike}
import freechips.rocketchip.amba.axi4.{AXI4Bundle, AXI4SlaveNode, AXI4MasterNode, AXI4EdgeParameters}
import freechips.rocketchip.devices.debug._
import freechips.rocketchip.jtag.{JTAGIO}
import freechips.rocketchip.system.{SimAXIMem}
import freechips.rocketchip.subsystem._

import sifive.blocks.devices.gpio._
import sifive.blocks.devices.uart._
import sifive.blocks.devices.spi._

import barstools.iocell.chisel._

import testchipip._

import chipyard.HasHarnessSignalReferences
import chipyard.iobinders.GetSystemParameters

import tracegen.{TraceGenSystemModuleImp}
import icenet.{CanHavePeripheryIceNIC, SimNetwork, NicLoopback, NICKey, NICIOvonly}

import scala.reflect.{ClassTag}

case object HarnessBinders extends Field[Map[String, (Any, HasHarnessSignalReferences, Seq[Data]) => Seq[Any]]](
  Map[String, (Any, HasHarnessSignalReferences, Seq[Data]) => Seq[Any]]().withDefaultValue((t: Any, th: HasHarnessSignalReferences, d: Seq[Data]) => Nil)
)


object ApplyHarnessBinders {
  def apply(th: HasHarnessSignalReferences, sys: LazyModule, portMap: Map[String, Seq[Data]])(implicit p: Parameters) = {
    val pm = portMap.withDefaultValue(Nil)
    p(HarnessBinders).map { case (s, f) => f(sys, th, pm(s)) ++ f(sys.module, th, pm(s)) }
  }
}

class HarnessBinder[T, S <: HasHarnessSignalReferences, U <: Data](composer: ((T, S, Seq[U]) => Seq[Any]) => (T, S, Seq[U]) => Seq[Any])(implicit tag: ClassTag[T], thtag: ClassTag[S], ptag: ClassTag[U]) extends Config((site, here, up) => {
  case HarnessBinders => up(HarnessBinders, site) + (tag.runtimeClass.toString ->
      ((t: Any, th: HasHarnessSignalReferences, ports: Seq[Data]) => {
        val pts = ports.collect({case p: U => p})
        require (pts.length == ports.length, s"Port type mismatch between IOBinder and HarnessBinder: ${ptag}")
        val upfn = up(HarnessBinders, site)(tag.runtimeClass.toString)
        th match {
          case th: S =>
            t match {
              case system: T => composer(upfn)(system, th, pts)
              case _ => Nil
            }
          case _ => Nil
        }
      })
  )
})

class OverrideHarnessBinder[T, S <: HasHarnessSignalReferences, U <: Data](fn: => (T, S, Seq[U]) => Seq[Any])
  (implicit tag: ClassTag[T], thtag: ClassTag[S], ptag: ClassTag[U])
    extends HarnessBinder[T, S, U]((upfn: (T, S, Seq[U]) => Seq[Any]) => fn)

class ComposeHarnessBinder[T, S <: HasHarnessSignalReferences, U <: Data](fn: => (T, S, Seq[U]) => Seq[Any])
  (implicit tag: ClassTag[T], thtag: ClassTag[S], ptag: ClassTag[U])
    extends HarnessBinder[T, S, U]((upfn: (T, S, Seq[U]) => Seq[Any]) => (t, th, p) => upfn(t, th, p) ++ fn(t, th, p))


class WithGPIOTiedOff extends OverrideHarnessBinder({
  (system: HasPeripheryGPIOModuleImp, th: BaseModule with HasHarnessSignalReferences, ports: Seq[Analog]) => {
    ports.foreach { _ <> AnalogConst(0) }
    Nil
  }
})

// DOC include start: WithUARTAdapter
class WithUARTAdapter extends OverrideHarnessBinder({
  (system: HasPeripheryUARTModuleImp, th: BaseModule with HasHarnessSignalReferences, ports: Seq[UARTPortIO]) => {
    UARTAdapter.connect(ports)(system.p)
    Nil
  }
})
// DOC include end: WithUARTAdapter

class WithSimSPIFlashModel(rdOnly: Boolean = true) extends OverrideHarnessBinder({
  (system: HasPeripherySPIFlashModuleImp, th: BaseModule with HasHarnessSignalReferences, ports: Seq[SPIChipIO]) => {
    SimSPIFlashModel.connect(ports, th.harnessReset, rdOnly)(system.p)
    Nil
  }
})

class WithSimBlockDevice extends OverrideHarnessBinder({
  (system: CanHavePeripheryBlockDevice, th: BaseModule with HasHarnessSignalReferences, ports: Seq[ClockedIO[BlockDeviceIO]]) => {
    implicit val p: Parameters = GetSystemParameters(system)
    ports.map { b => SimBlockDevice.connect(b.clock, th.harnessReset.asBool, Some(b.bits)) }
    Nil
  }
})

class WithBlockDeviceModel extends OverrideHarnessBinder({
  (system: CanHavePeripheryBlockDevice, th: BaseModule with HasHarnessSignalReferences, ports: Seq[ClockedIO[BlockDeviceIO]]) => {
    implicit val p: Parameters = GetSystemParameters(system)
    ports.map { b => withClockAndReset(b.clock, th.harnessReset) { BlockDeviceModel.connect(Some(b.bits)) } }
    Nil
  }
})

class WithLoopbackNIC extends OverrideHarnessBinder({
  (system: CanHavePeripheryIceNIC, th: BaseModule with HasHarnessSignalReferences, ports: Seq[ClockedIO[NICIOvonly]]) => {
    implicit val p: Parameters = GetSystemParameters(system)
    ports.map { n =>
      withClockAndReset(n.clock, th.harnessReset) {
        NicLoopback.connect(Some(n.bits), p(NICKey))
      }
    }
    Nil
  }
})

class WithSimNetwork extends OverrideHarnessBinder({
  (system: CanHavePeripheryIceNIC, th: BaseModule with HasHarnessSignalReferences, ports: Seq[ClockedIO[NICIOvonly]]) => {
    implicit val p: Parameters = GetSystemParameters(system)
    ports.map { n => SimNetwork.connect(Some(n.bits), n.clock, th.harnessReset.asBool) }
    Nil
  }
})

class WithSimAXIMem extends OverrideHarnessBinder({
  (system: CanHaveMasterAXI4MemPort, th: BaseModule with HasHarnessSignalReferences, ports: Seq[ClockedIO[AXI4Bundle]]) => {
    val p: Parameters = chipyard.iobinders.GetSystemParameters(system)
    (ports zip system.memAXI4Node.edges.in).map { case (port, edge) =>
      val mem = LazyModule(new SimAXIMem(edge, size=p(ExtMem).get.master.size)(p))
      withClockAndReset(port.clock, th.harnessReset) {
        Module(mem.module).suggestName("mem")
      }
      mem.io_axi4.head <> port.bits
    }
    Nil
  }
})

class WithBlackBoxSimMem extends OverrideHarnessBinder({
  (system: CanHaveMasterAXI4MemPort, th: BaseModule with HasHarnessSignalReferences, ports: Seq[ClockedIO[AXI4Bundle]]) => {
    val p: Parameters = chipyard.iobinders.GetSystemParameters(system)
    (ports zip system.memAXI4Node.edges.in).map { case (port, edge) =>
      val memSize = p(ExtMem).get.master.size
      val lineSize = p(CacheBlockBytes)
      val mem = Module(new SimDRAM(memSize, lineSize, edge.bundle)).suggestName("simdram")
      mem.io.axi <> port.bits
      mem.io.clock := port.clock
      mem.io.reset := th.harnessReset
    }
    Nil
  }
})

class WithSimAXIMMIO extends OverrideHarnessBinder({
  (system: CanHaveMasterAXI4MMIOPort, th: BaseModule with HasHarnessSignalReferences, ports: Seq[ClockedIO[AXI4Bundle]]) => {
    val p: Parameters = chipyard.iobinders.GetSystemParameters(system)
    (ports zip system.mmioAXI4Node.edges.in).map { case (port, edge) =>
      val mmio_mem = LazyModule(new SimAXIMem(edge, size = p(ExtBus).get.size)(p))
      withClockAndReset(port.clock, th.harnessReset) {
        Module(mmio_mem.module).suggestName("mmio_mem")
      }
      mmio_mem.io_axi4.head <> port.bits
    }
    Nil
  }
})

class WithTieOffInterrupts extends OverrideHarnessBinder({
  (system: HasExtInterruptsModuleImp, th: BaseModule with HasHarnessSignalReferences, ports: Seq[UInt]) => {
    ports.foreach { _ := 0.U }
    Nil
  }
})

class WithTieOffL2FBusAXI extends OverrideHarnessBinder({
  (system: CanHaveSlaveAXI4Port, th: BaseModule with HasHarnessSignalReferences, ports: Seq[ClockedIO[AXI4Bundle]]) => {
    ports.foreach({ p => p := DontCare; p.bits.tieoff() })
    Nil
  }
})

class WithSimDebug extends OverrideHarnessBinder({
  (system: HasPeripheryDebugModuleImp, th: BaseModule with HasHarnessSignalReferences, ports: Seq[Data]) => {
    ports.map {
      case d: ClockedDMIIO =>
        val dtm_success = WireInit(false.B)
        when (dtm_success) { th.success := true.B }
        val dtm = Module(new SimDTM()(system.p)).connect(th.harnessClock, th.harnessReset.asBool, d, dtm_success)
      case j: JTAGIO =>
        val dtm_success = WireInit(false.B)
        when (dtm_success) { th.success := true.B }
        val jtag = Module(new SimJTAG(tickDelay=3)).connect(j, th.harnessClock, th.harnessReset.asBool, ~(th.harnessReset.asBool), dtm_success)
    }
    Nil
  }
})

class WithTiedOffDebug extends OverrideHarnessBinder({
  (system: HasPeripheryDebugModuleImp, th: BaseModule with HasHarnessSignalReferences, ports: Seq[Data]) => {
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
    Nil
  }
})


class WithSerialAdapterTiedOff extends OverrideHarnessBinder({
  (system: CanHavePeripheryTLSerial, th: BaseModule with HasHarnessSignalReferences, ports: Seq[ClockedIO[SerialIO]]) => {
    implicit val p = chipyard.iobinders.GetSystemParameters(system)
    ports.map({ port =>
      val ram = SerialAdapter.connectHarnessRAM(system.serdesser.get, port, th.harnessReset)
      SerialAdapter.tieoff(ram.module.io.tsi_ser)
    })
  }
})

class WithSimSerial extends OverrideHarnessBinder({
  (system: CanHavePeripheryTLSerial, th: BaseModule with HasHarnessSignalReferences, ports: Seq[ClockedIO[SerialIO]]) => {
    implicit val p = chipyard.iobinders.GetSystemParameters(system)
    ports.map({ port =>
      val ram = SerialAdapter.connectHarnessRAM(system.serdesser.get, port, th.harnessReset)
      val success = SerialAdapter.connectSimSerial(ram.module.io.tsi_ser, port.clock, th.harnessReset.asBool)
      when (success) { th.success := true.B }
    })
  }
})

class WithTraceGenSuccess extends OverrideHarnessBinder({
  (system: TraceGenSystemModuleImp, th: BaseModule with HasHarnessSignalReferences, ports: Seq[Bool]) => {
    ports.map { p => when (p) { th.success := true.B } }
    Nil
  }
})

class WithSimDromajoBridge extends ComposeHarnessBinder({
  (system: CanHaveTraceIOModuleImp, th: BaseModule with HasHarnessSignalReferences, ports: Seq[TraceOutputTop]) => {
    ports.map { p => p.traces.map(tileTrace => SimDromajoBridge(tileTrace)(system.p)) }
    Nil
  }
})
