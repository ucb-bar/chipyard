
package chipyard.clocking

import chisel3._

import freechips.rocketchip.config.{Parameters}
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.prci._
import freechips.rocketchip.util.{ResetCatchAndSync}

/**
  * Instantiates a reset synchronizer on all clock-reset pairs in a clock group
  */
class ClockGroupResetSynchronizer(implicit p: Parameters) extends LazyModule {
  val node = ClockGroupAdapterNode()
  lazy val module = new LazyRawModuleImp(this) {
    (node.out zip node.in).map { case ((oG, _), (iG, _)) =>
      (oG.member.data zip iG.member.data).foreach { case (o, i) =>
        o.clock := i.clock
        o.reset := ResetCatchAndSync(i.clock, i.reset.asBool)
      }
    }
  }
}

object ClockGroupResetSynchronizer {
  def apply()(implicit p: Parameters, valName: ValName) = LazyModule(new ClockGroupResetSynchronizer()).node
}


