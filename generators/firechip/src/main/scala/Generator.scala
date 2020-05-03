//See LICENSE for license details.

package firesim.firesim

import java.io.{File, FileWriter}

import chisel3.RawModule
import chisel3.internal.firrtl.{Circuit, Port}

import freechips.rocketchip.diplomacy.{ValName, AutoBundle}
import freechips.rocketchip.devices.debug.DebugIO
import freechips.rocketchip.util.{ElaborationArtefacts}
import freechips.rocketchip.system.DefaultTestSuites._
import freechips.rocketchip.system.{TestGeneration, RegressionTestSuite}
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.subsystem.RocketTilesKey
import freechips.rocketchip.tile.XLen

import firesim.util.{GeneratorArgs, HasTargetAgnosticUtilites, HasFireSimGeneratorUtilities}

import scala.util.Try

import chipyard.TestSuiteHelper

trait HasTestSuites {
  def addTestSuites(targetName: String, params: Parameters) {
    val suiteHelper = new TestSuiteHelper
    suiteHelper.addRocketTestSuites(params)
    suiteHelper.addBoomTestSuites(params)
    suiteHelper.addArianeTestSuites(params)
    TestGeneration.addSuites(suiteHelper.suites.values.toSeq)
    TestGeneration.addSuite(FastBlockdevTests)
    TestGeneration.addSuite(SlowBlockdevTests)
    if (!targetName.contains("NoNIC"))
      TestGeneration.addSuite(NICLoopbackTests)

    import hwacha.HwachaTestSuites._
    if (Try(params(hwacha.HwachaNLanes)).getOrElse(0) > 0) {
      TestGeneration.addSuites(rv64uv.map(_("p")))
      TestGeneration.addSuites(rv64uv.map(_("vp")))
      TestGeneration.addSuite(rv64sv("p"))
      TestGeneration.addSuite(hwachaBmarks)
    }
  }
}

// Mixed into an App or into a TestSuite
trait IsFireSimGeneratorLike extends HasFireSimGeneratorUtilities with HasTestSuites {
  /** Output software test Makefrags, which provide targets for integration testing. */
  def generateTestSuiteMakefrags {
    addTestSuites(names.topModuleClass, targetParams)
    writeOutputFile(s"$longName.d", TestGeneration.generateMakeFrag) // Subsystem-specific test suites
  }

  // Output miscellaneous files produced as a side-effect of elaboration
  def generateArtefacts {
    // generate RC's artefacts
    ElaborationArtefacts.files.foreach { case (extension, contents) =>
      writeOutputFile(s"${longName}.${extension}", contents ())
    }
  }
}

object FireSimGenerator extends App with IsFireSimGeneratorLike {
  override lazy val longName = names.topModuleProject + "." + names.topModuleClass + "." + names.configs
  lazy val generatorArgs = GeneratorArgs(args)
  lazy val genDir = new File(names.targetDir)
  // The only reason this is not generateFirrtl; generateAnno is that we need to use a different
  // JsonProtocol to properly write out the annotations. Fix once the generated are unified
  elaborate
  generateTestSuiteMakefrags
  generateArtefacts
}
