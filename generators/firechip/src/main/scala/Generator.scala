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
