package mdf.macrolib

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import play.api.libs.json._

object JSONUtils {
  def readStringValueMap(str: String): Option[Map[String, JsValue]] = {
    Json.parse(str) match {
      case x: JsObject => Some(x.as[Map[String, JsValue]])
      case _ => None
    }
  }
}

// Tests for filler macros
class FillerMacroSpec extends AnyFlatSpec with Matchers {
  "Valid lvt macros" should "be detected" in {
    val m = JSONUtils
      .readStringValueMap("""
                            | {
                            |   "type": "filler cell",
                            |   "name": "MY_FILLER_CELL",
                            |   "vt": "lvt"
                            | }
                            |""".stripMargin)
      .get
    FillerMacroBase.parseJSON(m) shouldBe Some(FillerMacro("MY_FILLER_CELL", "lvt"))
  }

  "Valid metal macro" should "be detected" in {
    val m = JSONUtils
      .readStringValueMap("""
                            | {
                            |   "type": "metal filler cell",
                            |   "name": "METAL_FILLER_CELL",
                            |   "vt": "lvt"
                            | }
                            |""".stripMargin)
      .get
    FillerMacroBase.parseJSON(m) shouldBe Some(MetalFillerMacro("METAL_FILLER_CELL", "lvt"))
  }

  "Valid hvt macros" should "be detected" in {
    val m = JSONUtils
      .readStringValueMap("""
                            | {
                            |   "type": "filler cell",
                            |   "name": "HVT_CELL_PROP",
                            |   "vt": "hvt"
                            | }
                            |""".stripMargin)
      .get
    FillerMacroBase.parseJSON(m) shouldBe Some(FillerMacro("HVT_CELL_PROP", "hvt"))
  }

  "Empty name macros" should "be rejected" in {
    val m = JSONUtils
      .readStringValueMap("""
                            | {
                            |   "type": "filler cell",
                            |   "name": "",
                            |   "vt": "hvt"
                            | }
                            |""".stripMargin)
      .get
    FillerMacroBase.parseJSON(m) shouldBe None
  }

  "Empty vt macros" should "be rejected" in {
    val m = JSONUtils
      .readStringValueMap("""
                            | {
                            |   "type": "metal filler cell",
                            |   "name": "DEAD_CELL",
                            |   "vt": ""
                            | }
                            |""".stripMargin)
      .get
    FillerMacroBase.parseJSON(m) shouldBe None
  }

  "Missing vt macros" should "be rejected" in {
    val m = JSONUtils
      .readStringValueMap("""
                            | {
                            |   "type": "metal filler cell",
                            |   "name": "DEAD_CELL"
                            | }
                            |""".stripMargin)
      .get
    FillerMacroBase.parseJSON(m) shouldBe None
  }

  "Missing name macros" should "be rejected" in {
    val m = JSONUtils
      .readStringValueMap("""
                            | {
                            |   "type": "filler cell",
                            |   "vt": ""
                            | }
                            |""".stripMargin)
      .get
    FillerMacroBase.parseJSON(m) shouldBe None
  }
}

// Tests for SRAM type and associates.
class SRAMMacroSpec extends AnyFlatSpec with Matchers {
  // Simple port which can be reused in tests
  // Note: assume width=depth=simplePortConstant.
  val simplePortConstant = 1024
  def simplePort(
    postfix: String = "",
    width:   Int = simplePortConstant,
    depth:   Int = simplePortConstant
  ): (String, MacroPort) = {
    val json = s"""
      {
      "address port name": "A_${postfix}",
      "address port polarity": "active high",
      "clock port name": "CLK_${postfix}",
      "clock port polarity": "positive edge",
      "write enable port name": "WEN_${postfix}",
      "write enable port polarity": "active high",
      "read enable port name": "REN_${postfix}",
      "read enable port polarity": "active high",
      "chip enable port name": "CEN_${postfix}",
      "chip enable port polarity": "active high",
      "output port name": "OUT_${postfix}",
      "output port polarity": "active high",
      "input port name": "IN_${postfix}",
      "input port polarity": "active high",
      "mask granularity": 1,
      "mask port name": "MASK_${postfix}",
      "mask port polarity": "active high"
    }
    """
    val port = MacroPort(
      address = PolarizedPort(s"A_${postfix}", ActiveHigh),
      clock = Some(PolarizedPort(s"CLK_${postfix}", PositiveEdge)),
      writeEnable = Some(PolarizedPort(s"WEN_${postfix}", ActiveHigh)),
      readEnable = Some(PolarizedPort(s"REN_${postfix}", ActiveHigh)),
      chipEnable = Some(PolarizedPort(s"CEN_${postfix}", ActiveHigh)),
      output = Some(PolarizedPort(s"OUT_${postfix}", ActiveHigh)),
      input = Some(PolarizedPort(s"IN_${postfix}", ActiveHigh)),
      maskPort = Some(PolarizedPort(s"MASK_${postfix}", ActiveHigh)),
      maskGran = Some(1),
      width = Some(width),
      depth = Some(depth)
    )
    (json, port)
  }
  "Simple port" should "be valid" in {
    {
      val (json, port) = simplePort("Simple1")
      MacroPort.parseJSON(JSONUtils.readStringValueMap(json).get, simplePortConstant, simplePortConstant) shouldBe Some(
        port
      )
    }
    {
      val (json, port) = simplePort("Simple2")
      MacroPort.parseJSON(JSONUtils.readStringValueMap(json).get, simplePortConstant, simplePortConstant) shouldBe Some(
        port
      )
    }
    {
      val (json, port) = simplePort("bar")
      MacroPort.parseJSON(JSONUtils.readStringValueMap(json).get, simplePortConstant, simplePortConstant) shouldBe Some(
        port
      )
    }
    {
      val (json, port) = simplePort("")
      MacroPort.parseJSON(JSONUtils.readStringValueMap(json).get, simplePortConstant, simplePortConstant) shouldBe Some(
        port
      )
    }
  }

  "Simple SRAM macro" should "be detected" in {
    val (json, port) = simplePort("", 2048, 4096)
    val m = JSONUtils
      .readStringValueMap(s"""
{
  "type": "sram",
  "name": "SRAMS_R_US",
  "width": 2048,
  "depth": "4096",
  "family": "1rw",
  "ports": [
    ${json}
  ]
}
    """)
      .get
    SRAMMacro.parseJSON(m) shouldBe Some(
      SRAMMacro("SRAMS_R_US", width = 2048, depth = 4096, family = "1rw", ports = List(port), extraPorts = List())
    )
  }

  "Non-power-of-two width & depth SRAM macro" should "be detected" in {
    val (json, port) = simplePort("", 1234, 8888)
    val m = JSONUtils
      .readStringValueMap(s"""
{
  "type": "sram",
  "name": "SRAMS_R_US",
  "width": 1234,
  "depth": "8888",
  "family": "1rw",
  "ports": [
    ${json}
  ]
}
    """)
      .get
    SRAMMacro.parseJSON(m) shouldBe Some(
      SRAMMacro("SRAMS_R_US", width = 1234, depth = 8888, family = "1rw", ports = List(port), extraPorts = List())
    )
  }

  "Minimal memory port" should "be detected" in {
    val (json, port) = simplePort("_A", 64, 1024)
    val port2 = MacroPort(
      address = PolarizedPort("A_B", ActiveHigh),
      clock = Some(PolarizedPort("CLK_B", PositiveEdge)),
      writeEnable = Some(PolarizedPort("WEN_B", ActiveHigh)),
      readEnable = None,
      chipEnable = None,
      output = Some(PolarizedPort("OUT_B", ActiveHigh)),
      input = Some(PolarizedPort("IN_B", ActiveHigh)),
      maskPort = None,
      maskGran = None,
      width = Some(64),
      depth = Some(1024)
    )
    val m = JSONUtils
      .readStringValueMap(s"""
{
  "type": "sram",
  "name": "SRAMS_R_US",
  "width": 64,
  "depth": "1024",
  "family": "2rw",
  "ports": [
    ${json},
    {
      "address port name": "A_B",
      "address port polarity": "active high",
      "clock port name": "CLK_B",
      "clock port polarity": "positive edge",
      "write enable port name": "WEN_B",
      "write enable port polarity": "active high",
      "output port name": "OUT_B",
      "output port polarity": "active high",
      "input port name": "IN_B",
      "input port polarity": "active high"
    }
  ]
}
    """)
      .get
    SRAMMacro.parseJSON(m) shouldBe Some(
      SRAMMacro("SRAMS_R_US", width = 64, depth = 1024, family = "2rw", ports = List(port, port2), extraPorts = List())
    )
  }

  "Extra ports" should "be detected" in {
    val (json, port) = simplePort("", 2048, 4096)
    val m = JSONUtils
      .readStringValueMap(s"""
{
  "type": "sram",
  "name": "GOT_EXTRA",
  "width": 2048,
  "depth": "4096",
  "family": "1rw",
  "ports": [
    ${json}
  ],
  "extra ports": [
    {
      "name": "TIE_DIE",
      "width": 1,
      "type": "constant",
      "value": 1
    },
    {
      "name": "TIE_MOO",
      "width": 4,
      "type": "constant",
      "value": 0
    }
  ]
}
    """)
      .get
    SRAMMacro.parseJSON(m) shouldBe Some(
      SRAMMacro(
        "GOT_EXTRA",
        width = 2048,
        depth = 4096,
        family = "1rw",
        ports = List(port),
        extraPorts = List(
          MacroExtraPort(
            name = "TIE_DIE",
            width = 1,
            portType = Constant,
            value = 1
          ),
          MacroExtraPort(
            name = "TIE_MOO",
            width = 4,
            portType = Constant,
            value = 0
          )
        )
      )
    )
  }

  "Invalid port" should "be rejected" in {
    val (json, port) = simplePort("", 2048, 4096)
    val m = JSONUtils
      .readStringValueMap(s"""
{
  "type": "sram",
  "name": "SRAMS_R_US",
  "width": 2048,
  "depth": "4096",
  "family": "1rw",
  "ports": [
    {
      "address port name": "missing_polarity",
      "output port name": "missing_clock"
    }
  ]
}
    """)
      .get
    SRAMMacro.parseJSON(m) shouldBe None
  }

  "No ports" should "be rejected" in {
    val (json, port) = simplePort("", 2048, 4096)
    val m = JSONUtils
      .readStringValueMap(s"""
{
  "type": "sram",
  "name": "SRAMS_R_US",
  "width": 2048,
  "depth": "4096",
  "family": "1rw"
}
    """)
      .get
    SRAMMacro.parseJSON(m) shouldBe None
  }

  "No family and ports" should "be rejected" in {
    val (json, port) = simplePort("", 2048, 4096)
    val m = JSONUtils
      .readStringValueMap(s"""
{
  "type": "sram",
  "name": "SRAMS_R_US",
  "width": 2048,
  "depth": "4096"
}
    """)
      .get
    SRAMMacro.parseJSON(m) shouldBe None
  }

  "String width" should "be rejected" in {
    val (json, port) = simplePort("", 2048, 4096)
    val m = JSONUtils
      .readStringValueMap(s"""
{
  "type": "sram",
  "name": "BAD_BAD_SRAM",
  "width": "wide",
  "depth": "4096"
}
    """)
      .get
    SRAMMacro.parseJSON(m) shouldBe None
  }

  "String depth" should "be rejected" in {
    val (json, port) = simplePort("", 2048, 4096)
    val m = JSONUtils
      .readStringValueMap(s"""
{
  "type": "sram",
  "name": "BAD_BAD_SRAM",
  "width": 512,
  "depth": "octopus_under_the_sea"
}
    """)
      .get
    SRAMMacro.parseJSON(m) shouldBe None
  }
}
