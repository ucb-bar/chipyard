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

import barstools.iocell.chisel.{AnalogConst}

import chipyard.chiptop._
import chipyard.TestHarnessUtils
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

class WithChipTopSimpleClockAndReset extends OverrideIOBinder({
  (c, r, s, top: HasChipTopSimpleClockAndReset) => {
    top.clock := c
    top.reset := r
    Nil
  }
})

class WithGPIOTiedOff extends Config(new OverrideIOBinder({
  (c, r, s, top: HasPeripheryGPIOModuleImp) => top.gpio.foreach(_.pins.foreach(_.i.ival := false.B)); Nil
}) ++ new OverrideIOBinder({
  (c, r, s, top: CanHaveChipTopGPIO) => top.gpio.flatten.foreach(_ <> AnalogConst(0)); Nil
}))

class WithSimBlockDevice extends Config(new OverrideIOBinder({
  (c, r, s, top: CanHavePeripheryBlockDeviceModuleImp) => SimBlockDevice.connect(c, r, top.bdev)(top.p); Nil
}) ++ new OverrideIOBinder({
  (c, r, s, top: CanHaveChipTopBlockDevice) => SimBlockDevice.connect(c, r, top.bdev)(top.p); Nil
}))

class WithBlockDeviceModel extends Config(new OverrideIOBinder({
  (c, r, s, top: CanHavePeripheryBlockDeviceModuleImp) => BlockDeviceModel.connect(top.bdev)(top.p); Nil
}) ++ new OverrideIOBinder({
  (c, r, s, top: CanHaveChipTopBlockDevice) => BlockDeviceModel.connect(top.bdev)(top.p); Nil
}))

class WithLoopbackNIC extends Config(new OverrideIOBinder({
  (c, r, s, top: CanHavePeripheryIceNICModuleImp) => NicLoopback.connect(top.net, top.nicConf); Nil
}) ++ new OverrideIOBinder({
  (c, r, s, top: CanHaveChipTopIceNIC) => NicLoopback.connect(top.net, top.nicConf); Nil
}))

class WithSimNIC extends Config(new OverrideIOBinder({
  (c, r, s, top: CanHavePeripheryIceNICModuleImp) => SimNetwork.connect(top.net, c, r); Nil
}) ++ new OverrideIOBinder({
  (c, r, s, top: CanHaveChipTopIceNIC) => SimNetwork.connect(top.net, c, r); Nil
}))

class WithUARTAdapter extends Config(new OverrideIOBinder({
  (c, r, s, top: HasPeripheryUARTModuleImp) => UARTAdapter.connect(top.uart)(top.p); Nil
}) ++ new OverrideIOBinder({
  (c, r, s, top: CanHaveChipTopUART) => UARTAdapter.connect(top.uart)(top.p); Nil
}))

// DOC include start: WithSimAXIMem
class WithSimAXIMem extends OverrideIOBinder({
  (c, r, s, top: CanHaveMasterAXI4MemPortModuleImp) => top.connectSimAXIMem(); Nil
})
// Note: No equivalent ChipTop binder, as you generally wouldn't expose AXI4 at the chip level in a real system
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
// Note: No equivalent ChipTop binder, as you generally wouldn't expose AXI4 at the chip level in a real system

class WithSimAXIMMIO extends OverrideIOBinder({
  (c, r, s, top: CanHaveMasterAXI4MMIOPortModuleImp) => top.connectSimAXIMMIO(); Nil
})
// Note: No equivalent ChipTop binder, as you generally wouldn't expose AXI4 at the chip level in a real system

class WithDontTouchPorts extends OverrideIOBinder({
  (c, r, s, top: DontTouch) => top.dontTouchPorts(); Nil
})

class WithTieOffInterrupts extends Config(new OverrideIOBinder({
  (c, r, s, top: HasExtInterruptsBundle) => top.tieOffInterrupts(); Nil
}) ++ new OverrideIOBinder({
  (c, r, s, top: CanHaveChipTopExtInterrupts) => top.tieOffInterrupts(); Nil
}))

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
        case _ => throw new Exception("Unknown AXI port direction")
      }
    })
    Nil
  }
})
// Note: No equivalent ChipTop binder, as you generally wouldn't expose AXI4 at the chip level in a real system

class WithTiedOffDebug extends Config(new OverrideIOBinder({
  (c, r, s, top: HasPeripheryDebugModuleImp) => TestHarnessUtils.tieoffDebug(c, r, top.debug, top.psd); Nil
}) ++ new OverrideIOBinder({
  (c, r, s, top: CanHaveChipTopDebug) => top.psd.foreach { psd => TestHarnessUtils.tieoffDebug(c, r, top.debug, psd) }; Nil
}))

class WithSimSerial extends Config(new OverrideIOBinder({
  (c, r, s, top: CanHavePeripherySerialModuleImp) => {
    val ser_success = top.connectSimSerial()
    when (ser_success) { s := true.B }
    Nil
  }
}) ++ new OverrideIOBinder({
  (c, r, s, top: CanHaveChipTopSerial) => {
    val ser_success = top.connectSimSerial(c, r)
    when (ser_success) { s := true.B }
    Nil
  }
}))

class WithTiedOffSerial extends Config(new OverrideIOBinder({
  (c, r, s, top: CanHavePeripherySerialModuleImp) => top.tieoffSerial(); Nil
}) ++ new OverrideIOBinder({
  (c, r, s, top: CanHaveChipTopSerial) => top.tieoffSerial(); Nil
}))

class WithSimDebug extends Config(new OverrideIOBinder({
  (c, r, s, top: HasPeripheryDebugModuleImp) => top.reset := TestHarnessUtils.connectSimDebug(c, r, s, top.debug, top.psd)(top.p); Nil
}) ++ new OverrideIOBinder({
  (c, r, s, top: CanHaveChipTopDebug) => top.psd.foreach { psd =>
    top.asInstanceOf[BaseChipTop].connectReset(TestHarnessUtils.connectSimDebug(c, r, s, top.debug, psd)(top.p))
  }; Nil
}))

class WithTraceGenSuccessBinder extends Config(new OverrideIOBinder({
  (c, r, s, top: HasTraceGenTilesModuleImp) => when (top.success) { s := true.B };  Nil
}) ++ new OverrideIOBinder({
  (c, r, s, top: CanHaveChipTopTraceGen) => when (top.success) { s := true.B };  Nil
}))

