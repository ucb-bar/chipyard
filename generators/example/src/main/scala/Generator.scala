package example

import scala.util.control.Exception

import chisel3._

import freechips.rocketchip.config.{Parameters}
import freechips.rocketchip.util.{GeneratorApp}
import freechips.rocketchip.tile.{BuildRoCC}
import freechips.rocketchip.system.{TestGeneration}

import utilities.{TestSuiteHelper}

object Generator extends GeneratorApp {
  // add unique test suites
  override def addTestSuites {
    implicit val p: Parameters = params
    TestSuiteHelper.addRocketTestSuites
    TestSuiteHelper.addBoomTestSuites

    // if hwacha parameter exists then generate its tests
    // TODO: find a more elegant way to do this
    Exception.ignoring(classOf[java.lang.IllegalArgumentException]){
      if (p(hwacha.HwachaIcacheKey) != null) {
        // add hwacha bmarks + asm tests
        import hwacha.HwachaTestSuites._
        TestGeneration.addSuites(rv64uv.map(_("p")))
        TestGeneration.addSuites(rv64uv.map(_("vp")))
        TestGeneration.addSuite(rv64sv("p"))
        TestGeneration.addSuite(hwachaBmarks)
      }
    }
  }

  // specify the name that the generator outputs files as
  val longName = names.topModuleProject + "." + names.topModuleClass + "." + names.configs

  // generate files
  generateTestSuiteMakefrags
  generateFirrtl
  generateAnno
  //generateTestSuiteMakefrags
  generateArtefacts
}
