package mdf.macrolib

import firrtl.FileUtils
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class IOPropertiesSpec extends AnyFlatSpec with Matchers {
  "Parsing io_properties" should "work" in {
    val stream = FileUtils.getLinesResource("/io_properties.json")
    val mdf = Utils.readMDFFromString(stream.mkString("\n"))
    mdf match {
      case Some(Seq(fcp: IOProperties)) =>
    }
  }
}
