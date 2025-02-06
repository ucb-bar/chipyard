// See LICENSE for license details.

package firechip.chip

import java.io.File

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.sys.process.{stringSeqToProcess, ProcessLogger}
import scala.io.Source
import org.scalatest.Suites

import firesim.{BasePlatformConfig, TestSuiteCommon}

import freechips.rocketchip.diplomacy._
import freechips.rocketchip.system.{BenchmarkTestSuite, RocketTestSuite}
import freechips.rocketchip.system.TestGeneration._
import freechips.rocketchip.system.DefaultTestSuites._

object BaseConfigs {
  case object F1 extends BasePlatformConfig("f1", Seq("BaseF1Config"))
}

abstract class FireSimTestSuite(
  override val targetName:         String,
  override val targetConfigs:      String,
  override val basePlatformConfig: BasePlatformConfig,
  override val platformConfigs:    Seq[String] = Seq(),
  N:                               Int                     = 8,
) extends TestSuiteCommon("firesim") {
  import scala.concurrent.duration._
  import ExecutionContext.Implicits.global

  val chipyardDir = new File(System.getProperty("user.dir"))
  override val extraMakeArgs = Seq(s"TARGET_PROJECT_MAKEFRAG=$chipyardDir/generators/firechip/chip/src/main/makefrag/firesim")

  val topModuleProject = "firechip.chip"

  val chipyardLongName = topModuleProject + "." + targetName + "." + targetConfigs

  override lazy val genDir = new File(firesimDir, s"generated-src/${chipyardLongName}")

  def invokeMlSimulator(backend: String, name: String, debug: Boolean, additionalArgs: Seq[String] = Nil) = {
    make(
      (Seq(s"${outDir.getAbsolutePath}/${name}.%s".format(if (debug) "fsdb" else "out"), s"EMUL=${backend}")
        ++ additionalArgs): _*
    )
  }

  def runTest(backend: String, debug: Boolean, name: String, additionalArgs: Seq[String] = Nil) = {
    it should s"pass in ML simulation on ${backend}" in {
      assert(invokeMlSimulator(backend, name, debug, additionalArgs) == 0)
    }
  }

  def runSuite(backend: String, debug: Boolean)(suite: RocketTestSuite) {
    val postfix = suite match {
      case _: BenchmarkTestSuite | _: BlockdevTestSuite | _: NICTestSuite => ".riscv"
      case _                                                              => ""
    }
    it should s"pass all tests in ${suite.makeTargetName}" in {
      val results = suite.names.toSeq.sliding(N, N).map { t =>
        // minimal set of bmarks is for now only dhrystone as a smoke test
        val names = if (System.getenv("TEST_MINIMAL_BENCHMARKS") == null) {
          t
        }
        else {
          t.filter(_ == "dhrystone")
        }
        val subresults = names.map(name => Future(name -> invokeMlSimulator(backend, s"$name$postfix", debug)))
        Await.result(Future.sequence(subresults), Duration.Inf)
      }
      results.flatten.foreach { case (name, exitcode) =>
        assert(exitcode == 0, s"Failed $name")
      }
    }
  }

  override def defineTests(backend: String, debug: Boolean) {
    runTest(backend, debug, "rv64ui-p-simple", Seq(s"""EXTRA_SIM_ARGS=+trace-humanreadable0"""))
    if (System.getenv("TEST_DISABLE_BENCHMARKS") == null) {
      runSuite(backend, debug)(benchmarks)
    }
  }
}

class SimpleRocketF1Tests
    extends FireSimTestSuite(
      "FireSim",
      "FireSimRocketConfig",
      BaseConfigs.F1,
      Seq("FCFS16GBQuadRank"),
    )

class RocketF1Tests
    extends FireSimTestSuite(
      "FireSim",
      "FireSimQuadRocketConfig",
      BaseConfigs.F1,
      Seq("WithSynthAsserts", "FRFCFS16GBQuadRankLLC4MB"),
    )

class MultiRocketF1Tests
    extends FireSimTestSuite(
      "FireSim",
      "FireSimQuadRocketConfig",
      BaseConfigs.F1,
      Seq("WithSynthAsserts", "WithModelMultiThreading", "FRFCFS16GBQuadRankLLC4MB"),
    )

class BoomF1Tests
    extends FireSimTestSuite(
      "FireSim",
      "FireSimLargeBoomConfig",
      BaseConfigs.F1,
      Seq("FRFCFS16GBQuadRankLLC4MB"),
    )

class RocketNICF1Tests
    extends FireSimTestSuite(
      "FireSim",
      "WithNIC_FireSimRocketConfig",
      BaseConfigs.F1,
      Seq("FRFCFS16GBQuadRankLLC4MB"),
    )

class CVA6F1Tests
    extends FireSimTestSuite(
      "FireSim",
      "WithNIC_FireSimCVA6Config",
      BaseConfigs.F1,
      Seq("FRFCFS16GBQuadRankLLC4MB"),
    )

class CITests
    extends Suites(
      new SimpleRocketF1Tests,
      new RocketF1Tests,
      new MultiRocketF1Tests,
      new BoomF1Tests,
      new RocketNICF1Tests,
    )
