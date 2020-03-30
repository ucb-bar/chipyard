package chipyard
package object iobinders {

import chisel3._
import chisel3.experimental.{Analog, IO}

import freechips.rocketchip.config.{Field, Config, Parameters}
import freechips.rocketchip.diplomacy.{LazyModule}
import freechips.rocketchip.devices.debug._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.util._

import sifive.blocks.devices.gpio._
import sifive.blocks.devices.uart._

import barstools.iocell.chisel._

import testchipip._
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
// and functions which match the type of the DigitalTop are evaluated.

// You can add your own binder by adding a new (key, fn) pair, typically by using
// the OverrideIOBinder or ComposeIOBinder macros


// DOC include start: IOBinders
// This type describes a function callable on the TestHarness instance. Its return type is unused.
type TestHarnessFunction = (chipyard.TestHarness) => Seq[Any]
// IOBinders will return a Seq of this tuple, which contains three fields:
//  1. A Seq containing all IO ports created by the IOBinder function
//  2. A Seq containing all IO cell modules created by the IOBinder function
//  3. An optional function to call inside the test harness (e.g. to connect the IOs)
type IOBinderTuple = (Seq[Data], Seq[IOCell], Option[TestHarnessFunction])

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
  def gpio(gpios: Seq[GPIOPortIO], genFn: () => DigitalGPIOCell = IOCell.exampleGPIO): (Seq[Seq[Analog]], Seq[Seq[IOCell]]) = {
    gpios.zipWithIndex.map({ case (gpio, i) =>
      gpio.pins.zipWithIndex.map({ case (pin, j) =>
        val g = IO(Analog(1.W))
        g.suggestName("gpio_${i}_${j}")
        val iocell = genFn()
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

  /**
   * Add IO cells to a SiFive UART devices and name the IO ports.
   * @param gpios A Seq of UART port bundles
   * @return Returns a tuple of (A Seq of top-level UARTPortIO IOs; a 2D Seq of IOCell module references)
   */
  def uart(uartPins: Seq[UARTPortIO]): (Seq[UARTPortIO], Seq[Seq[IOCell]]) = {
    uartPins.zipWithIndex.map({ case (u, i) =>
      val (port, ios) = IOCell.generateIOFromSignal(u, Some(s"iocell_uart_${i}"))
      port.suggestName("iocell_uart_${i}")
      (port, ios)
    }).unzip
  }

  /**
   * Add IO cells to a debug module and name the IO ports.
   * @param gpios A PSDIO bundle
   * @param debugOpt An optional DebugIO bundle
   * @return Returns a tuple3 of (Top-level PSDIO IO; Optional top-level DebugIO IO; a list of IOCell module references)
   */
  def debug(psd: PSDIO, debugOpt: Option[DebugIO]): (PSDIO, Option[DebugIO], Seq[IOCell]) = {
    val (psdPort, psdIOs) = IOCell.generateIOFromSignal(psd, Some("iocell_psd"))
    val optTuple = debugOpt.map(d => IOCell.generateIOFromSignal(d, Some("iocell_debug")))
    val debugPortOpt: Option[DebugIO] = optTuple.map(_._1)
    val debugIOs: Seq[IOCell] = optTuple.map(_._2).toSeq.flatten
    debugPortOpt.foreach(_.suggestName("debug"))
    psdPort.suggestName("psd")
    (psdPort, debugPortOpt, psdIOs ++ debugIOs)
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
}

class WithGPIOTiedOff extends OverrideIOBinder({
  (system: HasPeripheryGPIOModuleImp) => {
    val (ports2d, ioCells2d) = AddIOCells.gpio(system.gpio)
    val harnessFn = (th: chipyard.TestHarness) => { ports2d.flatten.foreach(_ <> AnalogConst(0)); Nil }
    Seq((ports2d.flatten, ioCells2d.flatten, Some(harnessFn)))
  }
})

class WithUARTAdapter extends OverrideIOBinder({
  (system: HasPeripheryUARTModuleImp) => {
    val (ports, ioCells2d) = AddIOCells.uart(system.uart)
    val harnessFn = (th: chipyard.TestHarness) => { UARTAdapter.connect(ports)(system.p); Nil }
    Seq((ports, ioCells2d.flatten, Some(harnessFn)))
  }
})

class WithSimBlockDevice extends OverrideIOBinder({
  (system: CanHavePeripheryBlockDeviceModuleImp) => system.connectSimBlockDevice(system.clock, system.reset.asBool); Nil
})

class WithBlockDeviceModel extends OverrideIOBinder({
  (system: CanHavePeripheryBlockDeviceModuleImp) => system.connectBlockDeviceModel(); Nil
})

class WithLoopbackNIC extends OverrideIOBinder({
  (system: CanHavePeripheryIceNICModuleImp) => system.connectNicLoopback(); Nil
})

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
    val harnessFn = (th: chipyard.TestHarness) => { port := 0.U; Nil }
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
    val harnessFn = (th: chipyard.TestHarness) => {
      Debug.tieoffDebug(debugPortOpt, psdPort)
      // tieoffDebug doesn't actually tie everything off :/
      debugPortOpt.foreach(_.clockeddmi.foreach({ cdmi => cdmi.dmi.req.bits := DontCare }))
      Nil
    }
    Seq((Seq(psdPort) ++ debugPortOpt.toSeq, ioCells, Some(harnessFn)))
  }
})

class WithSimDebug extends OverrideIOBinder({
  (system: HasPeripheryDebugModuleImp) => {
    val (psdPort, debugPortOpt, ioCells) = AddIOCells.debug(system.psd, system.debug)
    val harnessFn = (th: chipyard.TestHarness) => {
      val dtm_success = Wire(Bool())
      Debug.connectDebug(debugPortOpt, psdPort, th.clock, th.harnessReset, dtm_success)(system.p)
      when (dtm_success) { th.success := true.B }
      th.dutReset := th.harnessReset | debugPortOpt.map { debug => AsyncResetReg(debug.ndreset).asBool }.getOrElse(false.B)
      Nil
    }
    Seq((Seq(psdPort) ++ debugPortOpt.toSeq, ioCells, Some(harnessFn)))
  }
})

class WithTiedOffSerial extends OverrideIOBinder({
  (system: CanHavePeripherySerialModuleImp) => system.serial.map({ serial =>
    val (port, ioCells) = AddIOCells.serial(serial)
    val harnessFn = (th: chipyard.TestHarness) => {
      SerialAdapter.tieoff(port)
      Nil
    }
    Seq((Seq(port), ioCells, Some(harnessFn)))
  }).getOrElse(Nil)
})

class WithSimSerial extends OverrideIOBinder({
  (system: CanHavePeripherySerialModuleImp) => system.serial.map({ serial =>
    val (port, ioCells) = AddIOCells.serial(serial)
    val harnessFn = (th: chipyard.TestHarness) => {
      val ser_success = SerialAdapter.connectSimSerial(port, th.clock, th.harnessReset)
      when (ser_success) { th.success := true.B }
      Nil
    }
    Seq((Seq(port), ioCells, Some(harnessFn)))
  }).getOrElse(Nil)
})

class WithTraceGenSuccessBinder extends OverrideIOBinder({
  (system: HasTraceGenTilesModuleImp) => {
    val (successPort, ioCells) = IOCell.generateIOFromSignal(system.success, Some("iocell_success"))
    successPort.suggestName("success")
    val harnessFn = (th: chipyard.TestHarness) => { when (successPort) { th.success := true.B }; Nil }
    Seq((Seq(successPort), ioCells, Some(harnessFn)))
  }
})

}
