package chipyard.clocking

import chisel3._

import freechips.rocketchip.config.{Parameters}
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.prci._
import freechips.rocketchip.util.ElaborationArtefacts

import scala.collection.mutable
import scala.collection.immutable.ListMap

/**
  * TODO: figure out how much division is acceptable in our simulators and redefine this.
  */
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

class SimplePllConfiguration(pllName: String, val sinks: Seq[ClockSinkParameters]) {
  val referenceFreqMHz = FrequencyUtils.computeReferenceFrequencyMHz(sinks.flatMap(_.take)).freqMHz
  val sinkDividerMap = ListMap((sinks.map({s => (s, Math.round(referenceFreqMHz / s.take.get.freqMHz).toInt) })):_*)

  private val preamble = s"""
    |${pllName} Frequency Summary
    |  Input Reference Frequency: ${referenceFreqMHz} MHz\n""".stripMargin
  private val outputSummaries = sinkDividerMap.map { case (sink, division) =>
      val requested = sink.take.get.freqMHz
      val actual = referenceFreqMHz / division.toDouble
      s"  Output clock ${sink.name.get}, requested: ${requested} MHz, actual: ${actual} MHz (division of ${division})"
    }

   val summaryString =  preamble + outputSummaries.mkString("\n")
   ElaborationArtefacts.add(s"${pllName}.freq-summary", summaryString)
   println(summaryString)
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

/**
  * Generates a digital-divider-only PLL model that verilator can simulate.
  * Inspects all take-specified frequencies in the output ClockGroup, calculates a
  * fast reference clock (roughly LCM(requested frequencies)) which is passed up the
  * diplomatic graph, and then generates dividers for each unique requested
  * frequency.
  */

class IdealizedPLL(pllName: String)(implicit p: Parameters, valName: ValName) extends LazyModule {
  val node = IdealizedPLLNode(pllName)

  lazy val module = new LazyRawModuleImp(this) {
    require(node.out.size == 1, "Idealized PLL expects to generate a single output clock group. Use a ClockGroupAggregator")
    val (refClock, ClockEdgeParameters(_, refSinkParam, _, _)) = node.in.head
    val (outClocks, ClockGroupEdgeParameters(_, outSinkParams,  _, _)) = node.out.head

    val referenceFreq = refSinkParam.take.get.freqMHz
    val pllConfig = new SimplePllConfiguration(pllName, outSinkParams.members)

    val dividedClocks = mutable.HashMap[Int, Clock]()
    def instantiateDivider(div: Int): Clock = {
      val divider = Module(new ClockDividerN(div))
      divider.suggestName(s"ClockDivideBy${div}")
      divider.io.clk_in := refClock.clock
      dividedClocks(div) = divider.io.clk_out
      divider.io.clk_out
    }

    for (((sinkBName, sinkB), sinkP) <- outClocks.member.elements.zip(outSinkParams.members)) {
      val div = pllConfig.sinkDividerMap(sinkP)
      sinkB.clock := dividedClocks.getOrElse(div, instantiateDivider(div))
      sinkB.reset := refClock.reset
    }
  }
}

object IdealizedPLL {
  def apply()(implicit p: Parameters, valName: ValName) = LazyModule(new IdealizedPLL(valName.name)).node
}
