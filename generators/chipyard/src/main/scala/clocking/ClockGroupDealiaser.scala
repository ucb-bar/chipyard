package chipyard.clocking

import chisel3._

import freechips.rocketchip.config.{Parameters}
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.prci._

/**
  * Somewhat hacky. Since not all clocks in a clock group specify a taken frequency
  * current, this LazyModule attempts to dealias them, by finding a specified
  * clock whose name has the longest matching prefix.
  *
  * Perhaps another, simpler solution would be to pass a default.
  *
  */

case class ClockGroupDealiaserNode()(implicit valName: ValName)
  extends NexusNode(ClockGroupImp)(
    dFn = { _ => ClockGroupSourceParameters() },
    uFn = { u =>
      require(u.size == 1)
      val takenClocks = u.head.members.filter(_.take.nonEmpty)
      require(takenClocks.nonEmpty,
        "At least one sink clock in clock group must specify its take parameter")
      u.head.copy(members = takenClocks)
    })

class ClockGroupDealiaser(name: String)(implicit p: Parameters) extends LazyModule {
  val node = ClockGroupDealiaserNode()

  lazy val module = new LazyRawModuleImp(this) {
    require(node.out.size == 1, "Must use a ClockGroupAggregator")
    val (outClocks, e @ ClockGroupEdgeParameters(_, outSinkParams,  _, _)) = node.out.head
    val (inClocks, ClockGroupEdgeParameters(_, inSinkParams,  _, _)) = node.in.head
    val inMap = inClocks.member.data.zip(inSinkParams.members).map({ case (b, p) => p.name -> b}).toMap

    for (((outBName, outB), outName) <- outClocks.member.elements.zip(outSinkParams.members.map(_.name))) {
      val inClock = inMap.getOrElse(outName, throw new Exception("""
          | No clock in input group with name: Option matching ${outName}. At least one clock
          | with the same must specify a frequency in its take parameter.""".stripMargin))
      // This will be removed.
      dontTouch(outB)
      outB := inClock
    }
  }
}

object ClockGroupDealiaser {
  def apply()(implicit p: Parameters, valName: ValName) = LazyModule(new ClockGroupDealiaser(valName.name)).node
}
