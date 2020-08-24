
package chipyard.clocking

import freechips.rocketchip.diplomacy._
import freechips.rocketchip.config.{Parameters}
import freechips.rocketchip.subsystem._
import freechips.rocketchip.prci.{ClockNode, ClockTempNode, ClockAdapterNode, ClockParameters}
/**
  * An adapter node hack c that just throws out the existing sink node
  * clock parameters in favor of the provided ones.
  */
class ForceTakeClock(clockParams: Option[ClockParameters])(implicit p: Parameters, v: ValName) extends LazyModule {
  val node = ClockAdapterNode(sinkFn = { s => s.copy(take = clockParams) })
  lazy val module = new LazyRawModuleImp(this) {
    (node.out zip node.in) map { case ((o, _), (i, _)) => o := i }
  }
}

object ForceTakeClock {
  def apply(clockParams: Option[ClockParameters])(implicit p: Parameters, v: ValName): ClockAdapterNode =
    LazyModule(new ForceTakeClock(clockParams)).node
}

object ClockNodeInjectionUtils {
  type InjectClockNodeFunc = (Attachable, Parameters) => ClockNode
  val injectIdentityClockNode: InjectClockNodeFunc = (a: Attachable, p: Parameters) => ClockTempNode()
  def forceTakeFrequency(freqMHz: Double): InjectClockNodeFunc =
    (a: Attachable, p: Parameters) => ForceTakeClock(Some(ClockParameters(freqMHz)))(p, ValName("ForcedTakeClock"))
}
