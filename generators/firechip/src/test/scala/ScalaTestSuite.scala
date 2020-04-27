//See LICENSE for license details.
package firesim.firesim

import java.io.File

import scala.concurrent.{Future, Await, ExecutionContext}
import scala.sys.process.{stringSeqToProcess, ProcessLogger}
import scala.io.Source

import freechips.rocketchip.diplomacy._
import freechips.rocketchip.system.{RocketTestSuite, BenchmarkTestSuite}
import freechips.rocketchip.system.TestGeneration._
import freechips.rocketchip.system.DefaultTestSuites._

import firesim.util.GeneratorArgs

abstract class FireSimTestSuite(
    topModuleClass: String,
    targetConfigs: String,
    platformConfigs: String,
    N: Int = 8
  ) extends firesim.TestSuiteCommon with IsFireSimGeneratorLike {
  import scala.concurrent.duration._
  import ExecutionContext.Implicits.global

  val longName = names.topModuleProject + "." + names.topModuleClass + "." + names.configs

  lazy val generatorArgs = GeneratorArgs(
    midasFlowKind = "midas",
    targetDir = "generated-src",
    topModuleProject = "firesim.firesim",
    topModuleClass = topModuleClass,
    targetConfigProject = "firesim.firesim",
    targetConfigs = targetConfigs ++ "_WithScalaTestFeatures",
    platformConfigProject = "firesim.firesim",
    platformConfigs = platformConfigs)

  // From HasFireSimGeneratorUtilities
  // For the firesim utilities to use the same directory as the test suite
  override lazy val testDir = genDir

  // From TestSuiteCommon
  val targetTuple = generatorArgs.tupleName
  val commonMakeArgs = Seq(s"DESIGN=${generatorArgs.topModuleClass}",
                           s"TARGET_CONFIG=${generatorArgs.targetConfigs}",
                           s"PLATFORM_CONFIG=${generatorArgs.platformConfigs}")

  def invokeMlSimulator(backend: String, name: String, debug: Boolean, additionalArgs: Seq[String] = Nil) = {
    make((Seq(s"${outDir.getAbsolutePath}/${name}.%s".format(if (debug) "vpd" else "out"),
              s"EMUL=${backend}")
         ++ additionalArgs):_*)
  }

  def runTest(backend: String, name: String, debug: Boolean, additionalArgs: Seq[String] = Nil) = {
    behavior of s"${name} running on ${backend} in MIDAS-level simulation"
    compileMlSimulator(backend, debug)
    if (isCmdAvailable(backend)) {
      it should s"pass" in {
        assert(invokeMlSimulator(backend, name, debug, additionalArgs) == 0)
      }
    }
  }

  //def runReplay(backend: String, replayBackend: String, name: String) = {
  //  val dir = (new File(outDir, backend)).getAbsolutePath
  //  (Seq("make", s"replay-$replayBackend",
  //       s"SAMPLE=${dir}/${name}.sample", s"output_dir=$dir") ++ makeArgs).!
  //}

  def runSuite(backend: String, debug: Boolean = false)(suite: RocketTestSuite) {
    // compile emulators
    behavior of s"${suite.makeTargetName} running on $backend"
    if (isCmdAvailable(backend)) {
      val postfix = suite match {
        case _: BenchmarkTestSuite | _: BlockdevTestSuite | _: NICTestSuite => ".riscv"
        case _ => ""
      }
      val results = suite.names.toSeq sliding (N, N) map { t => 
        val subresults = t map (name =>
          Future(name -> invokeMlSimulator(backend, s"$name$postfix", debug)))
        Await result (Future sequence subresults, Duration.Inf)
      }
      results.flatten foreach { case (name, exitcode) =>
        it should s"pass $name" in { assert(exitcode == 0) }
      }
      //replayBackends foreach { replayBackend =>
      //  if (platformParams(midas.EnableSnapshot) && isCmdAvailable("vcs")) {
      //    assert((Seq("make", s"vcs-$replayBackend") ++ makeArgs).! == 0) // compile vcs
      //    suite.names foreach { name =>
      //      it should s"replay $name in $replayBackend" in {
      //        assert(runReplay(backend, replayBackend, s"$name$postfix") == 0)
      //      }
      //    }
      //  } else {
      //    suite.names foreach { name =>
      //      ignore should s"replay $name in $backend"
      //    }
      //  }
      //}
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

  clean
  mkdirs
  elaborate
  generateTestSuiteMakefrags
  runTest("verilator", "rv64ui-p-simple", false, Seq(s"""EXTRA_SIM_ARGS=+trace-humanreadable0"""))
  //diffTracelog("rv64ui-p-simple.out")
  runSuite("verilator")(benchmarks)
  runSuite("verilator")(FastBlockdevTests)
}

class RocketF1Tests extends FireSimTestSuite("FireSim", "DDR3FRFCFSLLC4MB_FireSimQuadRocketConfig", "WithSynthAsserts_BaseF1Config")
class BoomF1Tests extends FireSimTestSuite("FireSim", "DDR3FRFCFSLLC4MB_FireSimLargeBoomConfig", "BaseF1Config")
class RocketNICF1Tests extends FireSimTestSuite("FireSim", "WithNIC_DDR3FRFCFSLLC4MB_FireSimRocketConfig", "BaseF1Config") {
  runSuite("verilator")(NICLoopbackTests)
}
class ArianeF1Tests extends FireSimTestSuite("FireSim", "WithNIC_DDR3FRFCFSLLC4MB_FireSimArianeConfig", "BaseF1Config") {
  runSuite("verilator")(NICLoopbackTests)
}
// Disabled until RAM optimizations re-enabled in multiclock
//class RamModelRocketF1Tests extends FireSimTestSuite("FireSim", "FireSimDualRocketConfig", "BaseF1Config_MCRams")
//class RamModelBoomF1Tests extends FireSimTestSuite("FireSim", "FireSimBoomConfig", "BaseF1Config_MCRams")

// Multiclock tests
class RocketMulticlockF1Tests extends FireSimTestSuite(
  "FireSimMulticlockPOC",
  "FireSimQuadRocketMulticlockConfig",
  "WithSynthAsserts_BaseF1Config")

// Jerry broke these -- damn it Jerry.
//abstract class FireSimTraceGenTest(targetConfig: String, platformConfig: String)
//    extends firesim.TestSuiteCommon with IsFireSimGeneratorLike {
//  val longName = names.topModuleProject + "." + names.topModuleClass + "." + names.configs
//
//  lazy val generatorArgs = GeneratorArgs(
//    midasFlowKind = "midas",
//    targetDir = "generated-src",
//    topModuleProject = "firesim.firesim",
//    topModuleClass = "FireSimTraceGen",
//    targetConfigProject = "firesim.firesim",
//    targetConfigs = targetConfig ++ "_WithScalaTestFeatures",
//    platformConfigProject = "firesim.firesim",
//    platformConfigs = platformConfig)
//
//  // From HasFireSimGeneratorUtilities
//  // For the firesim utilities to use the same directory as the test suite
//  override lazy val testDir = genDir
//
//  // From TestSuiteCommon
//  val targetTuple = generatorArgs.tupleName
//  val commonMakeArgs = Seq(s"DESIGN=${generatorArgs.topModuleClass}",
//                           s"TARGET_CONFIG=${generatorArgs.targetConfigs}",
//                           s"PLATFORM_CONFIG=${generatorArgs.platformConfigs}")
//
//  it should "pass" in {
//    assert(make("fsim-tracegen") == 0)
//  }
//}
//
//class FireSimLLCTraceGenTest extends FireSimTraceGenTest(
//  "DDR3FRFCFSLLC4MB_FireSimTraceGenConfig", "BaseF1Config")
//
//class FireSimL2TraceGenTest extends FireSimTraceGenTest(
//  "DDR3FRFCFS_FireSimTraceGenL2Config", "BaseF1Config")
