package chipyard.iobinders

import chisel3._
import chisel3.util.experimental.{BoringUtils}
import chisel3.experimental.{Analog, IO, DataMirror}

import freechips.rocketchip.config._
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
import icenet.{CanHavePeripheryIceNIC, SimNetwork, NicLoopback, NICKey, NICIOvonly}

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
case object IOBinders extends Field[Map[String, (Any) => (Seq[Data], Seq[IOCell])]](
  Map[String, (Any) => (Seq[Data], Seq[IOCell])]().withDefaultValue((Any) => (Nil, Nil))
)
object ApplyIOBinders {
  def apply(sys: LazyModule, map: Map[String, (Any) => (Seq[Data], Seq[IOCell])]):
      (Iterable[Data], Iterable[IOCell], Map[String, Seq[Data]]) = {
    val lzy = map.map({ case (s,f) => s -> f(sys) })
    val imp = map.map({ case (s,f) => s -> f(sys.module) })
    val unzipped = (lzy.values ++ imp.values).unzip

    val ports: Iterable[Data] = unzipped._1.flatten
    val cells: Iterable[IOCell] = unzipped._2.flatten
    val portMap: Map[String, Seq[Data]] = map.keys.map(k => k -> (lzy(k)._1 ++ imp(k)._1)).toMap
    (ports, cells, portMap)
  }
}

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

class IOBinder(f: (View, View, View) => PartialFunction[Any, Any]) extends Config(f)

// This macro overrides previous matches on some Top mixin. This is useful for
// binders which drive IO, since those typically cannot be composed
class OverrideIOBinder[T, S <: Data](fn: => (T) => (Seq[S], Seq[IOCell]))(implicit tag: ClassTag[T]) extends IOBinder((site, here, up) => {
  case IOBinders => up(IOBinders, site) + (tag.runtimeClass.toString ->
      ((t: Any) => {
        t match {
          case system: T =>
            val (ports, cells) = fn(system)
            (ports, cells)
          case _ => (Nil, Nil)
        }
      })
  )
})

// This macro composes with previous matches on some Top mixin. This is useful for
// annotation-like binders, since those can typically be composed
class ComposeIOBinder[T, S <: Data](fn: => (T) => (Seq[S], Seq[IOCell]))(implicit tag: ClassTag[T]) extends IOBinder((site, here, up) => {
  case IOBinders => up(IOBinders, site) + (tag.runtimeClass.toString ->
      ((t: Any) => {
        t match {
          case system: T =>
            val r = up(IOBinders, site)(tag.runtimeClass.toString)(system)
            val h = fn(system)
            val ports = r._1 ++ h._1
            val cells = r._2 ++ h._2
            (ports, cells)
          case _ => (Nil, Nil)
        }
      })
  )
})

object BoreHelper {
  def apply[T <: Data](name: String, source: T): T = {
    val (io, wire) = source match {
      case c: Clock =>
        val wire =  Wire(Clock())
        // Provide a dummy assignment to prevent FIRRTL invalid assignment
        // errors prior to running the wiring pass
        wire := false.B.asClock
        (IO(Output(Clock())), wire)
      case r: Reset =>
        val wire =  Wire(Reset())
        wire := false.B
        (IO(Output(Reset())), wire)
    }
    io.suggestName(name)
    wire.suggestName(s"chiptop_${name}")
    dontTouch(wire)
    BoringUtils.bore(source, Seq(wire))
    io := wire
    io.asInstanceOf[source.type]
  }
}


case object IOCellKey extends Field[IOCellTypeParams](GenericIOCellParams())


class WithGPIOCells extends OverrideIOBinder({
  (system: HasPeripheryGPIOModuleImp) => {
    val (ports2d, cells2d) = system.gpio.zipWithIndex.map({ case (gpio, i) =>
      gpio.pins.zipWithIndex.map({ case (pin, j) =>
        val g = IO(Analog(1.W)).suggestName(s"gpio_${i}_${j}")
        val iocell = system.p(IOCellKey).gpio().suggestName(s"iocell_gpio_${i}_${j}")
        iocell.io.o := pin.o.oval
        iocell.io.oe := pin.o.oe
        iocell.io.ie := pin.o.ie
        pin.i.ival := iocell.io.i
        iocell.io.pad <> g
        (g, iocell)
      }).unzip
    }).unzip
    val ports: Seq[Analog] = ports2d.flatten
    (ports, cells2d.flatten)
  }
})

// DOC include start: WithUARTIOCells
class WithUARTIOCells extends OverrideIOBinder({
  (system: HasPeripheryUARTModuleImp) => {
    val (ports: Seq[UARTPortIO], cells2d) = system.uart.zipWithIndex.map({ case (u, i) =>
      val (port, ios) = IOCell.generateIOFromSignal(u, s"uart_${i}", system.p(IOCellKey), abstractResetAsAsync = true)
      (port, ios)
    }).unzip
    (ports, cells2d.flatten)
  }
})
// DOC include end: WithUARTIOCells

class WithSPIIOCells extends OverrideIOBinder({
  (system: HasPeripherySPIFlashModuleImp) => {
    val (ports: Seq[SPIChipIO], cells2d) = system.qspi.zipWithIndex.map({ case (s, i) =>
      val name = s"spi_${i}"
      val port = IO(new SPIChipIO(s.c.csWidth)).suggestName(name)
      val iocellBase = s"iocell_${name}"

      // SCK and CS are unidirectional outputs
      val sckIOs = IOCell.generateFromSignal(s.sck, port.sck, Some(s"${iocellBase}_sck"), system.p(IOCellKey), IOCell.toAsyncReset)
      val csIOs = IOCell.generateFromSignal(s.cs, port.cs, Some(s"${iocellBase}_cs"), system.p(IOCellKey), IOCell.toAsyncReset)

      // DQ are bidirectional, so then need special treatment
      val dqIOs = s.dq.zip(port.dq).zipWithIndex.map { case ((pin, ana), j) =>
        val iocell = system.p(IOCellKey).gpio().suggestName(s"${iocellBase}_dq_${j}")
        iocell.io.o := pin.o
        iocell.io.oe := pin.oe
        iocell.io.ie := true.B
        pin.i := iocell.io.i
        iocell.io.pad <> ana
        iocell
      }

      (port, dqIOs ++ csIOs ++ sckIOs)
    }).unzip
    (ports, cells2d.flatten)
  }
})

class WithExtInterruptIOCells extends OverrideIOBinder({
  (system: HasExtInterruptsModuleImp) => {
    if (system.outer.nExtInterrupts > 0) {
      val (port: UInt, cells) = IOCell.generateIOFromSignal(system.interrupts, "ext_interrupts", system.p(IOCellKey), abstractResetAsAsync = true)
      (Seq(port), cells)
    } else {
      (Nil, Nil)
    }
  }
})


class WithDebugIOCells extends OverrideIOBinder({
  (system: HasPeripheryDebugModuleImp) => {
    system.debug.map({ debug =>
      val p = system.p
      val tlbus = system.outer.asInstanceOf[BaseSubsystem].locateTLBusWrapper(p(ExportDebug).slaveWhere)
      val debug_clock = Wire(Clock()).suggestName("debug_clock")
      val debug_reset = Wire(Reset()).suggestName("debug_reset")
      debug_clock := false.B.asClock // must provide default assignment to avoid firrtl unassigned error
      debug_reset := false.B // must provide default assignment to avoid firrtl unassigned error
      BoringUtils.bore(tlbus.module.clock, Seq(debug_clock))
      BoringUtils.bore(tlbus.module.reset, Seq(debug_reset))

      // We never use the PSDIO, so tie it off on-chip
      system.psd.psd.foreach { _ <> 0.U.asTypeOf(new PSDTestMode) }
      system.resetctrl.map { rcio => rcio.hartIsInReset.map { _ := debug_reset.asBool } }
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
          j.reset := debug_reset
          j.mfr_id := system.p(JtagDTMKey).idcodeManufId.U(11.W)
          j.part_number := system.p(JtagDTMKey).idcodePartNum.U(16.W)
          j.version := system.p(JtagDTMKey).idcodeVersion.U(4.W)
        }
      }
      Debug.connectDebugClockAndReset(Some(debug), debug_clock)(system.p)

      // Add IOCells for the DMI/JTAG/APB ports
      val dmiTuple = debug.clockeddmi.map { d =>
        IOCell.generateIOFromSignal(d, "dmi", p(IOCellKey), abstractResetAsAsync = true)
      }

      val jtagTuple = debug.systemjtag.map { j =>
        IOCell.generateIOFromSignal(j.jtag, "jtag", p(IOCellKey), abstractResetAsAsync = true)
      }

      val apbTuple = debug.apb.map { a =>
        IOCell.generateIOFromSignal(a, "apb", p(IOCellKey), abstractResetAsAsync = true)
      }

      val allTuples = (dmiTuple ++ jtagTuple ++ apbTuple).toSeq
      (allTuples.map(_._1).toSeq, allTuples.flatMap(_._2).toSeq)
    }).getOrElse((Nil, Nil))
  }
})

class WithSerialTLIOCells extends OverrideIOBinder({
  (system: CanHavePeripheryTLSerial) => system.serial_tl.map({ s =>
    val sys = system.asInstanceOf[BaseSubsystem]
    val (port, cells) = IOCell.generateIOFromSignal(s.getWrappedValue, "serial_tl", sys.p(IOCellKey), abstractResetAsAsync = true)
    (Seq(port), cells)
  }).getOrElse((Nil, Nil))
})


class WithAXI4MemPunchthrough extends OverrideIOBinder({
  (system: CanHaveMasterAXI4MemPort) => {
    val ports: Seq[ClockedAndResetIO[AXI4Bundle]] = system.mem_axi4.zipWithIndex.map({ case (m, i) =>
      val p = IO(new ClockedAndResetIO(DataMirror.internal.chiselTypeClone[AXI4Bundle](m))).suggestName(s"axi4_mem_${i}")
      p.bits <> m
      val mbus = system.asInstanceOf[HasTileLinkLocations].locateTLBusWrapper(MBUS)
      p.clock := BoreHelper("axi4_mem_clock",  mbus.module.clock)
      p.reset := BoreHelper("axi4_mem_reset", mbus.module.reset)
      p
    })
    (ports, Nil)
  }
})

class WithAXI4MMIOPunchthrough extends OverrideIOBinder({
  (system: CanHaveMasterAXI4MMIOPort) => {
    val ports: Seq[ClockedAndResetIO[AXI4Bundle]] = system.mmio_axi4.zipWithIndex.map({ case (m, i) =>
      val p = IO(new ClockedAndResetIO(DataMirror.internal.chiselTypeClone[AXI4Bundle](m))).suggestName(s"axi4_mmio_${i}")
      p.bits <> m
      val mbus = system.asInstanceOf[HasTileLinkLocations].locateTLBusWrapper(MBUS)
      p.clock := BoreHelper("axi4_mmio_clock",  mbus.module.clock)
      p.reset := BoreHelper("axi4_mmio_reset", mbus.module.reset)
      p
    })
    (ports, Nil)
  }
})

class WithL2FBusAXI4Punchthrough extends OverrideIOBinder({
   (system: CanHaveSlaveAXI4Port) => {
    val ports: Seq[ClockedIO[AXI4Bundle]] = system.l2_frontend_bus_axi4.zipWithIndex.map({ case (m, i) =>
      val p = IO(new ClockedIO(Flipped(DataMirror.internal.chiselTypeClone[AXI4Bundle](m)))).suggestName(s"axi4_fbus_${i}")
      m <> p.bits
      p.clock := BoreHelper("axi4_fbus_clock", system.asInstanceOf[BaseSubsystem].fbus.module.clock)
      p
    })
    (ports, Nil)
  }
})

class WithBlockDeviceIOPunchthrough extends OverrideIOBinder({
  (system: CanHavePeripheryBlockDevice) => {
    val ports: Seq[ClockedIO[BlockDeviceIO]] = system.bdev.map({ bdev =>
      val p = IO(new ClockedIO(new BlockDeviceIO()(GetSystemParameters(system)))).suggestName("blockdev")
      p <> bdev
      p
    }).toSeq
    (ports, Nil)
  }
})

class WithNICIOPunchthrough extends OverrideIOBinder({
  (system: CanHavePeripheryIceNIC) => {
    val ports: Seq[ClockedIO[NICIOvonly]] = system.icenicOpt.map({ n =>
      val p = IO(new ClockedIO(new NICIOvonly)).suggestName("nic")
      p <> n
      p
    }).toSeq
    (ports, Nil)
  }
})

class WithTraceGenSuccessPunchthrough extends OverrideIOBinder({
  (system: TraceGenSystemModuleImp) => {
    val success: Bool = IO(Output(Bool())).suggestName("success")
    success := system.success
    (Seq(success), Nil)
  }
})

class WithTraceIOPunchthrough extends OverrideIOBinder({
  (system: CanHaveTraceIOModuleImp) => {
    val ports: Option[TraceOutputTop] = system.traceIO.map { t =>
      val trace = IO(DataMirror.internal.chiselTypeClone[TraceOutputTop](t)).suggestName("trace")
      trace <> t
      trace
    }
    (ports.toSeq, Nil)
  }
})


class WithDontTouchPorts extends OverrideIOBinder({
  (system: DontTouch) => system.dontTouchPorts(); (Nil, Nil)
})

