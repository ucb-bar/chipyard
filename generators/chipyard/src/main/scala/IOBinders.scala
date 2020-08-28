package chipyard
package object iobinders {

import chisel3._
import chisel3.experimental.{Analog, IO}

import freechips.rocketchip.config.{Field, Config, Parameters}
import freechips.rocketchip.diplomacy.{LazyModule, LazyModuleImpLike}
import freechips.rocketchip.devices.debug._
import freechips.rocketchip.jtag.{JTAGIO}
import freechips.rocketchip.subsystem._
import freechips.rocketchip.system.{SimAXIMem}
import freechips.rocketchip.amba.axi4.{AXI4Bundle, AXI4SlaveNode, AXI4MasterNode, AXI4EdgeParameters}
import freechips.rocketchip.util._
import freechips.rocketchip.groundtest.{GroundTestSubsystemModuleImp, GroundTestSubsystem}

import sifive.blocks.devices.gpio._
import sifive.blocks.devices.uart._
import sifive.blocks.devices.spi._
import tracegen.{TraceGenSystemModuleImp}

import barstools.iocell.chisel._

import testchipip._
import icenet.{CanHavePeripheryIceNICModuleImp, SimNetwork, NicLoopback, NICKey}

import scala.reflect.{ClassTag}

// System for instantiating binders based
// on the scala type of the Target (_not_ its IO). This avoids needing to
// duplicate harnesses (essentially test harnesses) for each target.

// IOBinders is map between string representations of traits to the desired
// IO connection behavior for tops matching that trait. We use strings to enable
// composition and overriding of IOBinders, much like how normal Keys in the config
// system are used/ At elaboration, the testharness traverses this set of functions,
// and functions which match the type of the DigitalTop are evaluated.

// You can add your own binder by adding a new (key, fn) pair, typically by using
// the OverrideIOBinder or ComposeIOBinder macros


// DOC include start: IOBinders
// This type describes a function callable on the TestHarness instance. Its return type is unused.
type TestHarnessFunction = (chipyard.HasHarnessSignalReferences) => Seq[Any]
// IOBinders will return a Seq of this tuple, which contains three fields:
//  1. A Seq containing all IO ports created by the IOBinder function
//  2. A Seq containing all IO cell modules created by the IOBinder function
//  3. An optional function to call inside the test harness (e.g. to connect the IOs)
type IOBinderTuple = (Seq[Data], Seq[IOCell], Option[TestHarnessFunction])

case object IOBinders extends Field[Map[String, (Any) => Seq[IOBinderTuple]]](
  Map[String, (Any) => Seq[IOBinderTuple]]().withDefaultValue((Any) => Nil)
)

// Note: The parameters instance is accessible only through LazyModule
// or LazyModuleImpLike. The self-type requirement in traits like
// CanHaveMasterAXI4MemPort is insufficient to make it accessible to the IOBinder
// As a result, IOBinders only work on Modules which inherit LazyModule or
// or LazyModuleImpLike
object GetSystemParameters {
  def apply(s: Any): Parameters = {
    s match {
      case s: LazyModule => s.p
      case s: LazyModuleImpLike => s.p
      case _ => throw new Exception(s"Trying to get Parameters from a system that is not LazyModule or LazyModuleImpLike")
    }
  }
}

// This macro overrides previous matches on some Top mixin. This is useful for
// binders which drive IO, since those typically cannot be composed
class OverrideIOBinder[T](fn: => (T) => Seq[IOBinderTuple])(implicit tag: ClassTag[T]) extends Config((site, here, up) => {
  case IOBinders => up(IOBinders, site) + (tag.runtimeClass.toString ->
      ((t: Any) => {
        t match {
          case system: T => fn(system)
          case _ => Nil
        }
      })
  )
})

// This macro composes with previous matches on some Top mixin. This is useful for
// annotation-like binders, since those can typically be composed
class ComposeIOBinder[T](fn: => (T) => Seq[IOBinderTuple])(implicit tag: ClassTag[T]) extends Config((site, here, up) => {
  case IOBinders => up(IOBinders, site) + (tag.runtimeClass.toString ->
      ((t: Any) => {
        t match {
          case system: T => (up(IOBinders, site)(tag.runtimeClass.toString)(system)
            ++ fn(system))
          case _ => Nil
        }
      })
  )
})

// DOC include end: IOBinders

object AddIOCells {
  /**
   * Add IO cells to a SiFive GPIO devices and name the IO ports.
   * @param gpios A Seq of GPIO port bundles
   * @param genFn A callable function to generate a DigitalGPIOCell module to use
   * @return Returns a tuple of (a 2D Seq of Analog IOs corresponding to individual GPIO pins; a 2D Seq of IOCell module references)
   */
  def gpio(gpios: Seq[GPIOPortIO], genFn: () => DigitalGPIOCell = IOCell.genericGPIO): (Seq[Seq[Analog]], Seq[Seq[IOCell]]) = {
    gpios.zipWithIndex.map({ case (gpio, i) =>
      gpio.pins.zipWithIndex.map({ case (pin, j) =>
        val g = IO(Analog(1.W)).suggestName(s"gpio_${i}_${j}")
        val iocell = genFn().suggestName(s"iocell_gpio_${i}_${j}")
        iocell.io.o := pin.o.oval
        iocell.io.oe := pin.o.oe
        iocell.io.ie := pin.o.ie
        pin.i.ival := iocell.io.i
        iocell.io.pad <> g
        (g, iocell)
      }).unzip
    }).unzip
  }

  /**
   * Add IO cells to a SiFive UART devices and name the IO ports.
   * @param uartPins A Seq of UART port bundles
   * @return Returns a tuple of (A Seq of top-level UARTPortIO IOs; a 2D Seq of IOCell module references)
   */
  def uart(uartPins: Seq[UARTPortIO]): (Seq[UARTPortIO], Seq[Seq[IOCell]]) = {
    uartPins.zipWithIndex.map({ case (u, i) =>
      val (port, ios) = IOCell.generateIOFromSignal(u, Some(s"iocell_uart_${i}"))
      port.suggestName(s"uart_${i}")
      (port, ios)
    }).unzip
  }

  /**
   * Add IO cells to a SiFive SPI devices and name the IO ports.
   * @param spiPins A Seq of SPI port bundles
   * @param basename The base name for this port (defaults to "spi")
   * @param genFn A callable function to generate a DigitalGPIOCell module to use
   * @return Returns a tuple of (A Seq of top-level SPIChipIO IOs; a 2D Seq of IOCell module references)
   */
  def spi(spiPins: Seq[SPIPortIO], basename: String = "spi", genFn: () => DigitalGPIOCell = IOCell.genericGPIO): (Seq[SPIChipIO], Seq[Seq[IOCell]]) = {
    spiPins.zipWithIndex.map({ case (s, i) =>
      val port = IO(new SPIChipIO(s.c.csWidth)).suggestName(s"${basename}_${i}")
      val iocellBase = s"iocell_${basename}_${i}"

      // SCK and CS are unidirectional outputs
      val sckIOs = IOCell.generateFromSignal(s.sck, port.sck, Some(s"${iocellBase}_sck"))
      val csIOs = IOCell.generateFromSignal(s.cs, port.cs, Some(s"${iocellBase}_cs"))

      // DQ are bidirectional, so then need special treatment
      val dqIOs = s.dq.zip(port.dq).zipWithIndex.map { case ((pin, ana), j) =>
        val iocell = genFn().suggestName(s"${iocellBase}_dq_${j}")
        iocell.io.o := pin.o
        iocell.io.oe := pin.oe
        iocell.io.ie := true.B
        pin.i := iocell.io.i
        iocell.io.pad <> ana
        iocell
      }

      (port, dqIOs ++ csIOs ++ sckIOs)
    }).unzip
  }

  /**
   * Add IO cells to a debug module and name the IO ports, for debug IO which must go off-chip
   * For on-chip debug IO, drive them appropriately
   * @param system A BaseSubsystem that might have a debug module
   * @param resetctrlOpt An optional ResetCtrlIO bundle
   * @param debugOpt An optional DebugIO bundle
   * @return Returns a tuple2 of (Generated debug io ports, Generated IOCells)
   */
  def debug(system: HasPeripheryDebugModuleImp)(implicit p: Parameters): (Seq[Bundle], Seq[IOCell]) = {
    system.debug.map { debug =>

      // We never use the PSDIO, so tie it off on-chip
      system.psd.psd.foreach { _ <> 0.U.asTypeOf(new PSDTestMode) }

      // Set resetCtrlOpt with the system reset
      system.resetctrl.map { rcio => rcio.hartIsInReset.map { _ := system.reset.asBool } }

      system.debug.map { d =>
        // Tie off extTrigger
        d.extTrigger.foreach { t =>
          t.in.req := false.B
          t.out.ack := t.out.req
        }

        // Tie off disableDebug
        d.disableDebug.foreach { d => d := false.B }

        // Drive JTAG on-chip IOs
        d.systemjtag.map { j =>
          j.reset := system.reset
          j.mfr_id := system.p(JtagDTMKey).idcodeManufId.U(11.W)
          j.part_number := system.p(JtagDTMKey).idcodePartNum.U(16.W)
          j.version := system.p(JtagDTMKey).idcodeVersion.U(4.W)
        }
      }


      // Connect DebugClockAndReset to system implicit clock. TODO this should use the clock of the bus the debug module is attached to
      Debug.connectDebugClockAndReset(Some(debug), system.clock)(system.p)

      // Add IOCells for the DMI/JTAG/APB ports

      val dmiTuple = debug.clockeddmi.map { d =>
        IOCell.generateIOFromSignal(d, Some("iocell_dmi"), abstractResetAsAsync = p(GlobalResetSchemeKey).pinIsAsync)
      }
      dmiTuple.map(_._1).foreach(_.suggestName("dmi"))

      val jtagTuple = debug.systemjtag.map { j =>
        IOCell.generateIOFromSignal(j.jtag, Some("iocell_jtag"), abstractResetAsAsync = p(GlobalResetSchemeKey).pinIsAsync)
      }
      jtagTuple.map(_._1).foreach(_.suggestName("jtag"))

      val apbTuple = debug.apb.map { a =>
        IOCell.generateIOFromSignal(a, Some("iocell_apb"), abstractResetAsAsync = p(GlobalResetSchemeKey).pinIsAsync)
      }
      apbTuple.map(_._1).foreach(_.suggestName("apb"))

      val allTuples = (dmiTuple ++ jtagTuple ++ apbTuple).toSeq
      (allTuples.map(_._1).toSeq, allTuples.flatMap(_._2).toSeq)
    }.getOrElse((Nil, Nil))
  }

  /**
   * Add IO cells to a serial module and name the IO ports.
   * @param serial A SerialIO bundle
   * @return Returns a tuple of (Top-level SerialIO IO; a list of IOCell module references)
   */
  def serial(serial: SerialIO): (SerialIO, Seq[IOCell]) = {
    val (port, ios) = IOCell.generateIOFromSignal(serial, Some("iocell_serial"))
    port.suggestName("serial")
    (port, ios)
  }

  def axi4(io: Seq[AXI4Bundle], node: AXI4SlaveNode, name: String): Seq[(AXI4Bundle, AXI4EdgeParameters, Seq[IOCell])] = {
    io.zip(node.edges.in).zipWithIndex.map{ case ((mem_axi4, edge), i) => {
      val (port, ios) = IOCell.generateIOFromSignal(mem_axi4, Some(s"iocell_${name}_axi4_slave_${i}"))
      port.suggestName(s"${name}_axi4_slave_${i}")
      (port, edge, ios)
    }}
  }
  def axi4(io: Seq[AXI4Bundle], node: AXI4MasterNode, name: String): Seq[(AXI4Bundle, AXI4EdgeParameters, Seq[IOCell])] = {
    io.zip(node.edges.out).zipWithIndex.map{ case ((mem_axi4, edge), i) => {
      //val (port, ios) = IOCell.generateIOFromSignal(mem_axi4, Some(s"iocell_${name}_axi4_master_${i}"))
      val port = IO(Flipped(AXI4Bundle(edge.bundle)))
      val ios = IOCell.generateFromSignal(mem_axi4, port, Some(s"iocell_${name}_axi4_master_${i}"))
      port.suggestName(s"${name}_axi4_master_${i}")
      (port, edge, ios)
    }}
  }

  def blockDev(bdev: BlockDeviceIO): (BlockDeviceIO, Seq[IOCell]) = {
    val (port, ios) = IOCell.generateIOFromSignal(bdev, Some("iocell_bdev"))
    port.suggestName("bdev")
    (port, ios)
  }
}

// DOC include start: WithGPIOTiedOff
class WithGPIOTiedOff extends OverrideIOBinder({
  (system: HasPeripheryGPIOModuleImp) => {
    val (ports2d, ioCells2d) = AddIOCells.gpio(system.gpio)
    val harnessFn = (th: HasHarnessSignalReferences) => { ports2d.flatten.foreach(_ <> AnalogConst(0)); Nil }
    Seq((ports2d.flatten, ioCells2d.flatten, Some(harnessFn)))
  }
})
// DOC include end: WithGPIOTiedOff

class WithUARTAdapter extends OverrideIOBinder({
  (system: HasPeripheryUARTModuleImp) => {
    val (ports, ioCells2d) = AddIOCells.uart(system.uart)
    val harnessFn = (th: HasHarnessSignalReferences) => { UARTAdapter.connect(ports)(system.p); Nil }
    Seq((ports, ioCells2d.flatten, Some(harnessFn)))
  }
})

class WithSimSPIFlashModel(rdOnly: Boolean = true) extends OverrideIOBinder({
  (system: HasPeripherySPIFlashModuleImp) => {
    val (ports, ioCells2d) = AddIOCells.spi(system.qspi, "qspi")
    val harnessFn = (th: HasHarnessSignalReferences) => { SimSPIFlashModel.connect(ports, th.harnessReset, rdOnly)(system.p); Nil }
    Seq((ports, ioCells2d.flatten, Some(harnessFn)))
  }
})

class WithSimBlockDevice extends OverrideIOBinder({
  (system: CanHavePeripheryBlockDeviceModuleImp) => system.bdev.map { bdev =>
    val (port, ios) = AddIOCells.blockDev(bdev)
    val harnessFn = (th: HasHarnessSignalReferences) => {
      // TODO: Using harness clock/reset will be incorrect when systemClock =/= harnessClock
      SimBlockDevice.connect(th.harnessClock, th.harnessReset.asBool, Some(port))(system.p)
      Nil
    }
    Seq((Seq(port), ios, Some(harnessFn)))
  }.getOrElse(Nil)
})

class WithBlockDeviceModel extends OverrideIOBinder({
  (system: CanHavePeripheryBlockDeviceModuleImp) => system.bdev.map { bdev =>
    val (port, ios) = AddIOCells.blockDev(bdev)
    val harnessFn = (th: HasHarnessSignalReferences) => {
      BlockDeviceModel.connect(Some(port))(system.p)
      Nil
    }
    Seq((Seq(port), ios, Some(harnessFn)))
  }.getOrElse(Nil)
})

class WithLoopbackNIC extends OverrideIOBinder({
  (system: CanHavePeripheryIceNICModuleImp) => system.connectNicLoopback(); Nil
})

class WithSimNIC extends OverrideIOBinder({
  (system: CanHavePeripheryIceNICModuleImp) => system.connectSimNetwork(system.clock, system.reset.asBool); Nil
})

// DOC include start: WithSimAXIMem
class WithSimAXIMem extends OverrideIOBinder({
  (system: CanHaveMasterAXI4MemPort) => {
    implicit val p: Parameters = GetSystemParameters(system)
    val peiTuples = AddIOCells.axi4(system.mem_axi4, system.memAXI4Node, "mem")
    // TODO: we are inlining the connectMem method of SimAXIMem because
    //   it takes in a dut rather than seq of axi4 ports
    val harnessFn = (th: HasHarnessSignalReferences) => {
      peiTuples.map { case (port, edge, ios) =>
        val mem = LazyModule(new SimAXIMem(edge, size = p(ExtMem).get.master.size))
        Module(mem.module).suggestName("mem")
        mem.io_axi4.head <> port
      }
      Nil
    }
    Seq((peiTuples.map(_._1), peiTuples.flatMap(_._3), Some(harnessFn)))
  }
})
// DOC include end: WithSimAXIMem

class WithBlackBoxSimMem extends OverrideIOBinder({
  (system: CanHaveMasterAXI4MemPort) => {
    implicit val p: Parameters = GetSystemParameters(system)
    val peiTuples = AddIOCells.axi4(system.mem_axi4, system.memAXI4Node, "mem")
    val harnessFn = (th: HasHarnessSignalReferences) => {
      peiTuples.map { case (port, edge, ios) =>
        val memSize = p(ExtMem).get.master.size
        val lineSize = p(CacheBlockBytes)
        val mem = Module(new SimDRAM(memSize, lineSize, edge.bundle))
        mem.io.axi <> port
        // TODO: Using harness clock/reset will be incorrect when systemClock =/= harnessClock
        mem.io.clock := th.harnessClock
        mem.io.reset := th.harnessReset
      }
      Nil
    }
    Seq((peiTuples.map(_._1), peiTuples.flatMap(_._3), Some(harnessFn)))
  }
})

class WithSimAXIMMIO extends OverrideIOBinder({
  (system: CanHaveMasterAXI4MMIOPort) => {
    implicit val p: Parameters = GetSystemParameters(system)
    val peiTuples = AddIOCells.axi4(system.mmio_axi4, system.mmioAXI4Node, "mmio_mem")
    val harnessFn = (th: HasHarnessSignalReferences) => {
      peiTuples.zipWithIndex.map { case ((port, edge, ios), i) =>
        val mmio_mem = LazyModule(new SimAXIMem(edge, size = 4096))
        Module(mmio_mem.module).suggestName(s"mmio_mem_${i}")
        mmio_mem.io_axi4.head <> port
      }
      Nil
    }
    Seq((peiTuples.map(_._1), peiTuples.flatMap(_._3), Some(harnessFn)))
  }
})

class WithDontTouchPorts extends OverrideIOBinder({
  (system: DontTouch) => system.dontTouchPorts(); Nil
})

class WithTieOffInterrupts extends OverrideIOBinder({
  (system: HasExtInterruptsModuleImp) => {
    val (port, ioCells) = IOCell.generateIOFromSignal(system.interrupts, Some("iocell_interrupts"))
    port.suggestName("interrupts")
    val harnessFn = (th: HasHarnessSignalReferences) => { port := 0.U; Nil }
    Seq((Seq(port), ioCells, Some(harnessFn)))
  }
})

class WithTieOffL2FBusAXI extends OverrideIOBinder({
  (system: CanHaveSlaveAXI4Port) => {
    val peiTuples = AddIOCells.axi4(system.l2_frontend_bus_axi4, system.l2FrontendAXI4Node, "l2_fbus")
    val harnessFn = (th: HasHarnessSignalReferences) => {
      peiTuples.zipWithIndex.map { case ((port, edge, ios), i) =>
        port := DontCare // tieoff doesn't completely tie-off, for some reason
        port.tieoff()
      }
      Nil
    }
    Seq((peiTuples.map(_._1), peiTuples.flatMap(_._3), Some(harnessFn)))
  }
})

class WithSimDebug extends OverrideIOBinder({
  (system: HasPeripheryDebugModuleImp) => {
    val (ports, iocells) = AddIOCells.debug(system)(system.p)
    val harnessFn = (th: HasHarnessSignalReferences) => {
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
      Nil
    }
    Seq((ports, iocells, Some(harnessFn)))
  }
})

class WithTiedOffDebug extends OverrideIOBinder({
  (system: HasPeripheryDebugModuleImp) => {
    val (ports, iocells) = AddIOCells.debug(system)(system.p)
    val harnessFn = (th: HasHarnessSignalReferences) => {
      ports.map {
        case d: ClockedDMIIO =>
          d.dmi.req.valid := false.B
          d.dmi.req.bits  := DontCare
          d.dmi.resp.ready := true.B
          d.dmiClock := th.harnessClock
          d.dmiReset := th.harnessReset
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
    Seq((ports, iocells, Some(harnessFn)))
  }
})

class WithTiedOffSerial extends OverrideIOBinder({
  (system: CanHavePeripherySerialModuleImp) => system.serial.map({ serial =>
    val (port, ioCells) = AddIOCells.serial(serial)
    val harnessFn = (th: HasHarnessSignalReferences) => {
      SerialAdapter.tieoff(port)
      Nil
    }
    Seq((Seq(port), ioCells, Some(harnessFn)))
  }).getOrElse(Nil)
})

class WithSimSerial extends OverrideIOBinder({
  (system: CanHavePeripherySerialModuleImp) => system.serial.map({ serial =>
    val (port, ioCells) = AddIOCells.serial(serial)
    val harnessFn = (th: HasHarnessSignalReferences) => {
      val ser_success = SerialAdapter.connectSimSerial(port, th.harnessClock, th.harnessReset)
      when (ser_success) { th.success := true.B }
      Nil
    }
    Seq((Seq(port), ioCells, Some(harnessFn)))
  }).getOrElse(Nil)
})

class WithTraceGenSuccessBinder extends OverrideIOBinder({
  (system: TraceGenSystemModuleImp) => {
    val (successPort, ioCells) = IOCell.generateIOFromSignal(system.success, Some("iocell_success"))
    successPort.suggestName("success")
    val harnessFn = (th: HasHarnessSignalReferences) => { when (successPort) { th.success := true.B }; Nil }
    Seq((Seq(successPort), ioCells, Some(harnessFn)))
  }
})

class WithSimDromajoBridge extends ComposeIOBinder({
   (system: CanHaveTraceIOModuleImp) => {
     system.traceIO match { case Some(t) => t.traces.map(tileTrace => SimDromajoBridge(tileTrace)(system.p)) }
     Nil
   }
})


} /* end package object */
