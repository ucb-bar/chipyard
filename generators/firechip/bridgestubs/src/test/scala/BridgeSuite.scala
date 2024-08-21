// See LICENSE for license details.

package firechip.bridgestubs

import java.io._

import org.scalatest.Suites
import org.scalatest.matchers.should._

import firesim.{BasePlatformConfig, TestSuiteCommon}

object BaseConfigs {
  case object F1 extends BasePlatformConfig("f1", Seq("DefaultF1Config"))
}

abstract class BridgeSuite(
  val targetName:                  String,
  override val targetConfigs:      String,
  override val basePlatformConfig: BasePlatformConfig,
) extends TestSuiteCommon("bridges")
    with Matchers {

  val chipyardDir = new File(System.getProperty("user.dir"))
  override val extraMakeArgs = Seq(s"TARGET_PROJECT_MAKEFRAG=$chipyardDir/generators/firechip/bridgestubs/src/main/makefrag/bridges")

  /** Helper to generate tests strings.
    */
  def getTestString(length: Int): String = {
    val gen   = new scala.util.Random(100)
    val alpha = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
    (1 to length).map(_ => alpha(gen.nextInt(alpha.length))).mkString
  }
}

class UARTTest(targetConfig: BasePlatformConfig) extends BridgeSuite("UARTModule", "NoConfig", targetConfig) {
  override def defineTests(backend: String, debug: Boolean) {
    it should "echo input to output" in {
      // Generate a short test string.
      val data = getTestString(16)

      // Create an input file.
      val input       = File.createTempFile("input", ".txt")
      input.deleteOnExit()
      val inputWriter = new BufferedWriter(new FileWriter(input))
      inputWriter.write(data)
      inputWriter.flush()
      inputWriter.close()

      // Create an output file to write to.
      val output = File.createTempFile("output", ".txt")

      val runResult = run(backend, debug, args = Seq(s"+uart-in0=${input.getPath}", s"+uart-out0=${output.getPath}"))
      assert(runResult == 0)
      val result    = scala.io.Source.fromFile(output.getPath).mkString
      result should equal(data)
    }
  }
}

class UARTF1Test extends UARTTest(BaseConfigs.F1)

class BlockDevTest(targetConfig: BasePlatformConfig)
    extends BridgeSuite("BlockDevModule", "NoConfig", targetConfig) {
  override def defineTests(backend: String, debug: Boolean) {
    it should "copy from one device to another" in {
      // Generate a random string spanning 2 sectors with a fixed seed.
      val data = getTestString(1024)

      // Create an input file.
      val input       = File.createTempFile("input", ".txt")
      input.deleteOnExit()
      val inputWriter = new BufferedWriter(new FileWriter(input))
      inputWriter.write(data)
      inputWriter.flush()
      inputWriter.close()

      // Pre-allocate space in the output.
      val output       = File.createTempFile("output", ".txt")
      output.deleteOnExit()
      val outputWriter = new BufferedWriter(new FileWriter(output))
      for (i <- 1 to data.size) {
        outputWriter.write('x')
      }
      outputWriter.flush()
      outputWriter.close()

      val runResult = run(backend, debug, args = Seq(s"+blkdev0=${input.getPath}", s"+blkdev1=${output.getPath}"))
      assert(runResult == 0)
      val result    = scala.io.Source.fromFile(output.getPath).mkString
      result should equal(data)
    }
  }
}

class BlockDevF1Test extends BlockDevTest(BaseConfigs.F1)

class BridgeTests
    extends Suites(
      new UARTF1Test,
      new BlockDevF1Test,
      new TracerVF1TestCount1,
      new TracerVF1TestCount6,
      new TracerVF1TestCount7,
    )
