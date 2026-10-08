// See LICENSE for license details.

package firechip.bridgestubs

import java.nio.file.{Files, Paths}

import scala.jdk.CollectionConverters._

import org.scalatest.matchers.should._

import firesim.BasePlatformConfig

object IntervalTraceTestBase {
  // Trigger endpoints placed in the stimulus by IntervalTraceModule.cc.
  val startPC = "80000400"
  val endPC = "80000800"
  val startInstruction = "ffffffff00008013"
  val endInstruction = "ffffffff00010013"
}

/** Runs IntervalTraceModule under each trigger mode and compares both bridges'
  * outputs with the files its C++ test derives from a model of the bridge.
  */
abstract class IntervalTraceTestBase(platformConfig: BasePlatformConfig, targetConfig: String)
    extends BridgeSuite("IntervalTraceModule", targetConfig, platformConfig) {
  import IntervalTraceTestBase._

  private def compare(base: String, bridge: Int): Unit = {
    def read(name: String): Seq[String] = Files.readAllLines(Paths.get(name)).asScala.toSeq
    def actual(suffix: String) = read(s"$base-$bridge$suffix")
    def expected(suffix: String) = read(s"$base-$bridge-expected$suffix")
    assert(expected("-bb.csv").size > 1, s"bridge $bridge captured no blocks")
    for (suffix <- Seq("-epochs.csv", "-pmu.csv", "-bb.csv", "-blocks.csv", "-intervals.jsonl")) {
      withClue(s"bridge $bridge $suffix: ") { actual(suffix) should equal(expected(suffix)) }
    }
  }

  override def defineTests(backend: String, debug: Boolean): Unit = {
    def capture(behavior: String, args: Seq[String]): Unit = it should behavior in {
      val base = Files.createTempDirectory("intervaltrace").resolve("intervaltrace").toString
      val result = run(backend, debug, args = Seq(s"+interval-trace-file=$base", "+interval-trace-csv",
        "+interval-trace-interval=5") ++ args)
      assert(result == 0)
      for (bridge <- 0 until 2) compare(base, bridge)
    }
    capture("capture every cycle and restart after target reset", Nil)
    capture("capture an inclusive cycle window",
      Seq("+interval-trace-select=1", "+interval-trace-start=12", "+interval-trace-end=60"))
    capture("capture between PC endpoints",
      Seq("+interval-trace-select=2", s"+interval-trace-start=$startPC", s"+interval-trace-end=$endPC"))
    capture("capture between masked instruction endpoints",
      Seq("+interval-trace-select=3", s"+interval-trace-start=$startInstruction", s"+interval-trace-end=$endInstruction"))
  }
}

class IntervalTraceU250Width1Test extends IntervalTraceTestBase(BaseConfigs.U250, "IntervalTraceModuleWidth1Events3")
class IntervalTraceU250Width4Test extends IntervalTraceTestBase(BaseConfigs.U250, "IntervalTraceModuleWidth4Events19")
