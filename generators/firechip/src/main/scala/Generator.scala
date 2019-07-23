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

import boom.system.{BoomTilesKey, BoomTestSuites}

import firesim.util.{GeneratorArgs, HasTargetAgnosticUtilites, HasFireSimGeneratorUtilities}

trait HasTestSuites {
  val rv64RegrTestNames = collection.mutable.LinkedHashSet(
      "rv64ud-v-fcvt",
      "rv64ud-p-fdiv",
      "rv64ud-v-fadd",
      "rv64uf-v-fadd",
      "rv64um-v-mul",
      // "rv64mi-p-breakpoint", // Not implemented in BOOM
      // "rv64uc-v-rvc", // Not implemented in BOOM
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
    val coreParams =
      if (params(RocketTilesKey).nonEmpty) {
        params(RocketTilesKey).head.core
      } else {
        params(BoomTilesKey).head.core
      }
    val xlen = params(XLen)
    val vm = coreParams.useVM
    val env = if (vm) List("p","v") else List("p")
    coreParams.fpu foreach { case cfg =>
      if (xlen == 32) {
        TestGeneration.addSuites(env.map(rv32uf))
        if (cfg.fLen >= 64)
          TestGeneration.addSuites(env.map(rv32ud))
      } else {
        TestGeneration.addSuite(rv32udBenchmarks)
        TestGeneration.addSuites(env.map(rv64uf))
        if (cfg.fLen >= 64)
          TestGeneration.addSuites(env.map(rv64ud))
      }
    }
    if (coreParams.useAtomics)    TestGeneration.addSuites(env.map(if (xlen == 64) rv64ua else rv32ua))
    if (coreParams.useCompressed) TestGeneration.addSuites(env.map(if (xlen == 64) rv64uc else rv32uc))
    val (rvi, rvu) =
      if (params(BoomTilesKey).nonEmpty) ((if (vm) BoomTestSuites.rv64i else BoomTestSuites.rv64pi), rv64u)
      else if (xlen == 64) ((if (vm) rv64i else rv64pi), rv64u)
      else            ((if (vm) rv32i else rv32pi), rv32u)

    TestGeneration.addSuites(rvi.map(_("p")))
    TestGeneration.addSuites((if (vm) List("v") else List()).flatMap(env => rvu.map(_(env))))
    TestGeneration.addSuite(benchmarks)
    TestGeneration.addSuite(new RegressionTestSuite(if (xlen == 64) rv64RegrTestNames else rv32RegrTestNames))
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

// A runtime-configuration generation for memory models
// Args
//   0: filename (basename)
//   1: Output directory (same as above)
//   Remaining argments are the same as above
object FireSimRuntimeConfGenerator extends App with HasFireSimGeneratorUtilities {
  lazy val generatorArgs = GeneratorArgs(args)
  lazy val genDir = new File(names.targetDir)
  // We need the scala instance of an elaborated memory-model, so that settings
  // may be legalized against the generated hardware. TODO: Currently these
  // settings aren't dependent on the target-AXI4 widths (~bug); this will need
  // to be an optional post-generation step in MIDAS
  lazy val memModel = (hostParams(midas.models.MemModelKey))(hostParams alterPartial {
      case junctions.NastiKey => junctions.NastiParameters(64, 32, 4)})// Related note ^
  chisel3.Driver.elaborate(() => memModel)
  val confFileName = args(0)
  memModel.getSettings(confFileName)(hostParams)
}
