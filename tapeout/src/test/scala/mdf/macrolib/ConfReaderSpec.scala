package mdf.macrolib

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ConfReaderSpec extends AnyFlatSpec with Matchers {

  /** Generate a read port in accordance with RenameAnnotatedMemoryPorts. */
  def generateReadPort(num: Int, width: Int, depth: Int): MacroPort = {
    MacroPort(
      address = PolarizedPort(s"R${num}_addr", ActiveHigh),
      clock = Some(PolarizedPort(s"R${num}_clk", PositiveEdge)),
      output = Some(PolarizedPort(s"R${num}_data", ActiveHigh)),
      width = Some(width),
      depth = Some(depth)
    )
  }

  /** Generate a write port in accordance with RenameAnnotatedMemoryPorts. */
  def generateWritePort(num: Int, width: Int, depth: Int, maskGran: Option[Int] = None): MacroPort = {
    MacroPort(
      address = PolarizedPort(s"W${num}_addr", ActiveHigh),
      clock = Some(PolarizedPort(s"W${num}_clk", PositiveEdge)),
      input = Some(PolarizedPort(s"W${num}_data", ActiveHigh)),
      maskPort = if (maskGran.isDefined) Some(PolarizedPort(s"W${num}_mask", ActiveHigh)) else None,
      maskGran = maskGran,
      width = Some(184),
      depth = Some(128)
    )
  }

  "ConfReader" should "read a 1rw conf line" in {
    val confStr = "name Foo_Bar_mem123_ext depth 128 width 184 ports mrw mask_gran 23"
    ConfReader.readSingleLine(confStr) shouldBe SRAMMacro(
      name = "Foo_Bar_mem123_ext",
      width = 184,
      depth = 128,
      family = "1rw",
      ports = List(
        MacroPort(
          address = PolarizedPort("RW0_addr", ActiveHigh),
          clock = Some(PolarizedPort("RW0_clk", PositiveEdge)),
          writeEnable = Some(PolarizedPort("RW0_wmode", ActiveHigh)),
          output = Some(PolarizedPort("RW0_wdata", ActiveHigh)),
          input = Some(PolarizedPort("RW0_rdata", ActiveHigh)),
          maskPort = Some(PolarizedPort("RW0_wmask", ActiveHigh)),
          maskGran = Some(23),
          width = Some(184),
          depth = Some(128)
        )
      ),
      extraPorts = List()
    )
  }

  "ConfReader" should "read a 1r1w conf line" in {
    val confStr = "name Foo_Bar_mem321_ext depth 128 width 184 ports read,mwrite mask_gran 23"
    ConfReader.readSingleLine(confStr) shouldBe SRAMMacro(
      name = "Foo_Bar_mem321_ext",
      width = 184,
      depth = 128,
      family = "1r1w",
      ports = List(
        generateReadPort(0, 184, 128),
        generateWritePort(0, 184, 128, Some(23))
      ),
      extraPorts = List()
    )
  }

  "ConfReader" should "read a mixed 1r2w conf line" in {
    val confStr = "name Foo_Bar_mem321_ext depth 128 width 184 ports read,mwrite,write mask_gran 23"
    ConfReader.readSingleLine(confStr) shouldBe SRAMMacro(
      name = "Foo_Bar_mem321_ext",
      width = 184,
      depth = 128,
      family = "1r2w",
      ports = List(
        generateReadPort(0, 184, 128),
        generateWritePort(0, 184, 128, Some(23)),
        generateWritePort(1, 184, 128)
      ),
      extraPorts = List()
    )
  }

  "ConfReader" should "read a 42r29w conf line" in {
    val confStr =
      "name Foo_Bar_mem321_ext depth 128 width 184 ports " + (Seq.fill(42)("read") ++ Seq.fill(29)("mwrite"))
        .mkString(",") + " mask_gran 23"
    ConfReader.readSingleLine(confStr) shouldBe SRAMMacro(
      name = "Foo_Bar_mem321_ext",
      width = 184,
      depth = 128,
      family = "42r29w",
      ports = ((0 to 41).map((num: Int) => generateReadPort(num, 184, 128))) ++
        ((0 to 28).map((num: Int) => generateWritePort(num, 184, 128, Some(23)))),
      extraPorts = List()
    )
  }
}
