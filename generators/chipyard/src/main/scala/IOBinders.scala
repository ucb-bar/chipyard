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

import scala.reflect.{ClassTag, classTag}

// System for instantiating binders based
// on the scala type of the Target (_not_ its IO). This avoids needing to
// duplicate harnesses (essentially test harnesses) for each target.
//
// You could just as well create a custom harness module that instantiates
// bridges explicitly, or add methods to
// your target traits that instantiate the bridge there (i.e., akin to
// SimAXI4Mem). Since cake traits live in Rocket Chip it was easiest to match
// on the types rather than change trait code.



// A map of partial functions that match on the type the DUT (_not_ it's
// IO) to generate an appropriate bridge. You can add your own binder by adding
// a new (key, fn) pair. You should override existing pairs in this map when
// using a custom IOBinder

// Since we also want to compose this structure like the existing config system,
// use the scala string representation of the matched trait as a key

case object IOBinders extends Field[Map[String, (Clock, Bool, Bool, Any) => Seq[Any]]](Map())


// This macro overrides previous matches on some Top mixin. This is useful for
// binders which modify IO, since those typically cannot be composed
class RegisterIOBinder[T](fn: => (Clock, Bool, Bool, T) => Seq[Any])(implicit tag: ClassTag[T]) extends Config((site, here, up) => {
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
class RegisterBinder[T](fn: => (Clock, Bool, Bool, T) => Seq[Any])(implicit tag: ClassTag[T]) extends Config((site, here, up) => {
  case IOBinders => up(IOBinders, site) + (tag.runtimeClass.toString ->
      ((clock: Clock, reset: Bool, success: Bool, t: Any) => {
        t match {
          case top: T => fn(clock, reset, success, top) ++
            up(IOBinders, site).getOrElse(tag.runtimeClass.toString, (c: Clock, r: Bool, s: Bool, t: Any) => Nil)(clock, reset, success, top)
        }
      })
  )
})

class WithGPIOTiedOff extends RegisterIOBinder({
  (c, r, s, top: HasPeripheryGPIOModuleImp) => top.gpio.map(gpio => gpio.pins.map(p => p.i.ival := false.B)); Nil
})

class WithSimBlockDevice extends RegisterIOBinder({
  (c, r, s, top: CanHavePeripheryBlockDeviceModuleImp) => top.connectSimBlockDevice(c, r); Nil
})

class WithBlockDeviceModel extends RegisterIOBinder({
  (c, r, s, top: CanHavePeripheryBlockDeviceModuleImp) => top.connectBlockDeviceModel(); Nil
})

class WithLoopbackNIC extends RegisterIOBinder({
  (c, r, s, top: CanHavePeripheryIceNICModuleImp) => top.connectNicLoopback(); Nil
})

class WithUARTAdapter extends RegisterIOBinder({
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

class WithSimAXIMem extends RegisterIOBinder({
  (c, r, s, top: CanHaveMasterAXI4MemPortModuleImp) => top.connectSimAXIMem(); Nil
})

class WithSimAXIMMIO extends RegisterIOBinder({
  (c, r, s, top: CanHaveMasterAXI4MMIOPortModuleImp) => top.connectSimAXIMMIO(); Nil
})

class WithDontTouchPorts extends RegisterIOBinder({
  (c, r, s, top: DontTouch) => top.dontTouchPorts(); Nil
})

class WithTieOffInterrupts extends RegisterIOBinder({
  (c, r, s, top: HasExtInterruptsBundle) => top.tieOffInterrupts(); Nil
})

class WithTieOffL2FBusAXI extends RegisterIOBinder({
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

class WithTiedOffDebug extends RegisterIOBinder({
  (c, r, s, top: HasPeripheryDebugModuleImp) => {
    Debug.tieoffDebug(top.debug, top.psd)
    // tieoffDebug doesn't actually tie everything off :/
    top.debug.foreach(_.clockeddmi.foreach({ cdmi => cdmi.dmi.req.bits := DontCare }))
    Nil
  }
})

class WithSimSerial extends RegisterIOBinder({
  (c, r, s, top: CanHavePeripherySerialModuleImp) => {
    val ser_success = top.connectSimSerial()
    when (ser_success) { s := true.B }
    Nil
  }
})

class WithTiedOffSerial extends RegisterIOBinder({
  (c, r, s, top: CanHavePeripherySerialModuleImp) => top.tieoffSerial(); Nil
})


class WithSimDTM extends RegisterIOBinder({
  (c, r, s, top: HasPeripheryDebugModuleImp) => {
    val dtm_success = Wire(Bool())
    top.reset := r | top.debug.map { debug => AsyncResetReg(debug.ndreset) }.getOrElse(false.B)
    Debug.connectDebug(top.debug, top.psd, c, r, dtm_success)(top.p)
    when (dtm_success) { s := true.B }
    Nil
  }
})


class WithTraceGenSuccessBinder extends RegisterIOBinder({
  (c, r, s, top: HasTraceGenTilesModuleImp) => s := top.success; Nil
})
