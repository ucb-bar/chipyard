// SPDX-License-Identifier: Apache-2.0

package barstools.tapeout.transforms

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

import java.io.{ByteArrayOutputStream, PrintStream}

class GenerateTopSpec extends AnyFreeSpec with Matchers {
  "Generate top and harness" - {
    "should include the following transforms" in {
      val buffer = new ByteArrayOutputStream()
      Console.withOut(new PrintStream(buffer)) {
        GenerateTopAndHarness.main(Array("-i", "ExampleModuleNeedsResetInverted.fir", "-ll", "info"))
      }
      val output = buffer.toString
      output should include("barstools.tapeout.transforms.AddSuffixToModuleNames")
      output should include("barstools.tapeout.transforms.ConvertToExtMod")
      output should include("barstools.tapeout.transforms.RemoveUnusedModules")
      output should include("barstools.tapeout.transforms.AvoidExtModuleCollisions")
      println(output)
    }
  }

  "generate harness should " in {
    val buffer = new ByteArrayOutputStream()
    Console.withOut(new PrintStream(buffer)) {
      GenerateTopAndHarness.main(
        Array(
          "--target-dir", "test_run_dir/generate_top_spec",
          "-i", "/Users/chick/Adept/dev/masters/barstools/tapeout/src/test/resources/BlackBoxFloatTester.fir",
//          "-X", "low",
//          "-ll", "info",
//          "--help"
        )
      )
    }
    val output = buffer.toString
    println(output)
  }
}
