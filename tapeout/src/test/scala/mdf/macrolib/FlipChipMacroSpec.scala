package mdf.macrolib

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class FlipChipMacroSpec extends AnyFlatSpec with Matchers {
  "Parsing flipchipmacros" should "work" in {
    val stream = getClass.getResourceAsStream("/bumps.json")
    val mdf = Utils.readMDFFromString(scala.io.Source.fromInputStream(stream).getLines().mkString("\n"))
    mdf match {
      case Some(Seq(fcp: FlipChipMacro)) => println(fcp.visualize)
    }
  }
}
