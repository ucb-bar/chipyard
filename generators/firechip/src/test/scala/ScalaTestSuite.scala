//See LICENSE for license details.
package firesim.firesim

import java.io.File

import scala.concurrent.{Future, Await, ExecutionContext}
import scala.sys.process.{stringSeqToProcess, ProcessLogger}
import scala.io.Source
import org.scalatest.Suites

import freechips.rocketchip.diplomacy._
import freechips.rocketchip.system.{RocketTestSuite, BenchmarkTestSuite}
import freechips.rocketchip.system.TestGeneration._
import freechips.rocketchip.system.DefaultTestSuites._

abstract class FireSimTestSuite(
    topModuleClass: String,
    targetConfigs: String,
    platformConfigs: String,
    N: Int = 8
  ) extends firesim.TestSuiteCommon {
  import scala.concurrent.duration._
  import ExecutionContext.Implicits.global

  val topModuleProject = "firesim.firesim"

  val chipyardLongName = topModuleProject + "." + topModuleClass + "." + targetConfigs

  // From TestSuiteCommon
  val targetTuple = s"$topModuleClass-$targetConfigs-$platformConfigs"
  val commonMakeArgs = Seq(s"DESIGN=${topModuleClass}",
                           s"TARGET_CONFIG=${targetConfigs}",
                           s"PLATFORM_CONFIG=${platformConfigs}")

  override lazy val genDir  = new File(firesimDir, s"generated-src/${chipyardLongName}")


  def invokeMlSimulator(backend: String, name: String, debug: Boolean, additionalArgs: Seq[String] = Nil) = {
    make((Seq(s"${outDir.getAbsolutePath}/${name}.%s".format(if (debug) "vpd" else "out"),
              s"EMUL=${backend}")
         ++ additionalArgs):_*)
  }

  def runTest(backend: String, name: String, debug: Boolean, additionalArgs: Seq[String] = Nil) = {
    compileMlSimulator(backend, debug)
    if (isCmdAvailable(backend)) {
      it should s"pass in ML simulation on ${backend}" in {
        assert(invokeMlSimulator(backend, name, debug, additionalArgs) == 0)
      }
    }
  }

  def runSuite(backend: String, debug: Boolean = false)(suite: RocketTestSuite) {
    // compile emulators
    behavior of s"${suite.makeTargetName} running on $backend"
    if (isCmdAvailable(backend)) {
      val postfix = suite match {
        case _: BenchmarkTestSuite | _: BlockdevTestSuite | _: NICTestSuite => ".riscv"
        case _ => ""
      }
      it should s"pass all tests in ${suite.makeTargetName}" in {
        val results = suite.names.toSeq sliding (N, N) map { t =>
          val subresults = t map (name =>
            Future(name -> invokeMlSimulator(backend, s"$name$postfix", debug)))
          Await result (Future sequence subresults, Duration.Inf)
        }
        results.flatten foreach { case (name, exitcode) =>
          assert(exitcode == 0, s"Failed $name")
        }
      }
    } else {
      ignore should s"pass $backend"
    }
  }

  // Checks the collected trace log matches the behavior of a chisel printf
  def diffTracelog(verilatedLog: String) {
    behavior of "captured instruction trace"
    it should s"match the chisel printf in ${verilatedLog}" in {
      def getLines(file: File): Seq[String] = Source.fromFile(file).getLines.toList

      val printfPrefix =  "TRACEPORT 0: "
      val verilatedOutput  = getLines(new File(outDir,  s"/${verilatedLog}")).collect({
        case line if line.startsWith(printfPrefix) => line.stripPrefix(printfPrefix) })

      // Last bit indicates the core was under reset; reject those tokens
      // Tail to drop the first token which is initialized in the channel
      val synthPrintOutput = getLines(new File(genDir, s"/TRACEFILE")).tail.filter(line =>
        (line.last.toInt & 1) == 0)

      assert(math.abs(verilatedOutput.size - synthPrintOutput.size) <= 1,
        s"\nPrintf Length: ${verilatedOutput.size}, Trace Length: ${synthPrintOutput.size}")
      assert(verilatedOutput.nonEmpty)
      for ( (vPrint, sPrint) <- verilatedOutput.zip(synthPrintOutput) ) {
        assert(vPrint == sPrint)
      }
    }
  }

  mkdirs
  behavior of s"Tuple: ${targetTuple}"
  elaborateAndCompile()
  runTest("verilator", "rv64ui-p-simple", false, Seq(s"""EXTRA_SIM_ARGS=+trace-humanreadable0"""))
  runSuite("verilator")(benchmarks)
}

class RocketF1Tests extends FireSimTestSuite("FireSim", "DDR3FRFCFSLLC4MB_FireSimQuadRocketConfig", "WithSynthAsserts_BaseF1Config")
class BoomF1Tests extends FireSimTestSuite("FireSim", "DDR3FRFCFSLLC4MB_FireSimLargeBoomConfig", "BaseF1Config")
class RocketNICF1Tests extends FireSimTestSuite("FireSim", "WithNIC_DDR3FRFCFSLLC4MB_FireSimRocketConfig", "BaseF1Config")

class CVA6F1Tests extends FireSimTestSuite("FireSim", "WithNIC_DDR3FRFCFSLLC4MB_FireSimCVA6Config", "BaseF1Config")

// This test suite only mirrors what is run in CI. CI invokes each test individually, using a testOnly call.
class CITests extends Suites(
  new RocketF1Tests,
  new BoomF1Tests,
  new RocketNICF1Tests)
