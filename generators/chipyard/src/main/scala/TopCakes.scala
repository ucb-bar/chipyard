package chipyard

import chisel3._

import freechips.rocketchip.subsystem.BaseSubsystem
import freechips.rocketchip.config.{Field}
import freechips.rocketchip.diplomacy.{LazyModule, AddressSet}
import freechips.rocketchip.tilelink.{TLRAM}

case class BackingScratchpadParams(
  base: BigInt,
  mask: BigInt)

case object BackingScratchpadKey extends Field[Option[BackingScratchpadParams]](None)

/**
 * Trait to add a scratchpad on the mbus
 */
trait CanHaveBackingScratchpad { this: BaseSubsystem =>
  private val portName = "Backing-Scratchpad"

  val spadOpt = p(BackingScratchpadKey).map { param =>
    val spad = LazyModule(new TLRAM(address=AddressSet(param.base, param.mask), beatBytes=mbus.beatBytes))
    mbus.toVariableWidthSlave(Some(portName)) { spad.node }
    spad
  }
}
