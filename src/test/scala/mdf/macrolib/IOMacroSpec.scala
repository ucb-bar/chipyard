package mdf.macrolib

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class IOMacroSpec extends AnyFlatSpec with Matchers {
  "Ground IOs" should "be detected" in {
    val json =
      """{
        |  "name" : "GND",
        |  "type" : "ground"
        |}""".stripMargin
    val m = JSONUtils.readStringValueMap(json).get
    IOMacro.parseJSON(m) shouldBe Some(IOMacro("GND", Ground))
  }
  "Power IOs" should "be detected" in {
    val json =
      """{
        |  "name" : "VDD0V8",
        |  "type" : "power"
        |}""".stripMargin
    val m = JSONUtils.readStringValueMap(json).get
    IOMacro.parseJSON(m) shouldBe Some(IOMacro("VDD0V8", Power))
  }
  "Digital IOs" should "be detected" in {
    val json =
      """{
        |    "name" : "VDDC0_SEL[1:0]",
        |    "type" : "digital",
        |    "direction" : "output",
        |    "termination" : "CMOS"
        |}""".stripMargin
    val m = JSONUtils.readStringValueMap(json).get
    IOMacro.parseJSON(m) shouldBe Some(IOMacro("VDDC0_SEL[1:0]", Digital, Some(Output), Some(CMOS)))
  }
  "Digital IOs with termination" should "be detected" in {
    val json =
      """{
        |    "name" : "CCLK1",
        |    "type" : "digital",
        |    "direction" : "input",
        |    "termination" : 50,
        |    "terminationType" : "single",
        |    "terminationReference" : "GND"
        |}""".stripMargin
    val m = JSONUtils.readStringValueMap(json).get
    IOMacro.parseJSON(m) shouldBe Some(
      IOMacro("CCLK1", Digital, Some(Input), Some(Resistive(50)), Some(Single), Some("GND"))
    )
  }
  "Digital IOs with matching and termination" should "be detected" in {
    val json =
      """{
        |    "name" : "REFCLK0P",
        |    "type" : "analog",
        |    "direction" : "input",
        |    "match" : ["REFCLK0N"],
        |    "termination" : 100,
        |    "terminationType" : "differential",
        |    "terminationReference" : "GND"
        |}""".stripMargin
    val m = JSONUtils.readStringValueMap(json).get
    IOMacro.parseJSON(m) shouldBe Some(
      IOMacro("REFCLK0P", Analog, Some(Input), Some(Resistive(100)), Some(Differential), Some("GND"), List("REFCLK0N"))
    )
  }
}
