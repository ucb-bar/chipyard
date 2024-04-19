package barstools.macros

import mdf.macrolib.SRAMMacro

/** Tests to check that the cost function mechanism is working properly. */

/** A test metric that simply favours memories with smaller widths, to test that
  * the metric is chosen properly.
  */
object TestMinWidthMetric extends CostMetric with CostMetricCompanion {
  // Smaller width = lower cost = favoured
  override def cost(mem: Macro, lib: Macro): Option[Double] = Some(lib.src.width)

  override def commandLineParams() = Map()
  override def name() = "TestMinWidthMetric"
  override def construct(m: Map[String, String]): CostMetric = TestMinWidthMetric
}

/** Test that cost metric selection is working. */
class SelectCostMetric extends MacroCompilerSpec with HasSRAMGenerator {
  val mem = s"mem-SelectCostMetric.json"
  val lib = s"lib-SelectCostMetric.json"
  val v = s"SelectCostMetric.v"

  // Cost metrics must be registered for them to work with the command line.
  CostMetric.registerCostMetric(TestMinWidthMetric)

  override val costMetric: Option[CostMetric] = Some(TestMinWidthMetric)

  val libSRAMs = Seq(
    SRAMMacro(
      name = "SRAM_WIDTH_128",
      depth = BigInt(1024),
      width = 128,
      family = "1rw",
      ports = Seq(
        generateReadWritePort("", 128, BigInt(1024))
      )
    ),
    SRAMMacro(
      name = "SRAM_WIDTH_64",
      depth = BigInt(1024),
      width = 64,
      family = "1rw",
      ports = Seq(
        generateReadWritePort("", 64, BigInt(1024))
      )
    ),
    SRAMMacro(
      name = "SRAM_WIDTH_32",
      depth = BigInt(1024),
      width = 32,
      family = "1rw",
      ports = Seq(
        generateReadWritePort("", 32, BigInt(1024))
      )
    )
  )

  val memSRAMs = Seq(generateSRAM("target_memory", "", 128, BigInt(1024)))

  writeToLib(lib, libSRAMs)
  writeToMem(mem, memSRAMs)

  // Check that the min width SRAM was chosen, even though it is less efficient.
  val output =
    """
circuit target_memory :
  module target_memory :
    input addr : UInt<10>
    input clk : Clock
    input din : UInt<128>
    output dout : UInt<128>
    input write_en : UInt<1>

    inst mem_0_0 of SRAM_WIDTH_32
    inst mem_0_1 of SRAM_WIDTH_32
    inst mem_0_2 of SRAM_WIDTH_32
    inst mem_0_3 of SRAM_WIDTH_32
    mem_0_0.clk <= clk
    mem_0_0.addr <= addr
    node dout_0_0 = bits(mem_0_0.dout, 31, 0)
    mem_0_0.din <= bits(din, 31, 0)
    mem_0_0.write_en <= and(and(and(write_en, UInt<1>("h1")), UInt<1>("h1")), UInt<1>("h1"))
    mem_0_1.clk <= clk
    mem_0_1.addr <= addr
    node dout_0_1 = bits(mem_0_1.dout, 31, 0)
    mem_0_1.din <= bits(din, 63, 32)
    mem_0_1.write_en <= and(and(and(write_en, UInt<1>("h1")), UInt<1>("h1")), UInt<1>("h1"))
    mem_0_2.clk <= clk
    mem_0_2.addr <= addr
    node dout_0_2 = bits(mem_0_2.dout, 31, 0)
    mem_0_2.din <= bits(din, 95, 64)
    mem_0_2.write_en <= and(and(and(write_en, UInt<1>("h1")), UInt<1>("h1")), UInt<1>("h1"))
    mem_0_3.clk <= clk
    mem_0_3.addr <= addr
    node dout_0_3 = bits(mem_0_3.dout, 31, 0)
    mem_0_3.din <= bits(din, 127, 96)
    mem_0_3.write_en <= and(and(and(write_en, UInt<1>("h1")), UInt<1>("h1")), UInt<1>("h1"))
    node dout_0 = cat(dout_0_3, cat(dout_0_2, cat(dout_0_1, dout_0_0)))
    dout <= mux(UInt<1>("h1"), dout_0, UInt<128>("h0"))

  extmodule SRAM_WIDTH_32 :
    input addr : UInt<10>
    input clk : Clock
    input din : UInt<32>
    output dout : UInt<32>
    input write_en : UInt<1>

    defname = SRAM_WIDTH_32
"""

  compileExecuteAndTest(mem, lib, v, output)
}
