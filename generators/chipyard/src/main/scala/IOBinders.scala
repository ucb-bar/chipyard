package chipyard.iobinders

import chisel3._

import freechips.rocketchip.config.{Field, Config, Parameters}
import freechips.rocketchip.diplomacy.{LazyModule}
import freechips.rocketchip.devices.debug._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.system._
import freechips.rocketchip.util._

import sifive.blocks.devices.gpio._
import sifive.blocks.devices.uart._

import testchipip._
import icenet._
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

// DOC include start: IOBinders
case object IOBinders extends Field[Map[String, (Clock, Bool, Bool, Any) => Seq[Any]]](
  Map[String, (Clock, Bool, Bool, Any) => Seq[Any]]().withDefaultValue((c: Clock, r: Bool, s: Bool, t: Any) => Nil)
)

// This macro overrides previous matches on some Top mixin. This is useful for
// binders which drive IO, since those typically cannot be composed
class OverrideIOBinder[T](fn: => (Clock, Bool, Bool, T) => Seq[Any])(implicit tag: ClassTag[T]) extends Config((site, here, up) => {
  case IOBinders => up(IOBinders, site) + (tag.runtimeClass.toString ->
      ((clock: Clock, reset: Bool, success: Bool, t: Any) => {
        t match {
          case top: T => fn(clock, reset, success, top)
          case _ => Nil
        }
      })
  )
})

// This macro composes with previous matches on some Top mixin. This is useful for
// annotation-like binders, since those can typically be composed
class ComposeIOBinder[T](fn: => (Clock, Bool, Bool, T) => Seq[Any])(implicit tag: ClassTag[T]) extends Config((site, here, up) => {
  case IOBinders => up(IOBinders, site) + (tag.runtimeClass.toString ->
      ((clock: Clock, reset: Bool, success: Bool, t: Any) => {
        t match {
          case top: T => (up(IOBinders, site)(tag.runtimeClass.toString)(clock, reset, success, top)
            ++ fn(clock, reset, success, top))
          case _ => Nil
        }
      })
  )
})

// DOC include end: IOBinders

class WithGPIOTiedOff extends OverrideIOBinder({
  (c, r, s, top: HasPeripheryGPIOModuleImp) => top.gpio.map(gpio => gpio.pins.map(p => p.i.ival := false.B)); Nil
})

class WithSimBlockDevice extends OverrideIOBinder({
  (c, r, s, top: CanHavePeripheryBlockDeviceModuleImp) => top.connectSimBlockDevice(c, r); Nil
})

class WithBlockDeviceModel extends OverrideIOBinder({
  (c, r, s, top: CanHavePeripheryBlockDeviceModuleImp) => top.connectBlockDeviceModel(); Nil
})

class WithLoopbackNIC extends OverrideIOBinder({
  (c, r, s, top: CanHavePeripheryIceNICModuleImp) => top.connectNicLoopback(); Nil
})

class WithSimNIC extends OverrideIOBinder({
  (c, r, s, top: CanHavePeripheryIceNICModuleImp) => top.connectSimNetwork(c, r); Nil
})

class WithUARTAdapter extends OverrideIOBinder({
  (c, r, s, top: HasPeripheryUARTModuleImp) => {
    val defaultBaudRate = 115200 // matches sifive-blocks uart baudrate
    top.uart.zipWithIndex.foreach{ case (dut_io, i) =>
      val uart_sim = Module(new UARTAdapter(i, defaultBaudRate)(top.p))
      uart_sim.io.uart.txd := dut_io.txd
      dut_io.rxd := uart_sim.io.uart.rxd
    }
    Nil
  }
})

// DOC include start: WithSimAXIMem
class WithSimAXIMem extends OverrideIOBinder({
  (c, r, s, top: CanHaveMasterAXI4MemPortModuleImp) => top.connectSimAXIMem(); Nil
})
// DOC include end: WithSimAXIMem

class WithBlackBoxSimMem extends OverrideIOBinder({
  (clock, reset, _, top: CanHaveMasterAXI4MemPortModuleImp) => {
    (top.mem_axi4 zip top.outer.memAXI4Node).foreach { case (io, node) =>
      val memSize = top.p(ExtMem).get.master.size
      val lineSize = top.p(CacheBlockBytes)
      (io zip node.in).foreach { case (axi4, (_, edge)) =>
        val mem = Module(new SimDRAM(memSize, lineSize, edge.bundle))
        mem.io.axi <> axi4
        mem.io.clock := clock
        mem.io.reset := reset
      }
    }; Nil
  }
})

class WithSimAXIMMIO extends OverrideIOBinder({
  (c, r, s, top: CanHaveMasterAXI4MMIOPortModuleImp) => top.connectSimAXIMMIO(); Nil
})

class WithDontTouchPorts extends OverrideIOBinder({
  (c, r, s, top: DontTouch) => top.dontTouchPorts(); Nil
})

class WithTieOffInterrupts extends OverrideIOBinder({
  (c, r, s, top: HasExtInterruptsBundle) => top.tieOffInterrupts(); Nil
})

class WithTieOffL2FBusAXI extends OverrideIOBinder({
  (c, r, s, top: CanHaveSlaveAXI4PortModuleImp) => {
    top.l2_frontend_bus_axi4.foreach(axi => {
      axi.tieoff()
      experimental.DataMirror.directionOf(axi.ar.ready) match {
        case ActualDirection.Input =>
          axi.r.bits := DontCare
          axi.b.bits := DontCare
        case ActualDirection.Output =>
          axi.aw.bits := DontCare
          axi.ar.bits := DontCare
          axi.w.bits := DontCare
      }
    })
    Nil
  }
})

class WithTiedOffDebug extends OverrideIOBinder({
  (c, r, s, top: HasPeripheryDebugModuleImp) => {
    Debug.tieoffDebug(top.debug, top.psd)
    // tieoffDebug doesn't actually tie everything off :/
    top.debug.foreach(_.clockeddmi.foreach({ cdmi => cdmi.dmi.req.bits := DontCare }))
    Nil
  }
})

class WithSimSerial extends OverrideIOBinder({
  (c, r, s, top: CanHavePeripherySerialModuleImp) => {
    val ser_success = top.connectSimSerial()
    when (ser_success) { s := true.B }
    Nil
  }
})

class WithTiedOffSerial extends OverrideIOBinder({
  (c, r, s, top: CanHavePeripherySerialModuleImp) => top.tieoffSerial(); Nil
})


class WithSimDebug extends OverrideIOBinder({
  (c, r, s, top: HasPeripheryDebugModuleImp) => {
    val dtm_success = Wire(Bool())
    top.reset := r | top.debug.map { debug => AsyncResetReg(debug.ndreset) }.getOrElse(false.B)
    Debug.connectDebug(top.debug, top.psd, c, r, dtm_success)(top.p)
    when (dtm_success) { s := true.B }
    Nil
  }
})


class WithTraceGenSuccessBinder extends OverrideIOBinder({
  (c, r, s, top: HasTraceGenTilesModuleImp) => when (top.success) { s := true.B };  Nil
})
