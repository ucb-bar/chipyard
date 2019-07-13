package beagle

import chisel3._

import freechips.rocketchip.config.{Parameters}
import freechips.rocketchip.util.{GeneratorApp}
import freechips.rocketchip.system.{TestGeneration}

import boom.system.{BoomTilesKey, TestSuiteHelper}

object Generator extends GeneratorApp {
  // add unique test suites
  override def addTestSuites {
    implicit val p: Parameters = params
    TestSuiteHelper.addRocketTestSuites
    TestSuiteHelper.addBoomTestSuites

    // add hwacha bmarks
    //import hwacha.HwachaTestSuites._
    //TestGeneration.addSuites(rv64uv.map(_("p")))
    //TestGeneration.addSuites(rv64uv.map(_("vp")))
    //TestGeneration.addSuite(rv64sv("p"))
    //TestGeneration.addSuite(hwachaBmarks)
  }

  override def generateTestSuiteMakefrags {
    addTestSuites
    var frag = TestGeneration.generateMakefrag + "\nSRC_EXTENSION = $(base_dir)/hwacha/$(src_path)/*.scala" + "\nDISASM_EXTENSION = --extension=hwacha"
    writeOutputFile(td, s"$longName.d", frag)
  }

  // specify the name that the generator outputs files as
  val longName = names.topModuleProject + "." + names.topModuleClass + "." + names.configs

  // generate files
  generateFirrtl
  generateAnno
  generateTestSuiteMakefrags
  generateArtefacts
}
