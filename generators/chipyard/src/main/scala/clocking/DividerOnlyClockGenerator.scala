package chipyard.clocking

import chisel3._

import org.chipsalliance.cde.config.{Parameters}
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
    require(fastestFreq <= maximumAllowableFreqMHz, s"Fastest Freq $fastestFreq > Max Freq $maximumAllowableFreqMHz")

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
