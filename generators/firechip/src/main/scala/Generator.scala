//See LICENSE for license details.

package firesim.firesim

import java.io.{File}

import chisel3.experimental.RawModule
import chisel3.internal.firrtl.{Circuit, Port}

import freechips.rocketchip.diplomacy.{ValName, AutoBundle}
import freechips.rocketchip.devices.debug.DebugIO
import freechips.rocketchip.util.{HasGeneratorUtilities, ParsedInputNames, ElaborationArtefacts}
import freechips.rocketchip.system.DefaultTestSuites._
import freechips.rocketchip.system.{TestGeneration, RegressionTestSuite}
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.subsystem.RocketTilesKey
import freechips.rocketchip.tile.XLen

import firesim.util.{GeneratorArgs, HasTargetAgnosticUtilites, HasFireSimGeneratorUtilities}

import utilities.TestSuiteHelper

trait HasTestSuites {
  val rv64RegrTestNames = collection.mutable.LinkedHashSet(
      "rv64ud-v-fcvt",
      "rv64ud-p-fdiv",
      "rv64ud-v-fadd",
      "rv64uf-v-fadd",
      "rv64um-v-mul",
      "rv64mi-p-breakpoint",
      "rv64uc-v-rvc",
      "rv64ud-v-structural",
      "rv64si-p-wfi",
      "rv64um-v-divw",
      "rv64ua-v-lrsc",
      "rv64ui-v-fence_i",
      "rv64ud-v-fcvt_w",
      "rv64uf-v-fmin",
      "rv64ui-v-sb",
      "rv64ua-v-amomax_d",
      "rv64ud-v-move",
      "rv64ud-v-fclass",
      "rv64ua-v-amoand_d",
      "rv64ua-v-amoxor_d",
      "rv64si-p-sbreak",
      "rv64ud-v-fmadd",
      "rv64uf-v-ldst",
      "rv64um-v-mulh",
      "rv64si-p-dirty")

  val rv32RegrTestNames = collection.mutable.LinkedHashSet(
      "rv32mi-p-ma_addr",
      "rv32mi-p-csr",
      "rv32ui-p-sh",
      "rv32ui-p-lh",
      "rv32uc-p-rvc",
      "rv32mi-p-sbreak",
      "rv32ui-p-sll")

  def addTestSuites(targetName: String, params: Parameters) {
    TestSuiteHelper.addRocketTestSuites(params)
    TestSuiteHelper.addBoomTestSuites(params)
    TestGeneration.addSuite(FastBlockdevTests)
    TestGeneration.addSuite(SlowBlockdevTests)
    if (!targetName.contains("NoNIC"))
      TestGeneration.addSuite(NICLoopbackTests)
  }
}

// Mixed into an App or into a TestSuite
trait IsFireSimGeneratorLike extends HasFireSimGeneratorUtilities with HasTestSuites {
  /** Output software test Makefrags, which provide targets for integration testing. */
  def generateTestSuiteMakefrags {
    addTestSuites(names.topModuleClass, targetParams)
    writeOutputFile(s"$longName.d", TestGeneration.generateMakefrag) // Subsystem-specific test suites
  }

  // Output miscellaneous files produced as a side-effect of elaboration
  def generateArtefacts {
    ElaborationArtefacts.files.foreach { case (extension, contents) =>
      writeOutputFile(s"${longName}.${extension}", contents ())
    }
  }
}

object FireSimGenerator extends App with IsFireSimGeneratorLike {
  lazy val generatorArgs = GeneratorArgs(args)
  lazy val genDir = new File(names.targetDir)
  elaborateAndCompileWithMidas
  generateTestSuiteMakefrags
  generateHostVerilogHeader
  generateArtefacts
  generateTclEnvFile
}

// For now, provide a separate generator app when not specifically building for FireSim
object Generator extends freechips.rocketchip.util.GeneratorApp with HasTestSuites {
  val longName = names.topModuleProject + "." + names.topModuleClass + "." + names.configs
  generateFirrtl
  generateAnno
  generateTestSuiteMakefrags
  generateArtefacts
}
