package barstools.macros

import mdf.macrolib._

/** Tests to check that the cost function mechanism is working properly. */

/**
 * A test metric that simply favours memories with smaller widths, to test that
 * the metric is chosen properly.
 */
object TestMinWidthMetric extends CostMetric {
  // Smaller width = lower cost = favoured
  override def cost(mem: Macro, lib: Macro): Option[BigInt] = Some(lib.src.width)
}

/** Test that cost metric selection is working. */
class SelectCostMetric extends MacroCompilerSpec with HasSRAMGenerator {
  val mem = s"mem-SelectCostMetric.json"
  val lib = s"lib-SelectCostMetric.json"
  val v = s"SelectCostMetric.v"

  override val costMetric = TestMinWidthMetric

  val libSRAMs = Seq(
    SRAMMacro(
      macroType=SRAM,
      name="SRAM_WIDTH_128",
      depth=1024,
      width=128,
      family="1rw",
      ports=Seq(
        generateReadWritePort("", 128, 1024)
      )
    ),
    SRAMMacro(
      macroType=SRAM,
      name="SRAM_WIDTH_64",
      depth=1024,
      width=64,
      family="1rw",
      ports=Seq(
        generateReadWritePort("", 64, 1024)
      )
    ),
    SRAMMacro(
      macroType=SRAM,
      name="SRAM_WIDTH_32",
      depth=1024,
      width=32,
      family="1rw",
      ports=Seq(
        generateReadWritePort("", 32, 1024)
      )
    )
  )

  val memSRAMs = Seq(generateSRAM("target_memory", "", 128, 1024))

  writeToLib(lib, libSRAMs)
  writeToMem(mem, memSRAMs)

  // Check that the min width SRAM was chosen, even though it is less efficient.
  val output =
"""
circuit target_memory :
  module target_memory :
    input clk : Clock
    input addr : UInt<10>
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
    mem_0_0.write_en <= and(and(write_en, UInt<1>("h1")), UInt<1>("h1"))
    mem_0_1.clk <= clk
    mem_0_1.addr <= addr
    node dout_0_1 = bits(mem_0_1.dout, 31, 0)
    mem_0_1.din <= bits(din, 63, 32)
    mem_0_1.write_en <= and(and(write_en, UInt<1>("h1")), UInt<1>("h1"))
    mem_0_2.clk <= clk
    mem_0_2.addr <= addr
    node dout_0_2 = bits(mem_0_2.dout, 31, 0)
    mem_0_2.din <= bits(din, 95, 64)
    mem_0_2.write_en <= and(and(write_en, UInt<1>("h1")), UInt<1>("h1"))
    mem_0_3.clk <= clk
    mem_0_3.addr <= addr
    node dout_0_3 = bits(mem_0_3.dout, 31, 0)
    mem_0_3.din <= bits(din, 127, 96)
    mem_0_3.write_en <= and(and(write_en, UInt<1>("h1")), UInt<1>("h1"))
    node dout_0 = cat(dout_0_3, cat(dout_0_2, cat(dout_0_1, dout_0_0)))
    dout <= mux(UInt<1>("h1"), dout_0, UInt<1>("h0"))

  extmodule SRAM_WIDTH_32 :
    input clk : Clock
    input addr : UInt<10>
    input din : UInt<32>
    output dout : UInt<32>
    input write_en : UInt<1>

    defname = SRAM_WIDTH_32
"""

  compileExecuteAndTest(mem, lib, v, output)
}
