package mdf.macrolib

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import play.api.libs.json._

import java.io.File

// Output tests (Scala -> JSON).
// TODO: unify these tests with the input tests?

trait HasAwesomeMemData {
  def getAwesomeMem() = {
    SRAMMacro(
      name = "awesome_mem",
      width = 32,
      depth = 1024,
      family = "1rw",
      ports = Seq(
        MacroPort(
          address = PolarizedPort(name = "addr", polarity = ActiveHigh),
          clock = Some(PolarizedPort(name = "clk", polarity = PositiveEdge)),
          writeEnable = Some(PolarizedPort(name = "write_enable", polarity = ActiveHigh)),
          readEnable = Some(PolarizedPort(name = "read_enable", polarity = ActiveHigh)),
          chipEnable = Some(PolarizedPort(name = "chip_enable", polarity = ActiveHigh)),
          output = Some(PolarizedPort(name = "data_out", polarity = ActiveHigh)),
          input = Some(PolarizedPort(name = "data_in", polarity = ActiveHigh)),
          maskPort = Some(PolarizedPort(name = "mask", polarity = ActiveHigh)),
          maskGran = Some(8),
          width = Some(32),
          depth = Some(1024) // These numbers don't matter.
        )
      ),
      extraPorts = List()
    )
  }

  def getAwesomeMemJSON(): String = {
    """
      | {
      |   "type": "sram",
      |   "name": "awesome_mem",
      |   "width": 32,
      |   "depth": "1024",
      |   "mux": 1,
      |   "mask":true,
      |   "family": "1rw",
      |   "ports": [
      | {
      |   "address port name": "addr",
      |   "address port polarity": "active high",
      |   "clock port name": "clk",
      |   "clock port polarity": "positive edge",
      |   "write enable port name": "write_enable",
      |   "write enable port polarity": "active high",
      |   "read enable port name": "read_enable",
      |   "read enable port polarity": "active high",
      |   "chip enable port name": "chip_enable",
      |   "chip enable port polarity": "active high",
      |   "output port name": "data_out",
      |   "output port polarity": "active high",
      |   "input port name": "data_in",
      |   "input port polarity": "active high",
      |   "mask port name": "mask",
      |   "mask port polarity": "active high",
      |   "mask granularity": 8
      | }
      |   ]
      | }
      |""".stripMargin
  }
}

// Tests for filler macros.
class FillerMacroOutput extends AnyFlatSpec with Matchers {
  "Valid lvt macro" should "be generated" in {
    val expected = """
                     | {
                     |   "type": "filler cell",
                     |   "name": "MY_FILLER_CELL",
                     |   "vt": "lvt"
                     | }
                     |""".stripMargin
    FillerMacro("MY_FILLER_CELL", "lvt").toJSON shouldBe Json.parse(expected)
  }

  "Valid metal macro" should "be generated" in {
    val expected = """
                     | {
                     |   "type": "metal filler cell",
                     |   "name": "METAL_FILLER_CELL",
                     |   "vt": "lvt"
                     | }
                     |""".stripMargin
    MetalFillerMacro("METAL_FILLER_CELL", "lvt").toJSON shouldBe Json.parse(expected)
  }

  "Valid hvt macro" should "be generated" in {
    val expected = """
                     | {
                     |   "type": "filler cell",
                     |   "name": "HVT_CELL_PROP",
                     |   "vt": "hvt"
                     | }
                     |""".stripMargin
    FillerMacro("HVT_CELL_PROP", "hvt").toJSON shouldBe Json.parse(expected)
  }
}

class SRAMPortOutput extends AnyFlatSpec with Matchers {
  "Extra port" should "be generated" in {
    val m = MacroExtraPort(
      name = "TIE_HIGH",
      width = 8,
      portType = Constant,
      value = ((1 << 8) - 1)
    )
    val expected = """
                     | {
                     |   "type": "constant",
                     |   "name": "TIE_HIGH",
                     |   "width": 8,
                     |   "value": 255
                     | }
                     |""".stripMargin
    m.toJSON shouldBe Json.parse(expected)
  }

  "Minimal write port" should "be generated" in {
    val m = MacroPort(
      address = PolarizedPort(name = "addr", polarity = ActiveHigh),
      clock = Some(PolarizedPort(name = "clk", polarity = PositiveEdge)),
      writeEnable = Some(PolarizedPort(name = "write_enable", polarity = ActiveHigh)),
      input = Some(PolarizedPort(name = "data_in", polarity = ActiveHigh)),
      width = Some(32),
      depth = Some(1024) // These numbers don't matter.
    )
    val expected = """
                     | {
                     |   "address port name": "addr",
                     |   "address port polarity": "active high",
                     |   "clock port name": "clk",
                     |   "clock port polarity": "positive edge",
                     |   "write enable port name": "write_enable",
                     |   "write enable port polarity": "active high",
                     |   "input port name": "data_in",
                     |   "input port polarity": "active high"
                     | }
                     |""".stripMargin
    m.toJSON shouldBe Json.parse(expected)
  }

  "Minimal read port" should "be generated" in {
    val m = MacroPort(
      address = PolarizedPort(name = "addr", polarity = ActiveHigh),
      clock = Some(PolarizedPort(name = "clk", polarity = PositiveEdge)),
      output = Some(PolarizedPort(name = "data_out", polarity = ActiveHigh)),
      width = Some(32),
      depth = Some(1024) // These numbers don't matter.
    )
    val expected = """
                     | {
                     |   "address port name": "addr",
                     |   "address port polarity": "active high",
                     |   "clock port name": "clk",
                     |   "clock port polarity": "positive edge",
                     |   "output port name": "data_out",
                     |   "output port polarity": "active high"
                     | }
                     |""".stripMargin
    m.toJSON shouldBe Json.parse(expected)
  }

  "Masked read port" should "be generated" in {
    val m = MacroPort(
      address = PolarizedPort(name = "addr", polarity = ActiveHigh),
      clock = Some(PolarizedPort(name = "clk", polarity = PositiveEdge)),
      output = Some(PolarizedPort(name = "data_out", polarity = ActiveHigh)),
      maskPort = Some(PolarizedPort(name = "mask", polarity = ActiveHigh)),
      maskGran = Some(8),
      width = Some(32),
      depth = Some(1024) // These numbers don't matter.
    )
    val expected = """
                     | {
                     |   "address port name": "addr",
                     |   "address port polarity": "active high",
                     |   "clock port name": "clk",
                     |   "clock port polarity": "positive edge",
                     |   "output port name": "data_out",
                     |   "output port polarity": "active high",
                     |   "mask port name": "mask",
                     |   "mask port polarity": "active high",
                     |   "mask granularity": 8
                     | }
                     |""".stripMargin
    m.toJSON shouldBe Json.parse(expected)
  }

  "Everything port" should "be generated" in {
    val m = MacroPort(
      address = PolarizedPort(name = "addr", polarity = ActiveHigh),
      clock = Some(PolarizedPort(name = "clk", polarity = PositiveEdge)),
      writeEnable = Some(PolarizedPort(name = "write_enable", polarity = ActiveHigh)),
      readEnable = Some(PolarizedPort(name = "read_enable", polarity = ActiveHigh)),
      chipEnable = Some(PolarizedPort(name = "chip_enable", polarity = ActiveHigh)),
      output = Some(PolarizedPort(name = "data_out", polarity = ActiveHigh)),
      input = Some(PolarizedPort(name = "data_in", polarity = ActiveHigh)),
      maskPort = Some(PolarizedPort(name = "mask", polarity = ActiveHigh)),
      maskGran = Some(8),
      width = Some(32),
      depth = Some(1024) // These numbers don't matter.
    )
    val expected = """
                     | {
                     |   "address port name": "addr",
                     |   "address port polarity": "active high",
                     |   "clock port name": "clk",
                     |   "clock port polarity": "positive edge",
                     |   "write enable port name": "write_enable",
                     |   "write enable port polarity": "active high",
                     |   "read enable port name": "read_enable",
                     |   "read enable port polarity": "active high",
                     |   "chip enable port name": "chip_enable",
                     |   "chip enable port polarity": "active high",
                     |   "output port name": "data_out",
                     |   "output port polarity": "active high",
                     |   "input port name": "data_in",
                     |   "input port polarity": "active high",
                     |   "mask port name": "mask",
                     |   "mask port polarity": "active high",
                     |   "mask granularity": 8
                     | }
                     |""".stripMargin
    m.toJSON shouldBe Json.parse(expected)
  }
}

class SRAMMacroOutput extends AnyFlatSpec with Matchers with HasAwesomeMemData {
  "SRAM macro" should "be generated" in {
    val m = getAwesomeMem
    val expected = getAwesomeMemJSON
    m.toJSON shouldBe Json.parse(expected)
  }
}

class InputOutput extends AnyFlatSpec with Matchers with HasAwesomeMemData {
  "Read-write string" should "preserve data" in {
    val mdf = List(
      FillerMacro("MY_FILLER_CELL", "lvt"),
      MetalFillerMacro("METAL_GEAR_FILLER", "hvt"),
      getAwesomeMem
    )
    Utils.readMDFFromString(Utils.writeMDFToString(mdf)) shouldBe Some(mdf)
  }

  val testDir: String = "test_run_dir"
  new File(testDir).mkdirs // Make sure the testDir exists

  "Read-write file" should "preserve data" in {
    val mdf = List(
      FillerMacro("MY_FILLER_CELL", "lvt"),
      MetalFillerMacro("METAL_GEAR_FILLER", "hvt"),
      getAwesomeMem
    )
    val filename = testDir + "/" + "mdf_read_write_test.json"
    Utils.writeMDFToPath(Some(filename), mdf) shouldBe true
    Utils.readMDFFromPath(Some(filename)) shouldBe Some(mdf)
  }
}
