// SPDX-License-Identifier: Apache-2.0

package barstools.tapeout.transforms

import firrtl.FileUtils
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

import java.io.{ByteArrayOutputStream, File, PrintStream, PrintWriter}

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
    }
  }

  "generate harness should " ignore {
    val targetDir = "test_run_dir/generate_top_spec"
    FileUtils.makeDirectory(targetDir)

    val input = FileUtils.getLinesResource("/BlackBoxFloatTester.fir")
    val printWriter = new PrintWriter(new File(s"$targetDir/BlackBoxFloatTester.fir"))
    printWriter.write(input.mkString("\n"))
    printWriter.close()

    println(s"""Resource: ${input.mkString("\n")}""")


//    val buffer = new ByteArrayOutputStream()
//    Console.withOut(new PrintStream(buffer)) {
      GenerateTopAndHarness.main(
        Array(
          "--target-dir", "test_run_dir/generate_top_spec",
          "-i", s"$targetDir/BlackBoxFloatTester.fir",
          "-o",
          "chipyard.unittest.TestHarness.IceNetUnitTestConfig.top.v",
          "-tho", "chipyard.unittest.TestHarness.IceNetUnitTestConfig.harness.v",
          "-i", "chipyard.unittest.TestHarness.IceNetUnitTestConfig.fir",
          "--syn-top", "UnitTestSuite",
          "--harness-top", "TestHarness",
          "-faf", "chipyard.unittest.TestHarness.IceNetUnitTestConfig.anno.json",
          "-tsaof", "chipyard.unittest.TestHarness.IceNetUnitTestConfig.top.anno.json",
          "-tdf", "firrtl_black_box_resource_files.top.f",
          "-tsf", "chipyard.unittest.TestHarness.IceNetUnitTestConfig.top.fir",
          "-thaof", "chipyard.unittest.TestHarness.IceNetUnitTestConfig.harness.anno.json",
          "-hdf", "firrtl_black_box_resource_files.harness.f",
          "-thf", "chipyard.unittest.TestHarness.IceNetUnitTestConfig.harness.fir",
          "--infer-rw",
          "--repl-seq-mem", "-c:TestHarness:-o:chipyard.unittest.TestHarness.IceNetUnitTestConfig.top.mems.conf",
          "-thconf", "chipyard.unittest.TestHarness.IceNetUnitTestConfig.harness.mems.conf",
          "-td", "test_run_dir/from-ci",
          "-ll", "info"
        )
      )
    }
//    val output = buffer.toString
//    println(output)
}
