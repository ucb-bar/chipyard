package chipyard.harness

import chisel3._

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

import tracegen.{TraceGenSystemModuleImp}
import icenet.{CanHavePeripheryIceNICModuleImp, SimNetwork, NicLoopback, NICKey, NICIOvonly}

import scala.reflect.{ClassTag}

case object HarnessBinders extends Field[Map[String, (Any, HasHarnessSignalReferences, Seq[Data]) => Seq[Any]]](
  Map[String, (Any, HasHarnessSignalReferences, Seq[Data]) => Seq[Any]]().withDefaultValue((t: Any, th: HasHarnessSignalReferences, d: Seq[Data]) => Nil)
)


object ApplyHarnessBinders {
  def apply(th: HasHarnessSignalReferences, sys: LazyModule, map: Map[String, (Any, HasHarnessSignalReferences, Seq[Data]) => Seq[Any]], portMap: Map[String, Seq[Data]]) = {
    val pm = portMap.withDefaultValue(Nil)
    map.map { case (s, f) => f(sys, th, pm(s)) ++ f(sys.module, th, pm(s)) }
  }
}

class OverrideHarnessBinder[T](fn: => (T, HasHarnessSignalReferences, Seq[Data]) => Seq[Any])(implicit tag: ClassTag[T]) extends Config((site, here, up) => {
  case HarnessBinders => up(HarnessBinders, site) + (tag.runtimeClass.toString ->
      ((t: Any, th: HasHarnessSignalReferences, ports: Seq[Data]) => {
        t match {
          case system: T => fn(system, th, ports)
          case _ => Nil
        }
      })
  )
})

class ComposeHarnessBinder[T](fn: => (T, HasHarnessSignalReferences, Seq[Data]) => Seq[Any])(implicit tag: ClassTag[T]) extends Config((site, here, up) => {
  case HarnessBinders => up(HarnessBinders, site) + (tag.runtimeClass.toString ->
      ((t: Any, th: HasHarnessSignalReferences, ports: Seq[Data]) => {
        t match {
          case system: T => up(HarnessBinders, site)(tag.runtimeClass.toString)(system, th, ports) ++ fn(system, th, ports)
          case _ => Nil
        }
      })
  )
})

class WithGPIOTiedOff extends OverrideHarnessBinder({
  (system: HasPeripheryGPIOModuleImp, th: HasHarnessSignalReferences, ports: Seq[Data]) => {
    ports.map { case p: GPIOPortIO => p <> AnalogConst(0) }
    Nil
  }
})

// DOC include start: WithUARTAdapter
class WithUARTAdapter extends OverrideHarnessBinder({
  (system: HasPeripheryUARTModuleImp, th: HasHarnessSignalReferences, ports: Seq[Data]) => {
    UARTAdapter.connect(ports.map(_.asInstanceOf[UARTPortIO]))(system.p)
    Nil
  }
})
// DOC include end: WithUARTAdapter

class WithSimSPIFlashModel(rdOnly: Boolean = true) extends OverrideHarnessBinder({
  (system: HasPeripherySPIFlashModuleImp, th: HasHarnessSignalReferences, ports: Seq[Data]) => {
    SimSPIFlashModel.connect(ports.map(_.asInstanceOf[SPIChipIO]), th.harnessReset, rdOnly)(system.p)
    Nil
  }
})

class WithSimBlockDevice extends OverrideHarnessBinder({
  (system: CanHavePeripheryBlockDeviceModuleImp, th: HasHarnessSignalReferences, ports: Seq[Data]) => {
    val clock = WireInit(false.B.asClock)
    ports.map {
      case p: BlockDeviceIO => SimBlockDevice.connect(clock, th.harnessReset.asBool, Some(p))(system.p)
      case c: Clock => clock := c
    }
    Nil
  }
})

class WithBlockDeviceModel extends OverrideHarnessBinder({
  (system: CanHavePeripheryBlockDeviceModuleImp, th: HasHarnessSignalReferences, ports: Seq[Data]) => {
    val clock = WireInit(false.B.asClock)
    ports.map {
      case p: BlockDeviceIO => withClockAndReset(clock, th.harnessReset) { BlockDeviceModel.connect(Some(p))(system.p) }
      case c: Clock => clock := c
    }
    Nil
  }
})

class WithLoopbackNIC extends OverrideHarnessBinder({
  (system: CanHavePeripheryIceNICModuleImp, th: HasHarnessSignalReferences, ports: Seq[Data]) => {
    val clock = WireInit(false.B.asClock)
    ports.map {
      case p: NICIOvonly => withClockAndReset(clock, th.harnessReset) {
        NicLoopback.connect(Some(p), system.p(NICKey))
      }
      case c: Clock => clock := c
    }
    Nil
  }
})

class WithSimNetwork extends OverrideHarnessBinder({
  (system: CanHavePeripheryIceNICModuleImp, th: HasHarnessSignalReferences, ports: Seq[Data]) => {
    val clock = WireInit(false.B.asClock)
    ports.map {
      case p: NICIOvonly => SimNetwork.connect(Some(p), clock, th.harnessReset.asBool)
      case c: Clock => clock := c
    }
    Nil
  }
})

class WithSimAXIMem extends OverrideHarnessBinder({
  (system: CanHaveMasterAXI4MemPort, th: HasHarnessSignalReferences, ports: Seq[Data]) => {
    val p: Parameters = chipyard.iobinders.GetSystemParameters(system)
    val clock = WireInit(false.B.asClock)
    ports.filter(_.isInstanceOf[Clock]).map { case p: Clock => clock := p }
    val axi4_ports = ports.filter(_.isInstanceOf[AXI4Bundle])
    (axi4_ports zip system.memAXI4Node.edges.in).map { case (port: AXI4Bundle, edge) =>
      val mem = LazyModule(new SimAXIMem(edge, size=p(ExtMem).get.master.size)(p))
      withClockAndReset(clock, th.harnessReset) {
        Module(mem.module).suggestName("mem")
      }
      mem.io_axi4.head <> port
    }
    Nil
  }
})

class WithBlackBoxSimMem extends OverrideHarnessBinder({
  (system: CanHaveMasterAXI4MemPort, th: HasHarnessSignalReferences, ports: Seq[Data]) => {
    val p: Parameters = chipyard.iobinders.GetSystemParameters(system)
    val clock = WireInit(false.B.asClock)
    ports.filter(_.isInstanceOf[Clock]).map { case p: Clock => clock := p }
    val axi4_ports = ports.collect { case p: AXI4Bundle => p }
    (axi4_ports zip system.memAXI4Node.edges.in).map { case (port: AXI4Bundle, edge) =>
      val memSize = p(ExtMem).get.master.size
      val lineSize = p(CacheBlockBytes)
      val mem = Module(new SimDRAM(memSize, lineSize, edge.bundle)).suggestName("simdram")
      mem.io.axi <> port
      mem.io.clock := clock
      mem.io.reset := th.harnessReset
    }
    Nil
  }
})

class WithSimAXIMMIO extends OverrideHarnessBinder({
  (system: CanHaveMasterAXI4MMIOPort, th: HasHarnessSignalReferences, ports: Seq[Data]) => {
    val p: Parameters = chipyard.iobinders.GetSystemParameters(system)
    val clock = WireInit(false.B.asClock)
    ports.filter(_.isInstanceOf[Clock]).map { case p: Clock => clock := p }
    (ports zip system.mmioAXI4Node.edges.in).zipWithIndex.map { case ((port: AXI4Bundle, edge), i) =>
      val mmio_mem = LazyModule(new SimAXIMem(edge, size = 4096)(p))
      withClockAndReset(clock, th.harnessReset) {
        Module(mmio_mem.module).suggestName(s"mmio_mem_${i}")
      }
      mmio_mem.io_axi4.head <> port
    }
    Nil
  }
})

class WithTieOffInterrupts extends OverrideHarnessBinder({
  (system: HasExtInterruptsModuleImp, th: HasHarnessSignalReferences, ports: Seq[Data]) => {
    ports.map { case p: UInt =>  p := 0.U }
    Nil
  }
})

class WithTieOffL2FBusAXI extends OverrideHarnessBinder({
  (system: CanHaveSlaveAXI4Port, th: HasHarnessSignalReferences, ports: Seq[Data]) => {
    ports.map { case p: AXI4Bundle =>
      p := DontCare
      p.tieoff()
    }
    Nil
  }
})

class WithSimDebug extends OverrideHarnessBinder({
  (system: HasPeripheryDebugModuleImp, th: HasHarnessSignalReferences, ports: Seq[Data]) => {
    if (!ports.isEmpty) {
      val dtm_success = Wire(Bool())
      when (dtm_success) { th.success := true.B }
      ports.map {
        case d: ClockedDMIIO =>
          val dtm = Module(new SimDTM()(system.p)).connect(th.harnessClock, th.harnessReset.asBool, d, dtm_success)
        case j: JTAGIO =>
          val jtag = Module(new SimJTAG(tickDelay=3)).connect(j, th.harnessClock, th.harnessReset.asBool, ~(th.harnessReset.asBool), dtm_success)
        case _ =>
          require(false, "We only support DMI or JTAG simulated debug connections")
      }
    }
    Nil
  }
})

class WithTiedOffDebug extends OverrideHarnessBinder({
  (system: HasPeripheryDebugModuleImp, th: HasHarnessSignalReferences, ports: Seq[Data]) => {
    ports.map {
      case d: ClockedDMIIO =>
        d.dmi.req.valid := false.B
        d.dmi.req.bits  := DontCare
        d.dmi.resp.ready := true.B
        d.dmiClock := false.B.asClock
        d.dmiReset := true.B
      case j: JTAGIO =>
        j.TCK := true.B.asClock
        j.TMS := true.B
        j.TDI := true.B
        j.TRSTn.foreach { r => r := true.B }
      case a: ClockedAPBBundle =>
        a.tieoff()
        a.clock := false.B.asClock
        a.reset := true.B.asAsyncReset
        a.psel := false.B
        a.penable := false.B
      case _ => require(false)
    }
    Nil
  }
})

class WithTiedOffSerial extends OverrideHarnessBinder({
  (system: CanHavePeripherySerial, th: HasHarnessSignalReferences, ports: Seq[Data]) => {
    ports.map {
      case p: SerialIO => SerialAdapter.tieoff(Some(p))
      case _ =>
    }
    Nil
  }
})

class WithSimSerial extends OverrideHarnessBinder({
  (system: CanHavePeripherySerial, th: HasHarnessSignalReferences, ports: Seq[Data]) => {
    val serial_clock = WireInit(false.B.asClock)
    ports.map {
      case p: SerialIO =>
        val ser_success = SerialAdapter.connectSimSerial(p, serial_clock, th.harnessReset)
        when (ser_success) { th.success := true.B }
      case c: Clock =>
        serial_clock := c
    }
    Nil
  }
})

class WithTraceGenSuccess extends OverrideHarnessBinder({
  (system: TraceGenSystemModuleImp, th: HasHarnessSignalReferences, ports: Seq[Data]) => {
    ports.map { case p: Bool => when (p) { th.success := true.B } }
    Nil
  }
})

class WithSimDromajoBridge extends ComposeHarnessBinder({
  (system: CanHaveTraceIOModuleImp, th: HasHarnessSignalReferences, ports: Seq[Data]) => {
    ports.map { case p: TraceOutputTop => p.traces.map(tileTrace => SimDromajoBridge(tileTrace)(system.p)) }
    Nil
  }
})
