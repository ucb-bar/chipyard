// See LICENSE for license details.

package firechip.bridgestubs

import java.io._

import org.scalatest.Suites
import org.scalatest.matchers.should._

import firesim.{BasePlatformConfig}

abstract class TracerVTestBase(
  platformConfig: BasePlatformConfig,
  width:          Int,
  trace:          Option[Int]    = None,
  start:          Option[String] = None,
) extends BridgeSuite("TracerVModule", s"TracerVModuleInstrRetireCount${width}", platformConfig) {
  override def defineTests(backend: String, debug: Boolean) {
    it should "dump traced instructions" in {
      // Create an expected file.
      val expected = File.createTempFile("expected", ".txt")
      expected.deleteOnExit()

      // Create the output file. tracerv will always append '-C0' to the end of the path provided in the plusarg
      val output     = File.createTempFile("output", ".txt-C0")
      output.deleteOnExit()
      val outputPath = output.getPath.stripSuffix("-C0")

      // group the optional function args together with the correct plusarg string names
      val optionalArgs = Seq(
        ("+trace-select=", trace),
        ("+trace-start=", start),
      )

      // a seq starting with fixed plusargs, ending with optional plusargs
      // the optional plusargs are properly constructed or dropped
      val args = Seq(
        s"+tracefile=${outputPath}",
        s"+tracerv-expected-output=${expected.getPath}",
      ) ++ optionalArgs.collect { case (a, Some(b)) =>
        s"${a}${b}"
      }

      val runResult =
        run(backend, false, args = args)
      assert(runResult == 0)

      val expectedContents = scala.io.Source.fromFile(expected.getPath).mkString
      val outputContents   = scala.io.Source.fromFile(output.getPath).mkString
      outputContents should equal(expectedContents)
    }
  }
}

class TracerVF1TestCount1  extends TracerVTestBase(BaseConfigs.F1, 1);
// This test is disabled until FireSim issue #1428 is resolved
// class TracerVF1TestCount5 extends TracerVTestBase(BaseConfigs.F1, 5, Some(3), Some("FF0000001C")); // in hex
class TracerVF1TestCount6  extends TracerVTestBase(BaseConfigs.F1, 6, Some(2), Some("3000")); // in hex
class TracerVF1TestCount7  extends TracerVTestBase(BaseConfigs.F1, 7, Some(1), Some("9"));    // in decimal
class TracerVF1TestCount9  extends TracerVTestBase(BaseConfigs.F1, 9);
class TracerVF1TestCount14 extends TracerVTestBase(BaseConfigs.F1, 14, Some(1), Some("10"));  // in decimal
class TracerVF1TestCount15 extends TracerVTestBase(BaseConfigs.F1, 15, Some(2), Some("4000"));
