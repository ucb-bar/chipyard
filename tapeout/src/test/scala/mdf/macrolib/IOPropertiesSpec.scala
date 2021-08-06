package mdf.macrolib

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class IOPropertiesSpec extends AnyFlatSpec with Matchers {
  "Parsing io_properties" should "work" in {
    val stream = getClass.getResourceAsStream("/io_properties.json")
    val mdf = Utils.readMDFFromString(scala.io.Source.fromInputStream(stream).getLines().mkString("\n"))
    mdf match {
      case Some(Seq(fcp: IOProperties)) =>
    }
  }
}
