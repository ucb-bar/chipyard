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
  /**
    * Adds up the squared error between the generated clocks (refClock / [integer] divider)
    * and the requested frequencies.
    *
    * @param refMHz The candidate reference clock
    * @param desiredFreqMHz A list of the requested output frequencies
    */
  def squaredError(refMHz: Double, desiredFreqMHz: List[Double], sum: Double = 0.0): Double = desiredFreqMHz match {
    case Nil => sum
    case desired :: xs =>
      val divider = Math.round(refMHz / desired)
      val termError = ((refMHz / divider) - desired) / desired
      squaredError(refMHz, xs, sum + termError * termError)
  }

  /**
    * Picks a candidate reference frequency by doing a brute-force search over
    * multiples of the fastest requested clock. Choose the smallest multiple that
    * has an RMS error (across all output frequencies) that is:
    * 1) zero or failing that,
    * 2) is within the relativeThreshold of the best or is less than the absoluteThreshold
    *
    * @param requestedOutputs The desired output frequencies in MHz
    * @param maximumAllowableFreqMHz The maximum allowable reference in MHz
    * @param relativeThreshold See above
    * @param absoluteThreshold See above
    */
  def computeReferenceAsMultipleOfFastestClock(
    requestedOutputs: Seq[ClockParameters],
    maximumAllowableFreqMHz: Double,
    relativeThreshold: Double = 1.10,
    absoluteThreshold: Double = 0.01): ClockParameters = {

    require(requestedOutputs.nonEmpty)
    require(!requestedOutputs.contains(0.0))
    val requestedFreqs = requestedOutputs.map(_.freqMHz)
    val fastestFreq = requestedFreqs.max
    require(fastestFreq <= maximumAllowableFreqMHz)

    val candidateFreqs =
      Seq.tabulate(Math.ceil(maximumAllowableFreqMHz / fastestFreq).toInt)(i => (i + 1) * fastestFreq)
    val errorTuples = candidateFreqs.map { f =>
      f -> Math.sqrt(squaredError(f, requestedFreqs.toList) / requestedFreqs.size)
    }
    val minError = errorTuples.map(_._2).min
    val viableFreqs = errorTuples.collect {
      case (f, error) if (error <= minError * relativeThreshold) || (minError > 0 && error < absoluteThreshold) => f
    }
    ClockParameters(viableFreqs.min)
  }
}

class SimplePllConfiguration(
    name: String,
    val sinks: Seq[ClockSinkParameters],
    maximumAllowableFreqMHz: Double = 16000.0 ) {
  val referenceFreqMHz = FrequencyUtils.computeReferenceAsMultipleOfFastestClock(
    sinks.flatMap(_.take),
    maximumAllowableFreqMHz).freqMHz
  val sinkDividerMap = ListMap((sinks.map({s => (s, Math.round(referenceFreqMHz / s.take.get.freqMHz).toInt) })):_*)

  private val preamble = s"""
    |${name} Frequency Summary
    |  Input Reference Frequency: ${referenceFreqMHz} MHz\n""".stripMargin
  private val outputSummaries = sinkDividerMap.map { case (sink, division) =>
      val requested = sink.take.get.freqMHz
      val actual = referenceFreqMHz / division.toDouble
      s"  Output clock ${sink.name.get}, requested: ${requested} MHz, actual: ${actual} MHz (division of ${division})"
    }

   val summaryString =  preamble + outputSummaries.mkString("\n")
   def emitSummaries(): Unit = {
     ElaborationArtefacts.add(s"${name}.freq-summary", summaryString)
     println(summaryString)
   }
   def referenceSinkParams(): ClockSinkParameters = sinkDividerMap.find(_._2 == 1).get._1
}

case class DividerOnlyClockGeneratorNode(pllName: String)(implicit valName: ValName)
  extends MixedNexusNode(ClockImp, ClockGroupImp)(
    dFn = { _ => ClockGroupSourceParameters() },
    uFn = { u =>
    require(u.size == 1)
    require(!u.head.members.contains(None),
      "All output clocks in group must set their take parameters. Use a ClockGroupDealiaser")
    ClockSinkParameters(
      name = Some(s"${pllName}_reference_input"),
      take = Some(ClockParameters(new SimplePllConfiguration(pllName, u.head.members).referenceFreqMHz))) }
  )

/**
  * Generates a digital-divider-only PLL model that verilator can simulate.
  * Inspects all take-specified frequencies in the output ClockGroup, calculates a
  * fast reference clock (roughly LCM(requested frequencies)) which is passed up the
  * diplomatic graph, and then generates dividers for each unique requested
  * frequency.
  *
  * Output resets are not synchronized to generated clocks and should be
  * synchronized by the user in a manner they see fit.
  *
  */

class DividerOnlyClockGenerator(pllName: String)(implicit p: Parameters, valName: ValName) extends LazyModule {
  val node = DividerOnlyClockGeneratorNode(pllName)

  lazy val module = new LazyRawModuleImp(this) {
    require(node.out.size == 1, "Idealized PLL expects to generate a single output clock group. Use a ClockGroupAggregator")
    val (refClock, ClockEdgeParameters(_, refSinkParam, _, _)) = node.in.head
    val (outClocks, ClockGroupEdgeParameters(_, outSinkParams,  _, _)) = node.out.head

    val referenceFreq = refSinkParam.take.get.freqMHz
    val pllConfig = new SimplePllConfiguration(pllName, outSinkParams.members)
    pllConfig.emitSummaries()

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
      // Reset handling and synchronization is expected to be handled by a downstream node
      sinkB.reset := refClock.reset
    }
  }
}
