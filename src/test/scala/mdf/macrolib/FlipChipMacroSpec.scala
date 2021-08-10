package mdf.macrolib

import firrtl.FileUtils
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class FlipChipMacroSpec extends AnyFlatSpec with Matchers {
  "Parsing flipchipmacros" should "work" in {
    val stream = FileUtils.getLinesResource("/bumps.json")
    val mdf = Utils.readMDFFromString(stream.mkString("\n"))
    mdf match {
      case Some(Seq(fcp: FlipChipMacro)) => println(fcp.visualize)
    }
  }
}
