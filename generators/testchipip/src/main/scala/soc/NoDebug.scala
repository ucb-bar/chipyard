package testchipip.soc

import chisel3._
import freechips.rocketchip.subsystem.BaseSubsystem
import freechips.rocketchip.devices.debug.HasPeripheryDebug
import org.chipsalliance.cde.config.Parameters

trait HasNoDebug extends HasPeripheryDebug { this: BaseSubsystem =>
}

trait HasNoDebugModuleImp {
  implicit val p: Parameters
  val outer: HasNoDebug
  val debugIO = outer.debugOpt.get.module.io.dmi
  val clock: Clock
  val reset: Reset

  debugIO.get.dmi.req.valid := false.B
  debugIO.get.dmi.resp.ready := false.B
  debugIO.get.dmiClock := clock
  debugIO.get.dmiReset := reset.asBool
}
