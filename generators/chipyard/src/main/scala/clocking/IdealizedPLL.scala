package chipyard.clocking

import chisel3._

import freechips.rocketchip.config.{Parameters}
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.prci._

import scala.collection.mutable

object FrequencyUtils {
  def computeReferenceFrequencyMHz(
    requestedOutputs: Seq[ClockParameters],
    maximumAllowableDivisor: Int = 0xFFFF): ClockParameters = {
    require(requestedOutputs.nonEmpty)
    require(!requestedOutputs.contains(0.0))
    val freqs = requestedOutputs.map(f => BigInt(Math.round(f.freqMHz * 1000 * 1000)))
    val refFreq = freqs.reduce((a, b) => a * b / a.gcd(b)).toDouble / (1000 * 1000)
    assert((refFreq / freqs.min.toDouble) < maximumAllowableDivisor.toDouble)
    ClockParameters(refFreq)
  }
}

case class IdealizedPLLNode(pllName: String)(implicit valName: ValName)
  extends MixedNexusNode(ClockImp, ClockGroupImp)(
    dFn = { _ => ClockGroupSourceParameters() },
    uFn = { u =>
    require(u.size == 1)
    require(!u.head.members.contains(None),
      "All output clocks in group must set their take parameters. Use a ClockGroupDealiaser")
    ClockSinkParameters(
      name = Some(s"${pllName}_reference_input"),
      take = Some(FrequencyUtils.computeReferenceFrequencyMHz(u.head.members.flatMap(_.take)))) }
  )

class IdealizedPLL(pllName: String)(implicit p: Parameters, valName: ValName) extends LazyModule {
  val node = IdealizedPLLNode(pllName)

  lazy val module = new LazyRawModuleImp(this) {
    require(node.out.size == 1, "Must use a ClockGroupAggregator")
    val (refClock, ClockEdgeParameters(_, refSinkParam, _, _)) = node.in.head
    val (outClocks, ClockGroupEdgeParameters(_, outSinkParams,  _, _)) = node.out.head

    val referenceFreq = refSinkParam.take.get.freqMHz
    val requestedFreqs = outSinkParams.members.map(m => m.name -> m.take)

    val dividedClocks = mutable.HashMap[Int, Clock]()
    def instantiateDivider(div: Int): Clock = {
      val divider = Module(new ClockDividerN(div))
      divider.suggestName(s"ClockDivideBy${div}")
      divider.io.clk_in := refClock.clock
      dividedClocks(div) = divider.io.clk_out
      divider.io.clk_out
    }

    for (((sinkBName, sinkB), sinkP) <- outClocks.member.elements.zip(outSinkParams.members)) {
      val requested = sinkP.take.get.freqMHz
      val div = Math.round(referenceFreq / requested).toInt
      val actual = referenceFreq / div.toDouble
      println(s"Clock ${sinkBName}, requested freq: ${requested} MHz. Actual freq: ${actual} MHz via division of ${div}")
      sinkB.clock := dividedClocks.getOrElse(div, instantiateDivider(div))
    }
  }
}

object IdealizedPLL {
  def apply()(implicit p: Parameters, valName: ValName) = LazyModule(new IdealizedPLL(valName.name)).node
}
