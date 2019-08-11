//See LICENSE for license details.
package firesim.firesim

import scala.collection.mutable.LinkedHashSet

import freechips.rocketchip.system.{TestGeneration, RocketTestSuite}

/* This imports tests from FireChip to test devices that aren't natively
 * tested by the riscv assembly tests.
 * Firesim's target-specific makefrag gives the recipes for building the
 * binaries.
 */

class BlockdevTestSuite(prefix: String, val names: LinkedHashSet[String]) extends RocketTestSuite {
  val envName = ""
  // fc_test_dir is is defined in firesim's Makefrag
  val dir = "$(fc_test_dir)"
  val makeTargetName = prefix + "-blkdev-tests"
  def kind = "blockdev"
  // Blockdev tests need an image, which complicates this
  def additionalArgs = "+blkdev-in-mem0=128 +nic-loopback0"
  override def toString = s"$makeTargetName = \\\n" +
    // Make variable with the binaries of the suite
    names.map(n => s"\t$n.riscv").mkString(" \\\n") + "\n\n" +
    // Variables with binary specific arguments
    names.map(n => s"$n.riscv_ARGS=$additionalArgs").mkString(" \n") +
    postScript
}

object FastBlockdevTests extends BlockdevTestSuite("fast", LinkedHashSet("blkdev"))
object SlowBlockdevTests extends BlockdevTestSuite("slow", LinkedHashSet("big-blkdev"))

class NICTestSuite(prefix: String, val names: LinkedHashSet[String]) extends RocketTestSuite {
  val envName = ""
  val dir = "$(fc_test_dir)"
  val makeTargetName = prefix + "-nic-tests"
  def kind = "nic"
  def additionalArgs = "+netbw0=100 +linklatency0=6405 +netburst0=8 +slotid=0 +nic-loopback0"
  override def toString = s"$makeTargetName = \\\n" +
    names.map(n => s"\t$n.riscv").mkString(" \\\n") + "\n\n" +
    names.map(n => s"$n.riscv_ARGS=$additionalArgs").mkString(" \n") +
    postScript
}

object NICLoopbackTests extends NICTestSuite("loopback", LinkedHashSet("nic-loopback"))
