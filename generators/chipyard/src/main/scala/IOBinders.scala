package chipyard.iobinders

import chisel3._
import chisel3.experimental.{Analog, IO}

import freechips.rocketchip.config.{Field, Config, Parameters}
import freechips.rocketchip.diplomacy.{LazyModule}
import freechips.rocketchip.devices.debug._
import freechips.rocketchip.subsystem._
// Note: Do not import system._ because it also has a TestHarness that conflicts with chipyard.TestHarness
//import freechips.rocketchip.system._
import freechips.rocketchip.util._

import sifive.blocks.devices.gpio._
import sifive.blocks.devices.uart._

import barstools.iocell.chisel._

import chipyard.{TestHarness, TestHarnessUtils}
// Note: Do not import testchipip._ because it also has a TestHarness that conflicts with chipyard.TestHarness
import testchipip.{CanHavePeripherySerialModuleImp, SerialIO, SerialAdapter, CanHavePeripheryBlockDeviceModuleImp, SimBlockDevice, BlockDeviceModel, SimDRAM}
import icenet.{CanHavePeripheryIceNICModuleImp, SimNetwork, NicLoopback, NICKey}
import tracegen.{HasTraceGenTilesModuleImp}

import scala.reflect.{ClassTag}

// System for instantiating binders based
// on the scala type of the Target (_not_ its IO). This avoids needing to
// duplicate harnesses (essentially test harnesses) for each target.

// IOBinders is map between string representations of traits to the desired
// IO connection behavior for tops matching that trait. We use strings to enable
// composition and overriding of IOBinders, much like how normal Keys in the config
// system are used/ At elaboration, the testharness traverses this set of functions,
// and functions which match the type of the Top are evaluated.

// You can add your own binder by adding a new (key, fn) pair, typically by using
// the OverrideIOBinder or ComposeIOBinder macros


object types {
  type TestHarnessFunction = (TestHarness) => Seq[Any]
  type IOBinderTuple = (Seq[Data], Seq[IOCell], Option[TestHarnessFunction])
}
import types._

// DOC include start: IOBinders
case object IOBinders extends Field[Map[String, (Any) => Seq[IOBinderTuple]]](
  Map[String, (Any) => Seq[IOBinderTuple]]().withDefaultValue((Any) => Nil)
)

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
          // TODO is this how we want to merge the new IOBinders return values?
          case system: T => (up(IOBinders, site)(tag.runtimeClass.toString)(system)
            ++ fn(system))
          case _ => Nil
        }
      })
  )
})

// DOC include end: IOBinders

object AddIOCells {
  def gpio(gpios: Seq[GPIOPortIO]): (Seq[Seq[Analog]], Seq[Seq[IOCell]]) = {
    gpios.zipWithIndex.map({ case (gpio, i) =>
      gpio.pins.zipWithIndex.map({ case (pin, j) =>
        val g = IO(Analog(1.W))
        g.suggestName("gpio_${i}_${j}")
        // TODO functionalize this?
        val iocell = IOCell.exampleGPIO()
        iocell.suggestName(s"iocell_gpio_${i}_${j}")
        iocell.io.o := pin.o.oval
        iocell.io.oe := pin.o.oe
        iocell.io.ie := pin.o.ie
        pin.i.ival := iocell.io.i
        iocell.io.pad <> g
        (g, iocell)
      }).unzip
    }).unzip
  }

  def uart(uartPins: Seq[UARTPortIO]): (Seq[UARTPortIO], Seq[Seq[IOCell]]) = {
    uartPins.zipWithIndex.map({ case (u, i) =>
      val (port, ios) = IOCell.generateIOFromSignal(u, Some(s"iocell_uart_${i}"))
      port.suggestName("iocell_uart_${i}")
      (port, ios)
    }).unzip
  }

  def debug(psd: PSDIO, debugOpt: Option[DebugIO]): (PSDIO, Option[DebugIO], Seq[IOCell]) = {
    val (psdPort, psdIOs) = IOCell.generateIOFromSignal(psd, Some("iocell_psd"))
    val optTuple = debugOpt.map(d => IOCell.generateIOFromSignal(d, Some("iocell_debug")))
    val debugPortOpt: Option[DebugIO] = optTuple.map(_._1)
    val debugIOs: Seq[IOCell] = optTuple.map(_._2).toSeq.flatten
    debugPortOpt.foreach(_.suggestName("debug"))
    psdPort.suggestName("psd")
    (psdPort, debugPortOpt, psdIOs ++ debugIOs)
  }

  def serial(serial: SerialIO): (SerialIO, Seq[IOCell]) = {
    val (port, ios) = IOCell.generateIOFromSignal(serial, Some("iocell_serial"))
    port.suggestName("serial")
    (port, ios)
  }
}

class WithGPIOTiedOff extends OverrideIOBinder({
  (system: HasPeripheryGPIOModuleImp) => {
    // TODO: Do we suggestName the ports?
    val (ports2d, ioCells2d) = AddIOCells.gpio(system.gpio)
    val harnessFn = (th: TestHarness) => { ports2d.flatten.foreach(_ <> AnalogConst(0)); Nil }
    Seq((ports2d.flatten, ioCells2d.flatten, Some(harnessFn)))
  }
})

class WithUARTAdapter extends OverrideIOBinder({
  (system: HasPeripheryUARTModuleImp) => {
    // TODO: Do we suggestName the ports?
    val (ports, ioCells2d) = AddIOCells.uart(system.uart)
    val harnessFn = (th: TestHarness) => { UARTAdapter.connect(ports)(system.p); Nil }
    Seq((ports, ioCells2d.flatten, Some(harnessFn)))
  }
})

// TODO: Add a note about synthesizability of SimBlockDevice
class WithSimBlockDevice extends OverrideIOBinder({
  (system: CanHavePeripheryBlockDeviceModuleImp) => system.connectSimBlockDevice(system.clock, system.reset.asBool); Nil
})

// TODO: Add a note about synthesizability of SimBlockDevice
class WithBlockDeviceModel extends OverrideIOBinder({
  (system: CanHavePeripheryBlockDeviceModuleImp) => system.connectBlockDeviceModel(); Nil
})

// TODO: Do we want to allow this to be synthesized (i.e. should this go in the test harness or in ChipTop)
class WithLoopbackNIC extends OverrideIOBinder({
  (system: CanHavePeripheryIceNICModuleImp) => system.connectNicLoopback(); Nil
})

// TODO: Do we want to allow this to be synthesized (i.e. should this go in the test harness or in ChipTop)
class WithSimNIC extends OverrideIOBinder({
  (system: CanHavePeripheryIceNICModuleImp) => system.connectSimNetwork(system.clock, system.reset.asBool); Nil
})

// DOC include start: WithSimAXIMem
class WithSimAXIMem extends OverrideIOBinder({
  (system: CanHaveMasterAXI4MemPortModuleImp) => system.connectSimAXIMem(); Nil
})
// DOC include end: WithSimAXIMem

class WithBlackBoxSimMem extends OverrideIOBinder({
  (system: CanHaveMasterAXI4MemPortModuleImp) => {
    (system.mem_axi4 zip system.outer.memAXI4Node).foreach { case (io, node) =>
      val memSize = system.p(ExtMem).get.master.size
      val lineSize = system.p(CacheBlockBytes)
      (io zip node.in).foreach { case (axi4, (_, edge)) =>
        val mem = Module(new SimDRAM(memSize, lineSize, edge.bundle))
        mem.io.axi <> axi4
        mem.io.clock := system.clock
        mem.io.reset := system.reset
      }
    }; Nil
  }
})

class WithSimAXIMMIO extends OverrideIOBinder({
  (system: CanHaveMasterAXI4MMIOPortModuleImp) => system.connectSimAXIMMIO(); Nil
})

class WithDontTouchPorts extends OverrideIOBinder({
  (system: DontTouch) => system.dontTouchPorts(); Nil
})

class WithTieOffInterrupts extends OverrideIOBinder({
  (system: HasExtInterruptsModuleImp) => {
    val (port, ioCells) = IOCell.generateIOFromSignal(system.interrupts, Some("iocell_interrupts"))
    port.suggestName("interrupts")
    val harnessFn = (th: TestHarness) => { port := 0.U; Nil }
    Seq((Seq(port), ioCells, Some(harnessFn)))
  }
})

class WithTieOffL2FBusAXI extends OverrideIOBinder({
  (system: CanHaveSlaveAXI4PortModuleImp) => {
    system.l2_frontend_bus_axi4.foreach(axi => {
      axi.tieoff()
      experimental.DataMirror.directionOf(axi.ar.ready) match {
        case ActualDirection.Input =>
          axi.r.bits := DontCare
          axi.b.bits := DontCare
        case ActualDirection.Output =>
          axi.aw.bits := DontCare
          axi.ar.bits := DontCare
          axi.w.bits := DontCare
        case _ => throw new Exception("Unknown AXI port direction")
      }
    })
    Nil
  }
})

class WithTiedOffDebug extends OverrideIOBinder({
  (system: HasPeripheryDebugModuleImp) => {
    val (psdPort, debugPortOpt, ioCells) = AddIOCells.debug(system.psd, system.debug)
    val harnessFn = (th: TestHarness) => { TestHarnessUtils.tieoffDebug(th.c, th.r, debugPortOpt, psdPort); Nil }
    Seq((Seq(psdPort) ++ debugPortOpt.toSeq, ioCells, Some(harnessFn)))
  }
})

class WithSimDebug extends OverrideIOBinder({
  (system: HasPeripheryDebugModuleImp) => {
    val (psdPort, debugPortOpt, ioCells) = AddIOCells.debug(system.psd, system.debug)
    val harnessFn = (th: TestHarness) => {
      th.ro := TestHarnessUtils.connectSimDebug(th.c, th.r, th.s, debugPortOpt, psdPort)(system.p)
      Nil
    }
    Seq((Seq(psdPort) ++ debugPortOpt.toSeq, ioCells, Some(harnessFn)))
  }
})

class WithTiedOffSerial extends OverrideIOBinder({
  (system: CanHavePeripherySerialModuleImp) => system.serial.map({ serial =>
    val (port, ioCells) = AddIOCells.serial(serial)
    val harnessFn = (th: TestHarness) => {
      SerialAdapter.tieoff(port)
      Nil
    }
    Seq((Seq(port), ioCells, Some(harnessFn)))
  }).getOrElse(Nil)
})

class WithSimSerial extends OverrideIOBinder({
  (system: CanHavePeripherySerialModuleImp) => system.serial.map({ serial =>
    val (port, ioCells) = AddIOCells.serial(serial)
    val harnessFn = (th: TestHarness) => {
      val ser_success = SerialAdapter.connectSimSerial(port, th.c, th.r)
      when (ser_success) { th.s := true.B }
      Nil
    }
    Seq((Seq(port), ioCells, Some(harnessFn)))
  }).getOrElse(Nil)
})

class WithTraceGenSuccessBinder extends OverrideIOBinder({
  (system: HasTraceGenTilesModuleImp) => {
    val (successPort, ioCells) = IOCell.generateIOFromSignal(system.success, Some("iocell_success"))
    successPort.suggestName("success")
    val harnessFn = (th: TestHarness) => { when (successPort) { th.s := true.B }; Nil }
    Seq((Seq(successPort), ioCells, Some(harnessFn)))
  }
})

