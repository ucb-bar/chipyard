// See LICENSE for license details.
package barstools.macros

import firrtl.FileUtils
import mdf.macrolib.{Constant, MacroExtraPort, SRAMMacro}

// Specific one-off tests to run, not created by a generator.

// Check that verilog actually gets generated.
// TODO: check the actual verilog's correctness?
class GenerateSomeVerilog extends MacroCompilerSpec with HasSRAMGenerator with HasSimpleDepthTestGenerator {
  override lazy val width = 32
  override lazy val memDepth = BigInt(2048)
  override lazy val libDepth = BigInt(1024)

  it should "execute fine" in {
    compileExecuteAndTest(mem, lib, v, output)
  }

  it should "generate non-empty verilog" in {
    val verilog = FileUtils.getText(vPrefix + "/" + v)
    verilog.isEmpty shouldBe false
  }
}

class WriteEnableTest extends MacroCompilerSpec with HasSRAMGenerator {
  val mem = s"mem-WriteEnableTest.json" // mem. you want to create
  val lib = s"lib-WriteEnableTest.json" // lib. of mems to create it
  val v = s"WriteEnableTest.json"

  override val libPrefix = "src/test/resources"

  val memSRAMs: Seq[mdf.macrolib.Macro] = mdf.macrolib.Utils
    .readMDFFromString("""
[ {
  "type" : "sram",
  "name" : "cc_banks_0_ext",
  "width" : 64,
  "depth" : "4096",
  "mux" : 1,
  "ports" : [ {
    "address port name" : "RW0_addr",
    "address port polarity" : "active high",
    "clock port name" : "RW0_clk",
    "clock port polarity" : "positive edge",
    "write enable port name" : "RW0_wmode",
    "write enable port polarity" : "active high",
    "chip enable port name" : "RW0_en",
    "chip enable port polarity" : "active high",
    "output port name" : "RW0_rdata",
    "output port polarity" : "active high",
    "input port name" : "RW0_wdata",
    "input port polarity" : "active high"
  } ],
  "family" : "1rw"
} ]
""").getOrElse(Seq())

  writeToMem(mem, memSRAMs)

  val output =
    """
circuit cc_banks_0_ext :
  module cc_banks_0_ext :
    input RW0_addr : UInt<12>
    input RW0_clk : Clock
    input RW0_wdata : UInt<64>
    output RW0_rdata : UInt<64>
    input RW0_en : UInt<1>
    input RW0_wmode : UInt<1>

    inst mem_0_0 of fake_mem
    mem_0_0.clk <= RW0_clk
    mem_0_0.addr <= RW0_addr
    node RW0_rdata_0_0 = bits(mem_0_0.dataout, 63, 0)
    mem_0_0.datain <= bits(RW0_wdata, 63, 0)
    mem_0_0.ren <= and(and(not(RW0_wmode), RW0_en), UInt<1>("h1"))
    mem_0_0.wen <= and(and(and(RW0_wmode, RW0_en), UInt<1>("h1")), UInt<1>("h1"))
    node RW0_rdata_0 = RW0_rdata_0_0
    RW0_rdata <= mux(UInt<1>("h1"), RW0_rdata_0, UInt<64>("h0"))

  extmodule fake_mem :
    input addr : UInt<12>
    input clk : Clock
    input datain : UInt<64>
    output dataout : UInt<64>
    input ren : UInt<1>
    input wen : UInt<1>

    defname = fake_mem
"""

  it should "compile, execute, and test" in {
    compileExecuteAndTest(mem, lib, v, output)
  }
}

class MaskPortTest extends MacroCompilerSpec with HasSRAMGenerator {
  val mem = s"mem-MaskPortTest.json" // mem. you want to create
  val lib = s"lib-MaskPortTest.json" // lib. of mems to create it
  val v = s"MaskPortTest.json"

  override val libPrefix = "src/test/resources"

  val memSRAMs: Seq[mdf.macrolib.Macro] = mdf.macrolib.Utils
    .readMDFFromString("""
[ {
  "type" : "sram",
  "name" : "cc_dir_ext",
  "width" : 128,
  "depth" : "512",
  "mux" : 1,
  "ports" : [ {
    "address port name" : "RW0_addr",
    "address port polarity" : "active high",
    "clock port name" : "RW0_clk",
    "clock port polarity" : "positive edge",
    "write enable port name" : "RW0_wmode",
    "write enable port polarity" : "active high",
    "chip enable port name" : "RW0_en",
    "chip enable port polarity" : "active high",
    "output port name" : "RW0_rdata",
    "output port polarity" : "active high",
    "input port name" : "RW0_wdata",
    "input port polarity" : "active high",
    "mask port name" : "RW0_wmask",
    "mask port polarity" : "active high",
    "mask granularity" : 16
  } ],
  "family" : "1rw"
} ]
""").getOrElse(List())

  writeToMem(mem, memSRAMs)

  val output =
    """
circuit cc_dir_ext :
  module cc_dir_ext :
    input RW0_addr : UInt<9>
    input RW0_clk : Clock
    input RW0_wdata : UInt<128>
    output RW0_rdata : UInt<128>
    input RW0_en : UInt<1>
    input RW0_wmode : UInt<1>
    input RW0_wmask : UInt<8>

    inst mem_0_0 of fake_mem
    inst mem_0_1 of fake_mem
    mem_0_0.clk <= RW0_clk
    mem_0_0.addr <= RW0_addr
    node RW0_rdata_0_0 = bits(mem_0_0.dataout, 63, 0)
    mem_0_0.datain <= bits(RW0_wdata, 63, 0)
    mem_0_0.ren <= and(and(not(RW0_wmode), RW0_en), UInt<1>("h1"))
    mem_0_0.mport <= not(cat(bits(RW0_wmask, 3, 3), cat(bits(RW0_wmask, 3, 3), cat(bits(RW0_wmask, 3, 3), cat(bits(RW0_wmask, 3, 3), cat(bits(RW0_wmask, 3, 3), cat(bits(RW0_wmask, 3, 3), cat(bits(RW0_wmask, 3, 3), cat(bits(RW0_wmask, 3, 3), cat(bits(RW0_wmask, 3, 3), cat(bits(RW0_wmask, 3, 3), cat(bits(RW0_wmask, 3, 3), cat(bits(RW0_wmask, 3, 3), cat(bits(RW0_wmask, 3, 3), cat(bits(RW0_wmask, 3, 3), cat(bits(RW0_wmask, 3, 3), cat(bits(RW0_wmask, 3, 3), cat(bits(RW0_wmask, 2, 2), cat(bits(RW0_wmask, 2, 2), cat(bits(RW0_wmask, 2, 2), cat(bits(RW0_wmask, 2, 2), cat(bits(RW0_wmask, 2, 2), cat(bits(RW0_wmask, 2, 2), cat(bits(RW0_wmask, 2, 2), cat(bits(RW0_wmask, 2, 2), cat(bits(RW0_wmask, 2, 2), cat(bits(RW0_wmask, 2, 2), cat(bits(RW0_wmask, 2, 2), cat(bits(RW0_wmask, 2, 2), cat(bits(RW0_wmask, 2, 2), cat(bits(RW0_wmask, 2, 2), cat(bits(RW0_wmask, 2, 2), cat(bits(RW0_wmask, 2, 2), cat(bits(RW0_wmask, 1, 1), cat(bits(RW0_wmask, 1, 1), cat(bits(RW0_wmask, 1, 1), cat(bits(RW0_wmask, 1, 1), cat(bits(RW0_wmask, 1, 1), cat(bits(RW0_wmask, 1, 1), cat(bits(RW0_wmask, 1, 1), cat(bits(RW0_wmask, 1, 1), cat(bits(RW0_wmask, 1, 1), cat(bits(RW0_wmask, 1, 1), cat(bits(RW0_wmask, 1, 1), cat(bits(RW0_wmask, 1, 1), cat(bits(RW0_wmask, 1, 1), cat(bits(RW0_wmask, 1, 1), cat(bits(RW0_wmask, 1, 1), cat(bits(RW0_wmask, 1, 1), cat(bits(RW0_wmask, 0, 0), cat(bits(RW0_wmask, 0, 0), cat(bits(RW0_wmask, 0, 0), cat(bits(RW0_wmask, 0, 0), cat(bits(RW0_wmask, 0, 0), cat(bits(RW0_wmask, 0, 0), cat(bits(RW0_wmask, 0, 0), cat(bits(RW0_wmask, 0, 0), cat(bits(RW0_wmask, 0, 0), cat(bits(RW0_wmask, 0, 0), cat(bits(RW0_wmask, 0, 0), cat(bits(RW0_wmask, 0, 0), cat(bits(RW0_wmask, 0, 0), cat(bits(RW0_wmask, 0, 0), cat(bits(RW0_wmask, 0, 0), bits(RW0_wmask, 0, 0)))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))
    mem_0_0.wen <= and(and(RW0_wmode, RW0_en), UInt<1>("h1"))
    mem_0_1.clk <= RW0_clk
    mem_0_1.addr <= RW0_addr
    node RW0_rdata_0_1 = bits(mem_0_1.dataout, 63, 0)
    mem_0_1.datain <= bits(RW0_wdata, 127, 64)
    mem_0_1.ren <= and(and(not(RW0_wmode), RW0_en), UInt<1>("h1"))
    mem_0_1.mport <= not(cat(bits(RW0_wmask, 7, 7), cat(bits(RW0_wmask, 7, 7), cat(bits(RW0_wmask, 7, 7), cat(bits(RW0_wmask, 7, 7), cat(bits(RW0_wmask, 7, 7), cat(bits(RW0_wmask, 7, 7), cat(bits(RW0_wmask, 7, 7), cat(bits(RW0_wmask, 7, 7), cat(bits(RW0_wmask, 7, 7), cat(bits(RW0_wmask, 7, 7), cat(bits(RW0_wmask, 7, 7), cat(bits(RW0_wmask, 7, 7), cat(bits(RW0_wmask, 7, 7), cat(bits(RW0_wmask, 7, 7), cat(bits(RW0_wmask, 7, 7), cat(bits(RW0_wmask, 7, 7), cat(bits(RW0_wmask, 6, 6), cat(bits(RW0_wmask, 6, 6), cat(bits(RW0_wmask, 6, 6), cat(bits(RW0_wmask, 6, 6), cat(bits(RW0_wmask, 6, 6), cat(bits(RW0_wmask, 6, 6), cat(bits(RW0_wmask, 6, 6), cat(bits(RW0_wmask, 6, 6), cat(bits(RW0_wmask, 6, 6), cat(bits(RW0_wmask, 6, 6), cat(bits(RW0_wmask, 6, 6), cat(bits(RW0_wmask, 6, 6), cat(bits(RW0_wmask, 6, 6), cat(bits(RW0_wmask, 6, 6), cat(bits(RW0_wmask, 6, 6), cat(bits(RW0_wmask, 6, 6), cat(bits(RW0_wmask, 5, 5), cat(bits(RW0_wmask, 5, 5), cat(bits(RW0_wmask, 5, 5), cat(bits(RW0_wmask, 5, 5), cat(bits(RW0_wmask, 5, 5), cat(bits(RW0_wmask, 5, 5), cat(bits(RW0_wmask, 5, 5), cat(bits(RW0_wmask, 5, 5), cat(bits(RW0_wmask, 5, 5), cat(bits(RW0_wmask, 5, 5), cat(bits(RW0_wmask, 5, 5), cat(bits(RW0_wmask, 5, 5), cat(bits(RW0_wmask, 5, 5), cat(bits(RW0_wmask, 5, 5), cat(bits(RW0_wmask, 5, 5), cat(bits(RW0_wmask, 5, 5), cat(bits(RW0_wmask, 4, 4), cat(bits(RW0_wmask, 4, 4), cat(bits(RW0_wmask, 4, 4), cat(bits(RW0_wmask, 4, 4), cat(bits(RW0_wmask, 4, 4), cat(bits(RW0_wmask, 4, 4), cat(bits(RW0_wmask, 4, 4), cat(bits(RW0_wmask, 4, 4), cat(bits(RW0_wmask, 4, 4), cat(bits(RW0_wmask, 4, 4), cat(bits(RW0_wmask, 4, 4), cat(bits(RW0_wmask, 4, 4), cat(bits(RW0_wmask, 4, 4), cat(bits(RW0_wmask, 4, 4), cat(bits(RW0_wmask, 4, 4), bits(RW0_wmask, 4, 4)))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))
    mem_0_1.wen <= and(and(RW0_wmode, RW0_en), UInt<1>("h1"))
    node RW0_rdata_0 = cat(RW0_rdata_0_1, RW0_rdata_0_0)
    RW0_rdata <= mux(UInt<1>("h1"), RW0_rdata_0, UInt<128>("h0"))

  extmodule fake_mem :
    input addr : UInt<9>
    input clk : Clock
    input datain : UInt<64>
    output dataout : UInt<64>
    input ren : UInt<1>
    input wen : UInt<1>
    input mport : UInt<64>

    defname = fake_mem
"""

  it should "compile, execute, and test" in {
    compileExecuteAndTest(mem, lib, v, output)
  }
}

class BOOMTest extends MacroCompilerSpec with HasSRAMGenerator {
  val mem = s"mem-BOOMTest.json"
  val lib = s"lib-BOOMTest.json"
  val v = s"BOOMTest.v"

  override val libPrefix = "src/test/resources"

  val memSRAMs: Seq[mdf.macrolib.Macro] = mdf.macrolib.Utils
    .readMDFFromString("""
[ {
  "type" : "sram",
  "name" : "_T_182_ext",
  "width" : 88,
  "depth" : "64",
  "ports" : [ {
    "address port name" : "R0_addr",
    "address port polarity" : "active high",
    "clock port name" : "R0_clk",
    "clock port polarity" : "active high",
    "chip enable port name" : "R0_en",
    "chip enable port polarity" : "active high",
    "output port name" : "R0_data",
    "output port polarity" : "active high"
  }, {
    "address port name" : "W0_addr",
    "address port polarity" : "active high",
    "clock port name" : "W0_clk",
    "clock port polarity" : "active high",
    "chip enable port name" : "W0_en",
    "chip enable port polarity" : "active high",
    "input port name" : "W0_data",
    "input port polarity" : "active high",
    "mask port name" : "W0_mask",
    "mask port polarity" : "active high",
    "mask granularity" : 22
  } ]
}, {
  "type" : "sram",
  "name" : "_T_84_ext",
  "width" : 64,
  "depth" : "512",
  "ports" : [ {
    "address port name" : "R0_addr",
    "address port polarity" : "active high",
    "clock port name" : "R0_clk",
    "clock port polarity" : "active high",
    "chip enable port name" : "R0_en",
    "chip enable port polarity" : "active high",
    "output port name" : "R0_data",
    "output port polarity" : "active high"
  }, {
    "address port name" : "W0_addr",
    "address port polarity" : "active high",
    "clock port name" : "W0_clk",
    "clock port polarity" : "active high",
    "chip enable port name" : "W0_en",
    "chip enable port polarity" : "active high",
    "input port name" : "W0_data",
    "input port polarity" : "active high",
    "mask port name" : "W0_mask",
    "mask port polarity" : "active high",
    "mask granularity" : 64
  } ]
}, {
  "type" : "sram",
  "name" : "tag_array_ext",
  "width" : 80,
  "depth" : "64",
  "ports" : [ {
    "address port name" : "RW0_addr",
    "address port polarity" : "active high",
    "clock port name" : "RW0_clk",
    "clock port polarity" : "active high",
    "write enable port name" : "RW0_wmode",
    "write enable port polarity" : "active high",
    "chip enable port name" : "RW0_en",
    "chip enable port polarity" : "active high",
    "output port name" : "RW0_rdata",
    "output port polarity" : "active high",
    "input port name" : "RW0_wdata",
    "input port polarity" : "active high",
    "mask port name" : "RW0_wmask",
    "mask port polarity" : "active high",
    "mask granularity" : 20
  } ]
}, {
  "type" : "sram",
  "name" : "_T_886_ext",
  "width" : 64,
  "depth" : "512",
  "ports" : [ {
    "address port name" : "RW0_addr",
    "address port polarity" : "active high",
    "clock port name" : "RW0_clk",
    "clock port polarity" : "active high",
    "write enable port name" : "RW0_wmode",
    "write enable port polarity" : "active high",
    "chip enable port name" : "RW0_en",
    "chip enable port polarity" : "active high",
    "output port name" : "RW0_rdata",
    "output port polarity" : "active high",
    "input port name" : "RW0_wdata",
    "input port polarity" : "active high"
  } ]
}, {
  "type" : "sram",
  "name" : "entries_info_ext",
  "width" : 40,
  "depth" : "24",
  "ports" : [ {
    "address port name" : "R0_addr",
    "address port polarity" : "active high",
    "clock port name" : "R0_clk",
    "clock port polarity" : "active high",
    "chip enable port name" : "R0_en",
    "chip enable port polarity" : "active high",
    "output port name" : "R0_data",
    "output port polarity" : "active high"
  }, {
    "address port name" : "W0_addr",
    "address port polarity" : "active high",
    "clock port name" : "W0_clk",
    "clock port polarity" : "active high",
    "chip enable port name" : "W0_en",
    "chip enable port polarity" : "active high",
    "input port name" : "W0_data",
    "input port polarity" : "active high"
  } ]
}, {
  "type" : "sram",
  "name" : "smem_ext",
  "width" : 32,
  "depth" : "32",
  "ports" : [ {
    "address port name" : "RW0_addr",
    "address port polarity" : "active high",
    "clock port name" : "RW0_clk",
    "clock port polarity" : "active high",
    "write enable port name" : "RW0_wmode",
    "write enable port polarity" : "active high",
    "chip enable port name" : "RW0_en",
    "chip enable port polarity" : "active high",
    "output port name" : "RW0_rdata",
    "output port polarity" : "active high",
    "input port name" : "RW0_wdata",
    "input port polarity" : "active high",
    "mask port name" : "RW0_wmask",
    "mask port polarity" : "active high",
    "mask granularity" : 1
  } ]
}, {
  "type" : "sram",
  "name" : "smem_0_ext",
  "width" : 32,
  "depth" : "64",
  "ports" : [ {
    "address port name" : "RW0_addr",
    "address port polarity" : "active high",
    "clock port name" : "RW0_clk",
    "clock port polarity" : "active high",
    "write enable port name" : "RW0_wmode",
    "write enable port polarity" : "active high",
    "chip enable port name" : "RW0_en",
    "chip enable port polarity" : "active high",
    "output port name" : "RW0_rdata",
    "output port polarity" : "active high",
    "input port name" : "RW0_wdata",
    "input port polarity" : "active high",
    "mask port name" : "RW0_wmask",
    "mask port polarity" : "active high",
    "mask granularity" : 1
  } ]
} ]
""").getOrElse(List())

  writeToMem(mem, memSRAMs)

  val output = // TODO: check correctness...
    """
circuit smem_0_ext :
  module _T_182_ext :
    input R0_addr : UInt<6>
    input R0_clk : Clock
    output R0_data : UInt<88>
    input R0_en : UInt<1>
    input W0_addr : UInt<6>
    input W0_clk : Clock
    input W0_data : UInt<88>
    input W0_en : UInt<1>
    input W0_mask : UInt<4>

    node R0_addr_sel = bits(R0_addr, 5, 5)
    reg R0_addr_sel_reg : UInt<1>, R0_clk with :
      reset => (UInt<1>("h0"), R0_addr_sel_reg)
    R0_addr_sel_reg <= mux(R0_en, R0_addr_sel, R0_addr_sel_reg)
    node W0_addr_sel = bits(W0_addr, 5, 5)
    inst mem_0_0 of my_sram_2rw_32x22
    inst mem_0_1 of my_sram_2rw_32x22
    inst mem_0_2 of my_sram_2rw_32x22
    inst mem_0_3 of my_sram_2rw_32x22
    mem_0_0.CE1 <= W0_clk
    mem_0_0.A1 <= W0_addr
    mem_0_0.I1 <= bits(W0_data, 21, 0)
    mem_0_0.OEB1 <= not(and(and(not(UInt<1>("h1")), W0_en), eq(W0_addr_sel, UInt<1>("h0"))))
    mem_0_0.WEB1 <= not(and(and(UInt<1>("h1"), bits(W0_mask, 0, 0)), eq(W0_addr_sel, UInt<1>("h0"))))
    mem_0_0.CSB1 <= not(and(W0_en, eq(W0_addr_sel, UInt<1>("h0"))))
    mem_0_1.CE1 <= W0_clk
    mem_0_1.A1 <= W0_addr
    mem_0_1.I1 <= bits(W0_data, 43, 22)
    mem_0_1.OEB1 <= not(and(and(not(UInt<1>("h1")), W0_en), eq(W0_addr_sel, UInt<1>("h0"))))
    mem_0_1.WEB1 <= not(and(and(UInt<1>("h1"), bits(W0_mask, 1, 1)), eq(W0_addr_sel, UInt<1>("h0"))))
    mem_0_1.CSB1 <= not(and(W0_en, eq(W0_addr_sel, UInt<1>("h0"))))
    mem_0_2.CE1 <= W0_clk
    mem_0_2.A1 <= W0_addr
    mem_0_2.I1 <= bits(W0_data, 65, 44)
    mem_0_2.OEB1 <= not(and(and(not(UInt<1>("h1")), W0_en), eq(W0_addr_sel, UInt<1>("h0"))))
    mem_0_2.WEB1 <= not(and(and(UInt<1>("h1"), bits(W0_mask, 2, 2)), eq(W0_addr_sel, UInt<1>("h0"))))
    mem_0_2.CSB1 <= not(and(W0_en, eq(W0_addr_sel, UInt<1>("h0"))))
    mem_0_3.CE1 <= W0_clk
    mem_0_3.A1 <= W0_addr
    mem_0_3.I1 <= bits(W0_data, 87, 66)
    mem_0_3.OEB1 <= not(and(and(not(UInt<1>("h1")), W0_en), eq(W0_addr_sel, UInt<1>("h0"))))
    mem_0_3.WEB1 <= not(and(and(UInt<1>("h1"), bits(W0_mask, 3, 3)), eq(W0_addr_sel, UInt<1>("h0"))))
    mem_0_3.CSB1 <= not(and(W0_en, eq(W0_addr_sel, UInt<1>("h0"))))
    mem_0_0.CE2 <= R0_clk
    mem_0_0.A2 <= R0_addr
    node R0_data_0_0 = bits(mem_0_0.O2, 21, 0)
    mem_0_0.I2 is invalid
    mem_0_0.OEB2 <= not(and(and(not(UInt<1>("h0")), R0_en), eq(R0_addr_sel, UInt<1>("h0"))))
    mem_0_0.WEB2 <= not(and(and(UInt<1>("h0"), UInt<1>("h1")), eq(R0_addr_sel, UInt<1>("h0"))))
    mem_0_0.CSB2 <= not(and(R0_en, eq(R0_addr_sel, UInt<1>("h0"))))
    mem_0_1.CE2 <= R0_clk
    mem_0_1.A2 <= R0_addr
    node R0_data_0_1 = bits(mem_0_1.O2, 21, 0)
    mem_0_1.I2 is invalid
    mem_0_1.OEB2 <= not(and(and(not(UInt<1>("h0")), R0_en), eq(R0_addr_sel, UInt<1>("h0"))))
    mem_0_1.WEB2 <= not(and(and(UInt<1>("h0"), UInt<1>("h1")), eq(R0_addr_sel, UInt<1>("h0"))))
    mem_0_1.CSB2 <= not(and(R0_en, eq(R0_addr_sel, UInt<1>("h0"))))
    mem_0_2.CE2 <= R0_clk
    mem_0_2.A2 <= R0_addr
    node R0_data_0_2 = bits(mem_0_2.O2, 21, 0)
    mem_0_2.I2 is invalid
    mem_0_2.OEB2 <= not(and(and(not(UInt<1>("h0")), R0_en), eq(R0_addr_sel, UInt<1>("h0"))))
    mem_0_2.WEB2 <= not(and(and(UInt<1>("h0"), UInt<1>("h1")), eq(R0_addr_sel, UInt<1>("h0"))))
    mem_0_2.CSB2 <= not(and(R0_en, eq(R0_addr_sel, UInt<1>("h0"))))
    mem_0_3.CE2 <= R0_clk
    mem_0_3.A2 <= R0_addr
    node R0_data_0_3 = bits(mem_0_3.O2, 21, 0)
    mem_0_3.I2 is invalid
    mem_0_3.OEB2 <= not(and(and(not(UInt<1>("h0")), R0_en), eq(R0_addr_sel, UInt<1>("h0"))))
    mem_0_3.WEB2 <= not(and(and(UInt<1>("h0"), UInt<1>("h1")), eq(R0_addr_sel, UInt<1>("h0"))))
    mem_0_3.CSB2 <= not(and(R0_en, eq(R0_addr_sel, UInt<1>("h0"))))
    node R0_data_0 = cat(R0_data_0_3, cat(R0_data_0_2, cat(R0_data_0_1, R0_data_0_0)))
    inst mem_1_0 of my_sram_2rw_32x22
    inst mem_1_1 of my_sram_2rw_32x22
    inst mem_1_2 of my_sram_2rw_32x22
    inst mem_1_3 of my_sram_2rw_32x22
    mem_1_0.CE1 <= W0_clk
    mem_1_0.A1 <= W0_addr
    mem_1_0.I1 <= bits(W0_data, 21, 0)
    mem_1_0.OEB1 <= not(and(and(not(UInt<1>("h1")), W0_en), eq(W0_addr_sel, UInt<1>("h1"))))
    mem_1_0.WEB1 <= not(and(and(UInt<1>("h1"), bits(W0_mask, 0, 0)), eq(W0_addr_sel, UInt<1>("h1"))))
    mem_1_0.CSB1 <= not(and(W0_en, eq(W0_addr_sel, UInt<1>("h1"))))
    mem_1_1.CE1 <= W0_clk
    mem_1_1.A1 <= W0_addr
    mem_1_1.I1 <= bits(W0_data, 43, 22)
    mem_1_1.OEB1 <= not(and(and(not(UInt<1>("h1")), W0_en), eq(W0_addr_sel, UInt<1>("h1"))))
    mem_1_1.WEB1 <= not(and(and(UInt<1>("h1"), bits(W0_mask, 1, 1)), eq(W0_addr_sel, UInt<1>("h1"))))
    mem_1_1.CSB1 <= not(and(W0_en, eq(W0_addr_sel, UInt<1>("h1"))))
    mem_1_2.CE1 <= W0_clk
    mem_1_2.A1 <= W0_addr
    mem_1_2.I1 <= bits(W0_data, 65, 44)
    mem_1_2.OEB1 <= not(and(and(not(UInt<1>("h1")), W0_en), eq(W0_addr_sel, UInt<1>("h1"))))
    mem_1_2.WEB1 <= not(and(and(UInt<1>("h1"), bits(W0_mask, 2, 2)), eq(W0_addr_sel, UInt<1>("h1"))))
    mem_1_2.CSB1 <= not(and(W0_en, eq(W0_addr_sel, UInt<1>("h1"))))
    mem_1_3.CE1 <= W0_clk
    mem_1_3.A1 <= W0_addr
    mem_1_3.I1 <= bits(W0_data, 87, 66)
    mem_1_3.OEB1 <= not(and(and(not(UInt<1>("h1")), W0_en), eq(W0_addr_sel, UInt<1>("h1"))))
    mem_1_3.WEB1 <= not(and(and(UInt<1>("h1"), bits(W0_mask, 3, 3)), eq(W0_addr_sel, UInt<1>("h1"))))
    mem_1_3.CSB1 <= not(and(W0_en, eq(W0_addr_sel, UInt<1>("h1"))))
    mem_1_0.CE2 <= R0_clk
    mem_1_0.A2 <= R0_addr
    node R0_data_1_0 = bits(mem_1_0.O2, 21, 0)
    mem_1_0.I2 is invalid
    mem_1_0.OEB2 <= not(and(and(not(UInt<1>("h0")), R0_en), eq(R0_addr_sel, UInt<1>("h1"))))
    mem_1_0.WEB2 <= not(and(and(UInt<1>("h0"), UInt<1>("h1")), eq(R0_addr_sel, UInt<1>("h1"))))
    mem_1_0.CSB2 <= not(and(R0_en, eq(R0_addr_sel, UInt<1>("h1"))))
    mem_1_1.CE2 <= R0_clk
    mem_1_1.A2 <= R0_addr
    node R0_data_1_1 = bits(mem_1_1.O2, 21, 0)
    mem_1_1.I2 is invalid
    mem_1_1.OEB2 <= not(and(and(not(UInt<1>("h0")), R0_en), eq(R0_addr_sel, UInt<1>("h1"))))
    mem_1_1.WEB2 <= not(and(and(UInt<1>("h0"), UInt<1>("h1")), eq(R0_addr_sel, UInt<1>("h1"))))
    mem_1_1.CSB2 <= not(and(R0_en, eq(R0_addr_sel, UInt<1>("h1"))))
    mem_1_2.CE2 <= R0_clk
    mem_1_2.A2 <= R0_addr
    node R0_data_1_2 = bits(mem_1_2.O2, 21, 0)
    mem_1_2.I2 is invalid
    mem_1_2.OEB2 <= not(and(and(not(UInt<1>("h0")), R0_en), eq(R0_addr_sel, UInt<1>("h1"))))
    mem_1_2.WEB2 <= not(and(and(UInt<1>("h0"), UInt<1>("h1")), eq(R0_addr_sel, UInt<1>("h1"))))
    mem_1_2.CSB2 <= not(and(R0_en, eq(R0_addr_sel, UInt<1>("h1"))))
    mem_1_3.CE2 <= R0_clk
    mem_1_3.A2 <= R0_addr
    node R0_data_1_3 = bits(mem_1_3.O2, 21, 0)
    mem_1_3.I2 is invalid
    mem_1_3.OEB2 <= not(and(and(not(UInt<1>("h0")), R0_en), eq(R0_addr_sel, UInt<1>("h1"))))
    mem_1_3.WEB2 <= not(and(and(UInt<1>("h0"), UInt<1>("h1")), eq(R0_addr_sel, UInt<1>("h1"))))
    mem_1_3.CSB2 <= not(and(R0_en, eq(R0_addr_sel, UInt<1>("h1"))))
    node R0_data_1 = cat(R0_data_1_3, cat(R0_data_1_2, cat(R0_data_1_1, R0_data_1_0)))
    R0_data <= mux(eq(R0_addr_sel_reg, UInt<1>("h0")), R0_data_0, mux(eq(R0_addr_sel_reg, UInt<1>("h1")), R0_data_1, UInt<88>("h0")))


  module _T_84_ext :
    input R0_addr : UInt<9>
    input R0_clk : Clock
    output R0_data : UInt<64>
    input R0_en : UInt<1>
    input W0_addr : UInt<9>
    input W0_clk : Clock
    input W0_data : UInt<64>
    input W0_en : UInt<1>
    input W0_mask : UInt<1>

    node R0_addr_sel = bits(R0_addr, 8, 7)
    reg R0_addr_sel_reg : UInt<2>, R0_clk with :
      reset => (UInt<1>("h0"), R0_addr_sel_reg)
    R0_addr_sel_reg <= mux(R0_en, R0_addr_sel, R0_addr_sel_reg)
    node W0_addr_sel = bits(W0_addr, 8, 7)
    inst mem_0_0 of my_sram_2rw_128x32
    inst mem_0_1 of my_sram_2rw_128x32
    mem_0_0.CE1 <= W0_clk
    mem_0_0.A1 <= W0_addr
    mem_0_0.I1 <= bits(W0_data, 31, 0)
    mem_0_0.OEB1 <= not(and(and(not(UInt<1>("h1")), W0_en), eq(W0_addr_sel, UInt<2>("h0"))))
    mem_0_0.WEB1 <= not(and(and(UInt<1>("h1"), bits(W0_mask, 0, 0)), eq(W0_addr_sel, UInt<2>("h0"))))
    mem_0_0.CSB1 <= not(and(W0_en, eq(W0_addr_sel, UInt<2>("h0"))))
    mem_0_1.CE1 <= W0_clk
    mem_0_1.A1 <= W0_addr
    mem_0_1.I1 <= bits(W0_data, 63, 32)
    mem_0_1.OEB1 <= not(and(and(not(UInt<1>("h1")), W0_en), eq(W0_addr_sel, UInt<2>("h0"))))
    mem_0_1.WEB1 <= not(and(and(UInt<1>("h1"), bits(W0_mask, 0, 0)), eq(W0_addr_sel, UInt<2>("h0"))))
    mem_0_1.CSB1 <= not(and(W0_en, eq(W0_addr_sel, UInt<2>("h0"))))
    mem_0_0.CE2 <= R0_clk
    mem_0_0.A2 <= R0_addr
    node R0_data_0_0 = bits(mem_0_0.O2, 31, 0)
    mem_0_0.I2 is invalid
    mem_0_0.OEB2 <= not(and(and(not(UInt<1>("h0")), R0_en), eq(R0_addr_sel, UInt<2>("h0"))))
    mem_0_0.WEB2 <= not(and(and(UInt<1>("h0"), UInt<1>("h1")), eq(R0_addr_sel, UInt<2>("h0"))))
    mem_0_0.CSB2 <= not(and(R0_en, eq(R0_addr_sel, UInt<2>("h0"))))
    mem_0_1.CE2 <= R0_clk
    mem_0_1.A2 <= R0_addr
    node R0_data_0_1 = bits(mem_0_1.O2, 31, 0)
    mem_0_1.I2 is invalid
    mem_0_1.OEB2 <= not(and(and(not(UInt<1>("h0")), R0_en), eq(R0_addr_sel, UInt<2>("h0"))))
    mem_0_1.WEB2 <= not(and(and(UInt<1>("h0"), UInt<1>("h1")), eq(R0_addr_sel, UInt<2>("h0"))))
    mem_0_1.CSB2 <= not(and(R0_en, eq(R0_addr_sel, UInt<2>("h0"))))
    node R0_data_0 = cat(R0_data_0_1, R0_data_0_0)
    inst mem_1_0 of my_sram_2rw_128x32
    inst mem_1_1 of my_sram_2rw_128x32
    mem_1_0.CE1 <= W0_clk
    mem_1_0.A1 <= W0_addr
    mem_1_0.I1 <= bits(W0_data, 31, 0)
    mem_1_0.OEB1 <= not(and(and(not(UInt<1>("h1")), W0_en), eq(W0_addr_sel, UInt<2>("h1"))))
    mem_1_0.WEB1 <= not(and(and(UInt<1>("h1"), bits(W0_mask, 0, 0)), eq(W0_addr_sel, UInt<2>("h1"))))
    mem_1_0.CSB1 <= not(and(W0_en, eq(W0_addr_sel, UInt<2>("h1"))))
    mem_1_1.CE1 <= W0_clk
    mem_1_1.A1 <= W0_addr
    mem_1_1.I1 <= bits(W0_data, 63, 32)
    mem_1_1.OEB1 <= not(and(and(not(UInt<1>("h1")), W0_en), eq(W0_addr_sel, UInt<2>("h1"))))
    mem_1_1.WEB1 <= not(and(and(UInt<1>("h1"), bits(W0_mask, 0, 0)), eq(W0_addr_sel, UInt<2>("h1"))))
    mem_1_1.CSB1 <= not(and(W0_en, eq(W0_addr_sel, UInt<2>("h1"))))
    mem_1_0.CE2 <= R0_clk
    mem_1_0.A2 <= R0_addr
    node R0_data_1_0 = bits(mem_1_0.O2, 31, 0)
    mem_1_0.I2 is invalid
    mem_1_0.OEB2 <= not(and(and(not(UInt<1>("h0")), R0_en), eq(R0_addr_sel, UInt<2>("h1"))))
    mem_1_0.WEB2 <= not(and(and(UInt<1>("h0"), UInt<1>("h1")), eq(R0_addr_sel, UInt<2>("h1"))))
    mem_1_0.CSB2 <= not(and(R0_en, eq(R0_addr_sel, UInt<2>("h1"))))
    mem_1_1.CE2 <= R0_clk
    mem_1_1.A2 <= R0_addr
    node R0_data_1_1 = bits(mem_1_1.O2, 31, 0)
    mem_1_1.I2 is invalid
    mem_1_1.OEB2 <= not(and(and(not(UInt<1>("h0")), R0_en), eq(R0_addr_sel, UInt<2>("h1"))))
    mem_1_1.WEB2 <= not(and(and(UInt<1>("h0"), UInt<1>("h1")), eq(R0_addr_sel, UInt<2>("h1"))))
    mem_1_1.CSB2 <= not(and(R0_en, eq(R0_addr_sel, UInt<2>("h1"))))
    node R0_data_1 = cat(R0_data_1_1, R0_data_1_0)
    inst mem_2_0 of my_sram_2rw_128x32
    inst mem_2_1 of my_sram_2rw_128x32
    mem_2_0.CE1 <= W0_clk
    mem_2_0.A1 <= W0_addr
    mem_2_0.I1 <= bits(W0_data, 31, 0)
    mem_2_0.OEB1 <= not(and(and(not(UInt<1>("h1")), W0_en), eq(W0_addr_sel, UInt<2>("h2"))))
    mem_2_0.WEB1 <= not(and(and(UInt<1>("h1"), bits(W0_mask, 0, 0)), eq(W0_addr_sel, UInt<2>("h2"))))
    mem_2_0.CSB1 <= not(and(W0_en, eq(W0_addr_sel, UInt<2>("h2"))))
    mem_2_1.CE1 <= W0_clk
    mem_2_1.A1 <= W0_addr
    mem_2_1.I1 <= bits(W0_data, 63, 32)
    mem_2_1.OEB1 <= not(and(and(not(UInt<1>("h1")), W0_en), eq(W0_addr_sel, UInt<2>("h2"))))
    mem_2_1.WEB1 <= not(and(and(UInt<1>("h1"), bits(W0_mask, 0, 0)), eq(W0_addr_sel, UInt<2>("h2"))))
    mem_2_1.CSB1 <= not(and(W0_en, eq(W0_addr_sel, UInt<2>("h2"))))
    mem_2_0.CE2 <= R0_clk
    mem_2_0.A2 <= R0_addr
    node R0_data_2_0 = bits(mem_2_0.O2, 31, 0)
    mem_2_0.I2 is invalid
    mem_2_0.OEB2 <= not(and(and(not(UInt<1>("h0")), R0_en), eq(R0_addr_sel, UInt<2>("h2"))))
    mem_2_0.WEB2 <= not(and(and(UInt<1>("h0"), UInt<1>("h1")), eq(R0_addr_sel, UInt<2>("h2"))))
    mem_2_0.CSB2 <= not(and(R0_en, eq(R0_addr_sel, UInt<2>("h2"))))
    mem_2_1.CE2 <= R0_clk
    mem_2_1.A2 <= R0_addr
    node R0_data_2_1 = bits(mem_2_1.O2, 31, 0)
    mem_2_1.I2 is invalid
    mem_2_1.OEB2 <= not(and(and(not(UInt<1>("h0")), R0_en), eq(R0_addr_sel, UInt<2>("h2"))))
    mem_2_1.WEB2 <= not(and(and(UInt<1>("h0"), UInt<1>("h1")), eq(R0_addr_sel, UInt<2>("h2"))))
    mem_2_1.CSB2 <= not(and(R0_en, eq(R0_addr_sel, UInt<2>("h2"))))
    node R0_data_2 = cat(R0_data_2_1, R0_data_2_0)
    inst mem_3_0 of my_sram_2rw_128x32
    inst mem_3_1 of my_sram_2rw_128x32
    mem_3_0.CE1 <= W0_clk
    mem_3_0.A1 <= W0_addr
    mem_3_0.I1 <= bits(W0_data, 31, 0)
    mem_3_0.OEB1 <= not(and(and(not(UInt<1>("h1")), W0_en), eq(W0_addr_sel, UInt<2>("h3"))))
    mem_3_0.WEB1 <= not(and(and(UInt<1>("h1"), bits(W0_mask, 0, 0)), eq(W0_addr_sel, UInt<2>("h3"))))
    mem_3_0.CSB1 <= not(and(W0_en, eq(W0_addr_sel, UInt<2>("h3"))))
    mem_3_1.CE1 <= W0_clk
    mem_3_1.A1 <= W0_addr
    mem_3_1.I1 <= bits(W0_data, 63, 32)
    mem_3_1.OEB1 <= not(and(and(not(UInt<1>("h1")), W0_en), eq(W0_addr_sel, UInt<2>("h3"))))
    mem_3_1.WEB1 <= not(and(and(UInt<1>("h1"), bits(W0_mask, 0, 0)), eq(W0_addr_sel, UInt<2>("h3"))))
    mem_3_1.CSB1 <= not(and(W0_en, eq(W0_addr_sel, UInt<2>("h3"))))
    mem_3_0.CE2 <= R0_clk
    mem_3_0.A2 <= R0_addr
    node R0_data_3_0 = bits(mem_3_0.O2, 31, 0)
    mem_3_0.I2 is invalid
    mem_3_0.OEB2 <= not(and(and(not(UInt<1>("h0")), R0_en), eq(R0_addr_sel, UInt<2>("h3"))))
    mem_3_0.WEB2 <= not(and(and(UInt<1>("h0"), UInt<1>("h1")), eq(R0_addr_sel, UInt<2>("h3"))))
    mem_3_0.CSB2 <= not(and(R0_en, eq(R0_addr_sel, UInt<2>("h3"))))
    mem_3_1.CE2 <= R0_clk
    mem_3_1.A2 <= R0_addr
    node R0_data_3_1 = bits(mem_3_1.O2, 31, 0)
    mem_3_1.I2 is invalid
    mem_3_1.OEB2 <= not(and(and(not(UInt<1>("h0")), R0_en), eq(R0_addr_sel, UInt<2>("h3"))))
    mem_3_1.WEB2 <= not(and(and(UInt<1>("h0"), UInt<1>("h1")), eq(R0_addr_sel, UInt<2>("h3"))))
    mem_3_1.CSB2 <= not(and(R0_en, eq(R0_addr_sel, UInt<2>("h3"))))
    node R0_data_3 = cat(R0_data_3_1, R0_data_3_0)
    R0_data <= mux(eq(R0_addr_sel_reg, UInt<2>("h0")), R0_data_0, mux(eq(R0_addr_sel_reg, UInt<2>("h1")), R0_data_1, mux(eq(R0_addr_sel_reg, UInt<2>("h2")), R0_data_2, mux(eq(R0_addr_sel_reg, UInt<2>("h3")), R0_data_3, UInt<64>("h0")))))

  extmodule my_sram_2rw_128x32 :
    input A1 : UInt<7>
    input CE1 : Clock
    input I1 : UInt<32>
    output O1 : UInt<32>
    input CSB1 : UInt<1>
    input OEB1 : UInt<1>
    input WEB1 : UInt<1>
    input A2 : UInt<7>
    input CE2 : Clock
    input I2 : UInt<32>
    output O2 : UInt<32>
    input CSB2 : UInt<1>
    input OEB2 : UInt<1>
    input WEB2 : UInt<1>

    defname = my_sram_2rw_128x32


  module tag_array_ext :
    input RW0_addr : UInt<6>
    input RW0_clk : Clock
    input RW0_wdata : UInt<80>
    output RW0_rdata : UInt<80>
    input RW0_en : UInt<1>
    input RW0_wmode : UInt<1>
    input RW0_wmask : UInt<4>

    inst mem_0_0 of my_sram_1rw_64x32
    inst mem_0_1 of my_sram_1rw_64x32
    inst mem_0_2 of my_sram_1rw_64x32
    inst mem_0_3 of my_sram_1rw_64x32
    mem_0_0.CE <= RW0_clk
    mem_0_0.A <= RW0_addr
    node RW0_rdata_0_0 = bits(mem_0_0.O, 19, 0)
    mem_0_0.I <= bits(RW0_wdata, 19, 0)
    mem_0_0.OEB <= not(and(and(not(RW0_wmode), RW0_en), UInt<1>("h1")))
    mem_0_0.WEB <= not(and(and(RW0_wmode, bits(RW0_wmask, 0, 0)), UInt<1>("h1")))
    mem_0_0.CSB <= not(and(RW0_en, UInt<1>("h1")))
    mem_0_1.CE <= RW0_clk
    mem_0_1.A <= RW0_addr
    node RW0_rdata_0_1 = bits(mem_0_1.O, 19, 0)
    mem_0_1.I <= bits(RW0_wdata, 39, 20)
    mem_0_1.OEB <= not(and(and(not(RW0_wmode), RW0_en), UInt<1>("h1")))
    mem_0_1.WEB <= not(and(and(RW0_wmode, bits(RW0_wmask, 1, 1)), UInt<1>("h1")))
    mem_0_1.CSB <= not(and(RW0_en, UInt<1>("h1")))
    mem_0_2.CE <= RW0_clk
    mem_0_2.A <= RW0_addr
    node RW0_rdata_0_2 = bits(mem_0_2.O, 19, 0)
    mem_0_2.I <= bits(RW0_wdata, 59, 40)
    mem_0_2.OEB <= not(and(and(not(RW0_wmode), RW0_en), UInt<1>("h1")))
    mem_0_2.WEB <= not(and(and(RW0_wmode, bits(RW0_wmask, 2, 2)), UInt<1>("h1")))
    mem_0_2.CSB <= not(and(RW0_en, UInt<1>("h1")))
    mem_0_3.CE <= RW0_clk
    mem_0_3.A <= RW0_addr
    node RW0_rdata_0_3 = bits(mem_0_3.O, 19, 0)
    mem_0_3.I <= bits(RW0_wdata, 79, 60)
    mem_0_3.OEB <= not(and(and(not(RW0_wmode), RW0_en), UInt<1>("h1")))
    mem_0_3.WEB <= not(and(and(RW0_wmode, bits(RW0_wmask, 3, 3)), UInt<1>("h1")))
    mem_0_3.CSB <= not(and(RW0_en, UInt<1>("h1")))
    node RW0_rdata_0 = cat(RW0_rdata_0_3, cat(RW0_rdata_0_2, cat(RW0_rdata_0_1, RW0_rdata_0_0)))
    RW0_rdata <= mux(UInt<1>("h1"), RW0_rdata_0, UInt<80>("h0"))

  extmodule my_sram_1rw_64x32 :
    input A : UInt<6>
    input CE : Clock
    input I : UInt<32>
    output O : UInt<32>
    input CSB : UInt<1>
    input OEB : UInt<1>
    input WEB : UInt<1>

    defname = my_sram_1rw_64x32


  module _T_886_ext :
    input RW0_addr : UInt<9>
    input RW0_clk : Clock
    input RW0_wdata : UInt<64>
    output RW0_rdata : UInt<64>
    input RW0_en : UInt<1>
    input RW0_wmode : UInt<1>

    inst mem_0_0 of my_sram_1rw_512x32
    inst mem_0_1 of my_sram_1rw_512x32
    mem_0_0.CE <= RW0_clk
    mem_0_0.A <= RW0_addr
    node RW0_rdata_0_0 = bits(mem_0_0.O, 31, 0)
    mem_0_0.I <= bits(RW0_wdata, 31, 0)
    mem_0_0.OEB <= not(and(and(not(RW0_wmode), RW0_en), UInt<1>("h1")))
    mem_0_0.WEB <= not(and(and(RW0_wmode, UInt<1>("h1")), UInt<1>("h1")))
    mem_0_0.CSB <= not(and(RW0_en, UInt<1>("h1")))
    mem_0_1.CE <= RW0_clk
    mem_0_1.A <= RW0_addr
    node RW0_rdata_0_1 = bits(mem_0_1.O, 31, 0)
    mem_0_1.I <= bits(RW0_wdata, 63, 32)
    mem_0_1.OEB <= not(and(and(not(RW0_wmode), RW0_en), UInt<1>("h1")))
    mem_0_1.WEB <= not(and(and(RW0_wmode, UInt<1>("h1")), UInt<1>("h1")))
    mem_0_1.CSB <= not(and(RW0_en, UInt<1>("h1")))
    node RW0_rdata_0 = cat(RW0_rdata_0_1, RW0_rdata_0_0)
    RW0_rdata <= mux(UInt<1>("h1"), RW0_rdata_0, UInt<64>("h0"))

  extmodule my_sram_1rw_512x32 :
    input A : UInt<9>
    input CE : Clock
    input I : UInt<32>
    output O : UInt<32>
    input CSB : UInt<1>
    input OEB : UInt<1>
    input WEB : UInt<1>

    defname = my_sram_1rw_512x32


  module entries_info_ext :
    input R0_addr : UInt<5>
    input R0_clk : Clock
    output R0_data : UInt<40>
    input R0_en : UInt<1>
    input W0_addr : UInt<5>
    input W0_clk : Clock
    input W0_data : UInt<40>
    input W0_en : UInt<1>

    inst mem_0_0 of my_sram_2rw_32x22
    inst mem_0_1 of my_sram_2rw_32x22
    mem_0_0.CE1 <= W0_clk
    mem_0_0.A1 <= W0_addr
    mem_0_0.I1 <= bits(W0_data, 21, 0)
    mem_0_0.OEB1 <= not(and(and(not(UInt<1>("h1")), W0_en), UInt<1>("h1")))
    mem_0_0.WEB1 <= not(and(and(UInt<1>("h1"), UInt<1>("h1")), UInt<1>("h1")))
    mem_0_0.CSB1 <= not(and(W0_en, UInt<1>("h1")))
    mem_0_1.CE1 <= W0_clk
    mem_0_1.A1 <= W0_addr
    mem_0_1.I1 <= bits(W0_data, 39, 22)
    mem_0_1.OEB1 <= not(and(and(not(UInt<1>("h1")), W0_en), UInt<1>("h1")))
    mem_0_1.WEB1 <= not(and(and(UInt<1>("h1"), UInt<1>("h1")), UInt<1>("h1")))
    mem_0_1.CSB1 <= not(and(W0_en, UInt<1>("h1")))
    mem_0_0.CE2 <= R0_clk
    mem_0_0.A2 <= R0_addr
    node R0_data_0_0 = bits(mem_0_0.O2, 21, 0)
    mem_0_0.I2 is invalid
    mem_0_0.OEB2 <= not(and(and(not(UInt<1>("h0")), R0_en), UInt<1>("h1")))
    mem_0_0.WEB2 <= not(and(and(UInt<1>("h0"), UInt<1>("h1")), UInt<1>("h1")))
    mem_0_0.CSB2 <= not(and(R0_en, UInt<1>("h1")))
    mem_0_1.CE2 <= R0_clk
    mem_0_1.A2 <= R0_addr
    node R0_data_0_1 = bits(mem_0_1.O2, 17, 0)
    mem_0_1.I2 is invalid
    mem_0_1.OEB2 <= not(and(and(not(UInt<1>("h0")), R0_en), UInt<1>("h1")))
    mem_0_1.WEB2 <= not(and(and(UInt<1>("h0"), UInt<1>("h1")), UInt<1>("h1")))
    mem_0_1.CSB2 <= not(and(R0_en, UInt<1>("h1")))
    node R0_data_0 = cat(R0_data_0_1, R0_data_0_0)
    R0_data <= mux(UInt<1>("h1"), R0_data_0, UInt<40>("h0"))

  extmodule my_sram_2rw_32x22 :
    input A1 : UInt<5>
    input CE1 : Clock
    input I1 : UInt<22>
    output O1 : UInt<22>
    input CSB1 : UInt<1>
    input OEB1 : UInt<1>
    input WEB1 : UInt<1>
    input A2 : UInt<5>
    input CE2 : Clock
    input I2 : UInt<22>
    output O2 : UInt<22>
    input CSB2 : UInt<1>
    input OEB2 : UInt<1>
    input WEB2 : UInt<1>

    defname = my_sram_2rw_32x22


  module smem_ext :
    input RW0_addr : UInt<5>
    input RW0_clk : Clock
    input RW0_wdata : UInt<32>
    output RW0_rdata : UInt<32>
    input RW0_en : UInt<1>
    input RW0_wmode : UInt<1>
    input RW0_wmask : UInt<32>

    inst mem_0_0 of my_sram_1rw_64x8
    inst mem_0_1 of my_sram_1rw_64x8
    inst mem_0_2 of my_sram_1rw_64x8
    inst mem_0_3 of my_sram_1rw_64x8
    inst mem_0_4 of my_sram_1rw_64x8
    inst mem_0_5 of my_sram_1rw_64x8
    inst mem_0_6 of my_sram_1rw_64x8
    inst mem_0_7 of my_sram_1rw_64x8
    inst mem_0_8 of my_sram_1rw_64x8
    inst mem_0_9 of my_sram_1rw_64x8
    inst mem_0_10 of my_sram_1rw_64x8
    inst mem_0_11 of my_sram_1rw_64x8
    inst mem_0_12 of my_sram_1rw_64x8
    inst mem_0_13 of my_sram_1rw_64x8
    inst mem_0_14 of my_sram_1rw_64x8
    inst mem_0_15 of my_sram_1rw_64x8
    inst mem_0_16 of my_sram_1rw_64x8
    inst mem_0_17 of my_sram_1rw_64x8
    inst mem_0_18 of my_sram_1rw_64x8
    inst mem_0_19 of my_sram_1rw_64x8
    inst mem_0_20 of my_sram_1rw_64x8
    inst mem_0_21 of my_sram_1rw_64x8
    inst mem_0_22 of my_sram_1rw_64x8
    inst mem_0_23 of my_sram_1rw_64x8
    inst mem_0_24 of my_sram_1rw_64x8
    inst mem_0_25 of my_sram_1rw_64x8
    inst mem_0_26 of my_sram_1rw_64x8
    inst mem_0_27 of my_sram_1rw_64x8
    inst mem_0_28 of my_sram_1rw_64x8
    inst mem_0_29 of my_sram_1rw_64x8
    inst mem_0_30 of my_sram_1rw_64x8
    inst mem_0_31 of my_sram_1rw_64x8
    mem_0_0.CE <= RW0_clk
    mem_0_0.A <= RW0_addr
    node RW0_rdata_0_0 = bits(mem_0_0.O, 0, 0)
    mem_0_0.I <= bits(RW0_wdata, 0, 0)
    mem_0_0.OEB <= not(and(and(not(RW0_wmode), RW0_en), UInt<1>("h1")))
    mem_0_0.WEB <= not(and(and(RW0_wmode, bits(RW0_wmask, 0, 0)), UInt<1>("h1")))
    mem_0_0.CSB <= not(and(RW0_en, UInt<1>("h1")))
    mem_0_1.CE <= RW0_clk
    mem_0_1.A <= RW0_addr
    node RW0_rdata_0_1 = bits(mem_0_1.O, 0, 0)
    mem_0_1.I <= bits(RW0_wdata, 1, 1)
    mem_0_1.OEB <= not(and(and(not(RW0_wmode), RW0_en), UInt<1>("h1")))
    mem_0_1.WEB <= not(and(and(RW0_wmode, bits(RW0_wmask, 1, 1)), UInt<1>("h1")))
    mem_0_1.CSB <= not(and(RW0_en, UInt<1>("h1")))
    mem_0_2.CE <= RW0_clk
    mem_0_2.A <= RW0_addr
    node RW0_rdata_0_2 = bits(mem_0_2.O, 0, 0)
    mem_0_2.I <= bits(RW0_wdata, 2, 2)
    mem_0_2.OEB <= not(and(and(not(RW0_wmode), RW0_en), UInt<1>("h1")))
    mem_0_2.WEB <= not(and(and(RW0_wmode, bits(RW0_wmask, 2, 2)), UInt<1>("h1")))
    mem_0_2.CSB <= not(and(RW0_en, UInt<1>("h1")))
    mem_0_3.CE <= RW0_clk
    mem_0_3.A <= RW0_addr
    node RW0_rdata_0_3 = bits(mem_0_3.O, 0, 0)
    mem_0_3.I <= bits(RW0_wdata, 3, 3)
    mem_0_3.OEB <= not(and(and(not(RW0_wmode), RW0_en), UInt<1>("h1")))
    mem_0_3.WEB <= not(and(and(RW0_wmode, bits(RW0_wmask, 3, 3)), UInt<1>("h1")))
    mem_0_3.CSB <= not(and(RW0_en, UInt<1>("h1")))
    mem_0_4.CE <= RW0_clk
    mem_0_4.A <= RW0_addr
    node RW0_rdata_0_4 = bits(mem_0_4.O, 0, 0)
    mem_0_4.I <= bits(RW0_wdata, 4, 4)
    mem_0_4.OEB <= not(and(and(not(RW0_wmode), RW0_en), UInt<1>("h1")))
    mem_0_4.WEB <= not(and(and(RW0_wmode, bits(RW0_wmask, 4, 4)), UInt<1>("h1")))
    mem_0_4.CSB <= not(and(RW0_en, UInt<1>("h1")))
    mem_0_5.CE <= RW0_clk
    mem_0_5.A <= RW0_addr
    node RW0_rdata_0_5 = bits(mem_0_5.O, 0, 0)
    mem_0_5.I <= bits(RW0_wdata, 5, 5)
    mem_0_5.OEB <= not(and(and(not(RW0_wmode), RW0_en), UInt<1>("h1")))
    mem_0_5.WEB <= not(and(and(RW0_wmode, bits(RW0_wmask, 5, 5)), UInt<1>("h1")))
    mem_0_5.CSB <= not(and(RW0_en, UInt<1>("h1")))
    mem_0_6.CE <= RW0_clk
    mem_0_6.A <= RW0_addr
    node RW0_rdata_0_6 = bits(mem_0_6.O, 0, 0)
    mem_0_6.I <= bits(RW0_wdata, 6, 6)
    mem_0_6.OEB <= not(and(and(not(RW0_wmode), RW0_en), UInt<1>("h1")))
    mem_0_6.WEB <= not(and(and(RW0_wmode, bits(RW0_wmask, 6, 6)), UInt<1>("h1")))
    mem_0_6.CSB <= not(and(RW0_en, UInt<1>("h1")))
    mem_0_7.CE <= RW0_clk
    mem_0_7.A <= RW0_addr
    node RW0_rdata_0_7 = bits(mem_0_7.O, 0, 0)
    mem_0_7.I <= bits(RW0_wdata, 7, 7)
    mem_0_7.OEB <= not(and(and(not(RW0_wmode), RW0_en), UInt<1>("h1")))
    mem_0_7.WEB <= not(and(and(RW0_wmode, bits(RW0_wmask, 7, 7)), UInt<1>("h1")))
    mem_0_7.CSB <= not(and(RW0_en, UInt<1>("h1")))
    mem_0_8.CE <= RW0_clk
    mem_0_8.A <= RW0_addr
    node RW0_rdata_0_8 = bits(mem_0_8.O, 0, 0)
    mem_0_8.I <= bits(RW0_wdata, 8, 8)
    mem_0_8.OEB <= not(and(and(not(RW0_wmode), RW0_en), UInt<1>("h1")))
    mem_0_8.WEB <= not(and(and(RW0_wmode, bits(RW0_wmask, 8, 8)), UInt<1>("h1")))
    mem_0_8.CSB <= not(and(RW0_en, UInt<1>("h1")))
    mem_0_9.CE <= RW0_clk
    mem_0_9.A <= RW0_addr
    node RW0_rdata_0_9 = bits(mem_0_9.O, 0, 0)
    mem_0_9.I <= bits(RW0_wdata, 9, 9)
    mem_0_9.OEB <= not(and(and(not(RW0_wmode), RW0_en), UInt<1>("h1")))
    mem_0_9.WEB <= not(and(and(RW0_wmode, bits(RW0_wmask, 9, 9)), UInt<1>("h1")))
    mem_0_9.CSB <= not(and(RW0_en, UInt<1>("h1")))
    mem_0_10.CE <= RW0_clk
    mem_0_10.A <= RW0_addr
    node RW0_rdata_0_10 = bits(mem_0_10.O, 0, 0)
    mem_0_10.I <= bits(RW0_wdata, 10, 10)
    mem_0_10.OEB <= not(and(and(not(RW0_wmode), RW0_en), UInt<1>("h1")))
    mem_0_10.WEB <= not(and(and(RW0_wmode, bits(RW0_wmask, 10, 10)), UInt<1>("h1")))
    mem_0_10.CSB <= not(and(RW0_en, UInt<1>("h1")))
    mem_0_11.CE <= RW0_clk
    mem_0_11.A <= RW0_addr
    node RW0_rdata_0_11 = bits(mem_0_11.O, 0, 0)
    mem_0_11.I <= bits(RW0_wdata, 11, 11)
    mem_0_11.OEB <= not(and(and(not(RW0_wmode), RW0_en), UInt<1>("h1")))
    mem_0_11.WEB <= not(and(and(RW0_wmode, bits(RW0_wmask, 11, 11)), UInt<1>("h1")))
    mem_0_11.CSB <= not(and(RW0_en, UInt<1>("h1")))
    mem_0_12.CE <= RW0_clk
    mem_0_12.A <= RW0_addr
    node RW0_rdata_0_12 = bits(mem_0_12.O, 0, 0)
    mem_0_12.I <= bits(RW0_wdata, 12, 12)
    mem_0_12.OEB <= not(and(and(not(RW0_wmode), RW0_en), UInt<1>("h1")))
    mem_0_12.WEB <= not(and(and(RW0_wmode, bits(RW0_wmask, 12, 12)), UInt<1>("h1")))
    mem_0_12.CSB <= not(and(RW0_en, UInt<1>("h1")))
    mem_0_13.CE <= RW0_clk
    mem_0_13.A <= RW0_addr
    node RW0_rdata_0_13 = bits(mem_0_13.O, 0, 0)
    mem_0_13.I <= bits(RW0_wdata, 13, 13)
    mem_0_13.OEB <= not(and(and(not(RW0_wmode), RW0_en), UInt<1>("h1")))
    mem_0_13.WEB <= not(and(and(RW0_wmode, bits(RW0_wmask, 13, 13)), UInt<1>("h1")))
    mem_0_13.CSB <= not(and(RW0_en, UInt<1>("h1")))
    mem_0_14.CE <= RW0_clk
    mem_0_14.A <= RW0_addr
    node RW0_rdata_0_14 = bits(mem_0_14.O, 0, 0)
    mem_0_14.I <= bits(RW0_wdata, 14, 14)
    mem_0_14.OEB <= not(and(and(not(RW0_wmode), RW0_en), UInt<1>("h1")))
    mem_0_14.WEB <= not(and(and(RW0_wmode, bits(RW0_wmask, 14, 14)), UInt<1>("h1")))
    mem_0_14.CSB <= not(and(RW0_en, UInt<1>("h1")))
    mem_0_15.CE <= RW0_clk
    mem_0_15.A <= RW0_addr
    node RW0_rdata_0_15 = bits(mem_0_15.O, 0, 0)
    mem_0_15.I <= bits(RW0_wdata, 15, 15)
    mem_0_15.OEB <= not(and(and(not(RW0_wmode), RW0_en), UInt<1>("h1")))
    mem_0_15.WEB <= not(and(and(RW0_wmode, bits(RW0_wmask, 15, 15)), UInt<1>("h1")))
    mem_0_15.CSB <= not(and(RW0_en, UInt<1>("h1")))
    mem_0_16.CE <= RW0_clk
    mem_0_16.A <= RW0_addr
    node RW0_rdata_0_16 = bits(mem_0_16.O, 0, 0)
    mem_0_16.I <= bits(RW0_wdata, 16, 16)
    mem_0_16.OEB <= not(and(and(not(RW0_wmode), RW0_en), UInt<1>("h1")))
    mem_0_16.WEB <= not(and(and(RW0_wmode, bits(RW0_wmask, 16, 16)), UInt<1>("h1")))
    mem_0_16.CSB <= not(and(RW0_en, UInt<1>("h1")))
    mem_0_17.CE <= RW0_clk
    mem_0_17.A <= RW0_addr
    node RW0_rdata_0_17 = bits(mem_0_17.O, 0, 0)
    mem_0_17.I <= bits(RW0_wdata, 17, 17)
    mem_0_17.OEB <= not(and(and(not(RW0_wmode), RW0_en), UInt<1>("h1")))
    mem_0_17.WEB <= not(and(and(RW0_wmode, bits(RW0_wmask, 17, 17)), UInt<1>("h1")))
    mem_0_17.CSB <= not(and(RW0_en, UInt<1>("h1")))
    mem_0_18.CE <= RW0_clk
    mem_0_18.A <= RW0_addr
    node RW0_rdata_0_18 = bits(mem_0_18.O, 0, 0)
    mem_0_18.I <= bits(RW0_wdata, 18, 18)
    mem_0_18.OEB <= not(and(and(not(RW0_wmode), RW0_en), UInt<1>("h1")))
    mem_0_18.WEB <= not(and(and(RW0_wmode, bits(RW0_wmask, 18, 18)), UInt<1>("h1")))
    mem_0_18.CSB <= not(and(RW0_en, UInt<1>("h1")))
    mem_0_19.CE <= RW0_clk
    mem_0_19.A <= RW0_addr
    node RW0_rdata_0_19 = bits(mem_0_19.O, 0, 0)
    mem_0_19.I <= bits(RW0_wdata, 19, 19)
    mem_0_19.OEB <= not(and(and(not(RW0_wmode), RW0_en), UInt<1>("h1")))
    mem_0_19.WEB <= not(and(and(RW0_wmode, bits(RW0_wmask, 19, 19)), UInt<1>("h1")))
    mem_0_19.CSB <= not(and(RW0_en, UInt<1>("h1")))
    mem_0_20.CE <= RW0_clk
    mem_0_20.A <= RW0_addr
    node RW0_rdata_0_20 = bits(mem_0_20.O, 0, 0)
    mem_0_20.I <= bits(RW0_wdata, 20, 20)
    mem_0_20.OEB <= not(and(and(not(RW0_wmode), RW0_en), UInt<1>("h1")))
    mem_0_20.WEB <= not(and(and(RW0_wmode, bits(RW0_wmask, 20, 20)), UInt<1>("h1")))
    mem_0_20.CSB <= not(and(RW0_en, UInt<1>("h1")))
    mem_0_21.CE <= RW0_clk
    mem_0_21.A <= RW0_addr
    node RW0_rdata_0_21 = bits(mem_0_21.O, 0, 0)
    mem_0_21.I <= bits(RW0_wdata, 21, 21)
    mem_0_21.OEB <= not(and(and(not(RW0_wmode), RW0_en), UInt<1>("h1")))
    mem_0_21.WEB <= not(and(and(RW0_wmode, bits(RW0_wmask, 21, 21)), UInt<1>("h1")))
    mem_0_21.CSB <= not(and(RW0_en, UInt<1>("h1")))
    mem_0_22.CE <= RW0_clk
    mem_0_22.A <= RW0_addr
    node RW0_rdata_0_22 = bits(mem_0_22.O, 0, 0)
    mem_0_22.I <= bits(RW0_wdata, 22, 22)
    mem_0_22.OEB <= not(and(and(not(RW0_wmode), RW0_en), UInt<1>("h1")))
    mem_0_22.WEB <= not(and(and(RW0_wmode, bits(RW0_wmask, 22, 22)), UInt<1>("h1")))
    mem_0_22.CSB <= not(and(RW0_en, UInt<1>("h1")))
    mem_0_23.CE <= RW0_clk
    mem_0_23.A <= RW0_addr
    node RW0_rdata_0_23 = bits(mem_0_23.O, 0, 0)
    mem_0_23.I <= bits(RW0_wdata, 23, 23)
    mem_0_23.OEB <= not(and(and(not(RW0_wmode), RW0_en), UInt<1>("h1")))
    mem_0_23.WEB <= not(and(and(RW0_wmode, bits(RW0_wmask, 23, 23)), UInt<1>("h1")))
    mem_0_23.CSB <= not(and(RW0_en, UInt<1>("h1")))
    mem_0_24.CE <= RW0_clk
    mem_0_24.A <= RW0_addr
    node RW0_rdata_0_24 = bits(mem_0_24.O, 0, 0)
    mem_0_24.I <= bits(RW0_wdata, 24, 24)
    mem_0_24.OEB <= not(and(and(not(RW0_wmode), RW0_en), UInt<1>("h1")))
    mem_0_24.WEB <= not(and(and(RW0_wmode, bits(RW0_wmask, 24, 24)), UInt<1>("h1")))
    mem_0_24.CSB <= not(and(RW0_en, UInt<1>("h1")))
    mem_0_25.CE <= RW0_clk
    mem_0_25.A <= RW0_addr
    node RW0_rdata_0_25 = bits(mem_0_25.O, 0, 0)
    mem_0_25.I <= bits(RW0_wdata, 25, 25)
    mem_0_25.OEB <= not(and(and(not(RW0_wmode), RW0_en), UInt<1>("h1")))
    mem_0_25.WEB <= not(and(and(RW0_wmode, bits(RW0_wmask, 25, 25)), UInt<1>("h1")))
    mem_0_25.CSB <= not(and(RW0_en, UInt<1>("h1")))
    mem_0_26.CE <= RW0_clk
    mem_0_26.A <= RW0_addr
    node RW0_rdata_0_26 = bits(mem_0_26.O, 0, 0)
    mem_0_26.I <= bits(RW0_wdata, 26, 26)
    mem_0_26.OEB <= not(and(and(not(RW0_wmode), RW0_en), UInt<1>("h1")))
    mem_0_26.WEB <= not(and(and(RW0_wmode, bits(RW0_wmask, 26, 26)), UInt<1>("h1")))
    mem_0_26.CSB <= not(and(RW0_en, UInt<1>("h1")))
    mem_0_27.CE <= RW0_clk
    mem_0_27.A <= RW0_addr
    node RW0_rdata_0_27 = bits(mem_0_27.O, 0, 0)
    mem_0_27.I <= bits(RW0_wdata, 27, 27)
    mem_0_27.OEB <= not(and(and(not(RW0_wmode), RW0_en), UInt<1>("h1")))
    mem_0_27.WEB <= not(and(and(RW0_wmode, bits(RW0_wmask, 27, 27)), UInt<1>("h1")))
    mem_0_27.CSB <= not(and(RW0_en, UInt<1>("h1")))
    mem_0_28.CE <= RW0_clk
    mem_0_28.A <= RW0_addr
    node RW0_rdata_0_28 = bits(mem_0_28.O, 0, 0)
    mem_0_28.I <= bits(RW0_wdata, 28, 28)
    mem_0_28.OEB <= not(and(and(not(RW0_wmode), RW0_en), UInt<1>("h1")))
    mem_0_28.WEB <= not(and(and(RW0_wmode, bits(RW0_wmask, 28, 28)), UInt<1>("h1")))
    mem_0_28.CSB <= not(and(RW0_en, UInt<1>("h1")))
    mem_0_29.CE <= RW0_clk
    mem_0_29.A <= RW0_addr
    node RW0_rdata_0_29 = bits(mem_0_29.O, 0, 0)
    mem_0_29.I <= bits(RW0_wdata, 29, 29)
    mem_0_29.OEB <= not(and(and(not(RW0_wmode), RW0_en), UInt<1>("h1")))
    mem_0_29.WEB <= not(and(and(RW0_wmode, bits(RW0_wmask, 29, 29)), UInt<1>("h1")))
    mem_0_29.CSB <= not(and(RW0_en, UInt<1>("h1")))
    mem_0_30.CE <= RW0_clk
    mem_0_30.A <= RW0_addr
    node RW0_rdata_0_30 = bits(mem_0_30.O, 0, 0)
    mem_0_30.I <= bits(RW0_wdata, 30, 30)
    mem_0_30.OEB <= not(and(and(not(RW0_wmode), RW0_en), UInt<1>("h1")))
    mem_0_30.WEB <= not(and(and(RW0_wmode, bits(RW0_wmask, 30, 30)), UInt<1>("h1")))
    mem_0_30.CSB <= not(and(RW0_en, UInt<1>("h1")))
    mem_0_31.CE <= RW0_clk
    mem_0_31.A <= RW0_addr
    node RW0_rdata_0_31 = bits(mem_0_31.O, 0, 0)
    mem_0_31.I <= bits(RW0_wdata, 31, 31)
    mem_0_31.OEB <= not(and(and(not(RW0_wmode), RW0_en), UInt<1>("h1")))
    mem_0_31.WEB <= not(and(and(RW0_wmode, bits(RW0_wmask, 31, 31)), UInt<1>("h1")))
    mem_0_31.CSB <= not(and(RW0_en, UInt<1>("h1")))
    node RW0_rdata_0 = cat(RW0_rdata_0_31, cat(RW0_rdata_0_30, cat(RW0_rdata_0_29, cat(RW0_rdata_0_28, cat(RW0_rdata_0_27, cat(RW0_rdata_0_26, cat(RW0_rdata_0_25, cat(RW0_rdata_0_24, cat(RW0_rdata_0_23, cat(RW0_rdata_0_22, cat(RW0_rdata_0_21, cat(RW0_rdata_0_20, cat(RW0_rdata_0_19, cat(RW0_rdata_0_18, cat(RW0_rdata_0_17, cat(RW0_rdata_0_16, cat(RW0_rdata_0_15, cat(RW0_rdata_0_14, cat(RW0_rdata_0_13, cat(RW0_rdata_0_12, cat(RW0_rdata_0_11, cat(RW0_rdata_0_10, cat(RW0_rdata_0_9, cat(RW0_rdata_0_8, cat(RW0_rdata_0_7, cat(RW0_rdata_0_6, cat(RW0_rdata_0_5, cat(RW0_rdata_0_4, cat(RW0_rdata_0_3, cat(RW0_rdata_0_2, cat(RW0_rdata_0_1, RW0_rdata_0_0)))))))))))))))))))))))))))))))
    RW0_rdata <= mux(UInt<1>("h1"), RW0_rdata_0, UInt<32>("h0"))

  module smem_0_ext :
    input RW0_addr : UInt<6>
    input RW0_clk : Clock
    input RW0_wdata : UInt<32>
    output RW0_rdata : UInt<32>
    input RW0_en : UInt<1>
    input RW0_wmode : UInt<1>
    input RW0_wmask : UInt<32>

    inst mem_0_0 of my_sram_1rw_64x8
    inst mem_0_1 of my_sram_1rw_64x8
    inst mem_0_2 of my_sram_1rw_64x8
    inst mem_0_3 of my_sram_1rw_64x8
    inst mem_0_4 of my_sram_1rw_64x8
    inst mem_0_5 of my_sram_1rw_64x8
    inst mem_0_6 of my_sram_1rw_64x8
    inst mem_0_7 of my_sram_1rw_64x8
    inst mem_0_8 of my_sram_1rw_64x8
    inst mem_0_9 of my_sram_1rw_64x8
    inst mem_0_10 of my_sram_1rw_64x8
    inst mem_0_11 of my_sram_1rw_64x8
    inst mem_0_12 of my_sram_1rw_64x8
    inst mem_0_13 of my_sram_1rw_64x8
    inst mem_0_14 of my_sram_1rw_64x8
    inst mem_0_15 of my_sram_1rw_64x8
    inst mem_0_16 of my_sram_1rw_64x8
    inst mem_0_17 of my_sram_1rw_64x8
    inst mem_0_18 of my_sram_1rw_64x8
    inst mem_0_19 of my_sram_1rw_64x8
    inst mem_0_20 of my_sram_1rw_64x8
    inst mem_0_21 of my_sram_1rw_64x8
    inst mem_0_22 of my_sram_1rw_64x8
    inst mem_0_23 of my_sram_1rw_64x8
    inst mem_0_24 of my_sram_1rw_64x8
    inst mem_0_25 of my_sram_1rw_64x8
    inst mem_0_26 of my_sram_1rw_64x8
    inst mem_0_27 of my_sram_1rw_64x8
    inst mem_0_28 of my_sram_1rw_64x8
    inst mem_0_29 of my_sram_1rw_64x8
    inst mem_0_30 of my_sram_1rw_64x8
    inst mem_0_31 of my_sram_1rw_64x8
    mem_0_0.CE <= RW0_clk
    mem_0_0.A <= RW0_addr
    node RW0_rdata_0_0 = bits(mem_0_0.O, 0, 0)
    mem_0_0.I <= bits(RW0_wdata, 0, 0)
    mem_0_0.OEB <= not(and(and(not(RW0_wmode), RW0_en), UInt<1>("h1")))
    mem_0_0.WEB <= not(and(and(RW0_wmode, bits(RW0_wmask, 0, 0)), UInt<1>("h1")))
    mem_0_0.CSB <= not(and(RW0_en, UInt<1>("h1")))
    mem_0_1.CE <= RW0_clk
    mem_0_1.A <= RW0_addr
    node RW0_rdata_0_1 = bits(mem_0_1.O, 0, 0)
    mem_0_1.I <= bits(RW0_wdata, 1, 1)
    mem_0_1.OEB <= not(and(and(not(RW0_wmode), RW0_en), UInt<1>("h1")))
    mem_0_1.WEB <= not(and(and(RW0_wmode, bits(RW0_wmask, 1, 1)), UInt<1>("h1")))
    mem_0_1.CSB <= not(and(RW0_en, UInt<1>("h1")))
    mem_0_2.CE <= RW0_clk
    mem_0_2.A <= RW0_addr
    node RW0_rdata_0_2 = bits(mem_0_2.O, 0, 0)
    mem_0_2.I <= bits(RW0_wdata, 2, 2)
    mem_0_2.OEB <= not(and(and(not(RW0_wmode), RW0_en), UInt<1>("h1")))
    mem_0_2.WEB <= not(and(and(RW0_wmode, bits(RW0_wmask, 2, 2)), UInt<1>("h1")))
    mem_0_2.CSB <= not(and(RW0_en, UInt<1>("h1")))
    mem_0_3.CE <= RW0_clk
    mem_0_3.A <= RW0_addr
    node RW0_rdata_0_3 = bits(mem_0_3.O, 0, 0)
    mem_0_3.I <= bits(RW0_wdata, 3, 3)
    mem_0_3.OEB <= not(and(and(not(RW0_wmode), RW0_en), UInt<1>("h1")))
    mem_0_3.WEB <= not(and(and(RW0_wmode, bits(RW0_wmask, 3, 3)), UInt<1>("h1")))
    mem_0_3.CSB <= not(and(RW0_en, UInt<1>("h1")))
    mem_0_4.CE <= RW0_clk
    mem_0_4.A <= RW0_addr
    node RW0_rdata_0_4 = bits(mem_0_4.O, 0, 0)
    mem_0_4.I <= bits(RW0_wdata, 4, 4)
    mem_0_4.OEB <= not(and(and(not(RW0_wmode), RW0_en), UInt<1>("h1")))
    mem_0_4.WEB <= not(and(and(RW0_wmode, bits(RW0_wmask, 4, 4)), UInt<1>("h1")))
    mem_0_4.CSB <= not(and(RW0_en, UInt<1>("h1")))
    mem_0_5.CE <= RW0_clk
    mem_0_5.A <= RW0_addr
    node RW0_rdata_0_5 = bits(mem_0_5.O, 0, 0)
    mem_0_5.I <= bits(RW0_wdata, 5, 5)
    mem_0_5.OEB <= not(and(and(not(RW0_wmode), RW0_en), UInt<1>("h1")))
    mem_0_5.WEB <= not(and(and(RW0_wmode, bits(RW0_wmask, 5, 5)), UInt<1>("h1")))
    mem_0_5.CSB <= not(and(RW0_en, UInt<1>("h1")))
    mem_0_6.CE <= RW0_clk
    mem_0_6.A <= RW0_addr
    node RW0_rdata_0_6 = bits(mem_0_6.O, 0, 0)
    mem_0_6.I <= bits(RW0_wdata, 6, 6)
    mem_0_6.OEB <= not(and(and(not(RW0_wmode), RW0_en), UInt<1>("h1")))
    mem_0_6.WEB <= not(and(and(RW0_wmode, bits(RW0_wmask, 6, 6)), UInt<1>("h1")))
    mem_0_6.CSB <= not(and(RW0_en, UInt<1>("h1")))
    mem_0_7.CE <= RW0_clk
    mem_0_7.A <= RW0_addr
    node RW0_rdata_0_7 = bits(mem_0_7.O, 0, 0)
    mem_0_7.I <= bits(RW0_wdata, 7, 7)
    mem_0_7.OEB <= not(and(and(not(RW0_wmode), RW0_en), UInt<1>("h1")))
    mem_0_7.WEB <= not(and(and(RW0_wmode, bits(RW0_wmask, 7, 7)), UInt<1>("h1")))
    mem_0_7.CSB <= not(and(RW0_en, UInt<1>("h1")))
    mem_0_8.CE <= RW0_clk
    mem_0_8.A <= RW0_addr
    node RW0_rdata_0_8 = bits(mem_0_8.O, 0, 0)
    mem_0_8.I <= bits(RW0_wdata, 8, 8)
    mem_0_8.OEB <= not(and(and(not(RW0_wmode), RW0_en), UInt<1>("h1")))
    mem_0_8.WEB <= not(and(and(RW0_wmode, bits(RW0_wmask, 8, 8)), UInt<1>("h1")))
    mem_0_8.CSB <= not(and(RW0_en, UInt<1>("h1")))
    mem_0_9.CE <= RW0_clk
    mem_0_9.A <= RW0_addr
    node RW0_rdata_0_9 = bits(mem_0_9.O, 0, 0)
    mem_0_9.I <= bits(RW0_wdata, 9, 9)
    mem_0_9.OEB <= not(and(and(not(RW0_wmode), RW0_en), UInt<1>("h1")))
    mem_0_9.WEB <= not(and(and(RW0_wmode, bits(RW0_wmask, 9, 9)), UInt<1>("h1")))
    mem_0_9.CSB <= not(and(RW0_en, UInt<1>("h1")))
    mem_0_10.CE <= RW0_clk
    mem_0_10.A <= RW0_addr
    node RW0_rdata_0_10 = bits(mem_0_10.O, 0, 0)
    mem_0_10.I <= bits(RW0_wdata, 10, 10)
    mem_0_10.OEB <= not(and(and(not(RW0_wmode), RW0_en), UInt<1>("h1")))
    mem_0_10.WEB <= not(and(and(RW0_wmode, bits(RW0_wmask, 10, 10)), UInt<1>("h1")))
    mem_0_10.CSB <= not(and(RW0_en, UInt<1>("h1")))
    mem_0_11.CE <= RW0_clk
    mem_0_11.A <= RW0_addr
    node RW0_rdata_0_11 = bits(mem_0_11.O, 0, 0)
    mem_0_11.I <= bits(RW0_wdata, 11, 11)
    mem_0_11.OEB <= not(and(and(not(RW0_wmode), RW0_en), UInt<1>("h1")))
    mem_0_11.WEB <= not(and(and(RW0_wmode, bits(RW0_wmask, 11, 11)), UInt<1>("h1")))
    mem_0_11.CSB <= not(and(RW0_en, UInt<1>("h1")))
    mem_0_12.CE <= RW0_clk
    mem_0_12.A <= RW0_addr
    node RW0_rdata_0_12 = bits(mem_0_12.O, 0, 0)
    mem_0_12.I <= bits(RW0_wdata, 12, 12)
    mem_0_12.OEB <= not(and(and(not(RW0_wmode), RW0_en), UInt<1>("h1")))
    mem_0_12.WEB <= not(and(and(RW0_wmode, bits(RW0_wmask, 12, 12)), UInt<1>("h1")))
    mem_0_12.CSB <= not(and(RW0_en, UInt<1>("h1")))
    mem_0_13.CE <= RW0_clk
    mem_0_13.A <= RW0_addr
    node RW0_rdata_0_13 = bits(mem_0_13.O, 0, 0)
    mem_0_13.I <= bits(RW0_wdata, 13, 13)
    mem_0_13.OEB <= not(and(and(not(RW0_wmode), RW0_en), UInt<1>("h1")))
    mem_0_13.WEB <= not(and(and(RW0_wmode, bits(RW0_wmask, 13, 13)), UInt<1>("h1")))
    mem_0_13.CSB <= not(and(RW0_en, UInt<1>("h1")))
    mem_0_14.CE <= RW0_clk
    mem_0_14.A <= RW0_addr
    node RW0_rdata_0_14 = bits(mem_0_14.O, 0, 0)
    mem_0_14.I <= bits(RW0_wdata, 14, 14)
    mem_0_14.OEB <= not(and(and(not(RW0_wmode), RW0_en), UInt<1>("h1")))
    mem_0_14.WEB <= not(and(and(RW0_wmode, bits(RW0_wmask, 14, 14)), UInt<1>("h1")))
    mem_0_14.CSB <= not(and(RW0_en, UInt<1>("h1")))
    mem_0_15.CE <= RW0_clk
    mem_0_15.A <= RW0_addr
    node RW0_rdata_0_15 = bits(mem_0_15.O, 0, 0)
    mem_0_15.I <= bits(RW0_wdata, 15, 15)
    mem_0_15.OEB <= not(and(and(not(RW0_wmode), RW0_en), UInt<1>("h1")))
    mem_0_15.WEB <= not(and(and(RW0_wmode, bits(RW0_wmask, 15, 15)), UInt<1>("h1")))
    mem_0_15.CSB <= not(and(RW0_en, UInt<1>("h1")))
    mem_0_16.CE <= RW0_clk
    mem_0_16.A <= RW0_addr
    node RW0_rdata_0_16 = bits(mem_0_16.O, 0, 0)
    mem_0_16.I <= bits(RW0_wdata, 16, 16)
    mem_0_16.OEB <= not(and(and(not(RW0_wmode), RW0_en), UInt<1>("h1")))
    mem_0_16.WEB <= not(and(and(RW0_wmode, bits(RW0_wmask, 16, 16)), UInt<1>("h1")))
    mem_0_16.CSB <= not(and(RW0_en, UInt<1>("h1")))
    mem_0_17.CE <= RW0_clk
    mem_0_17.A <= RW0_addr
    node RW0_rdata_0_17 = bits(mem_0_17.O, 0, 0)
    mem_0_17.I <= bits(RW0_wdata, 17, 17)
    mem_0_17.OEB <= not(and(and(not(RW0_wmode), RW0_en), UInt<1>("h1")))
    mem_0_17.WEB <= not(and(and(RW0_wmode, bits(RW0_wmask, 17, 17)), UInt<1>("h1")))
    mem_0_17.CSB <= not(and(RW0_en, UInt<1>("h1")))
    mem_0_18.CE <= RW0_clk
    mem_0_18.A <= RW0_addr
    node RW0_rdata_0_18 = bits(mem_0_18.O, 0, 0)
    mem_0_18.I <= bits(RW0_wdata, 18, 18)
    mem_0_18.OEB <= not(and(and(not(RW0_wmode), RW0_en), UInt<1>("h1")))
    mem_0_18.WEB <= not(and(and(RW0_wmode, bits(RW0_wmask, 18, 18)), UInt<1>("h1")))
    mem_0_18.CSB <= not(and(RW0_en, UInt<1>("h1")))
    mem_0_19.CE <= RW0_clk
    mem_0_19.A <= RW0_addr
    node RW0_rdata_0_19 = bits(mem_0_19.O, 0, 0)
    mem_0_19.I <= bits(RW0_wdata, 19, 19)
    mem_0_19.OEB <= not(and(and(not(RW0_wmode), RW0_en), UInt<1>("h1")))
    mem_0_19.WEB <= not(and(and(RW0_wmode, bits(RW0_wmask, 19, 19)), UInt<1>("h1")))
    mem_0_19.CSB <= not(and(RW0_en, UInt<1>("h1")))
    mem_0_20.CE <= RW0_clk
    mem_0_20.A <= RW0_addr
    node RW0_rdata_0_20 = bits(mem_0_20.O, 0, 0)
    mem_0_20.I <= bits(RW0_wdata, 20, 20)
    mem_0_20.OEB <= not(and(and(not(RW0_wmode), RW0_en), UInt<1>("h1")))
    mem_0_20.WEB <= not(and(and(RW0_wmode, bits(RW0_wmask, 20, 20)), UInt<1>("h1")))
    mem_0_20.CSB <= not(and(RW0_en, UInt<1>("h1")))
    mem_0_21.CE <= RW0_clk
    mem_0_21.A <= RW0_addr
    node RW0_rdata_0_21 = bits(mem_0_21.O, 0, 0)
    mem_0_21.I <= bits(RW0_wdata, 21, 21)
    mem_0_21.OEB <= not(and(and(not(RW0_wmode), RW0_en), UInt<1>("h1")))
    mem_0_21.WEB <= not(and(and(RW0_wmode, bits(RW0_wmask, 21, 21)), UInt<1>("h1")))
    mem_0_21.CSB <= not(and(RW0_en, UInt<1>("h1")))
    mem_0_22.CE <= RW0_clk
    mem_0_22.A <= RW0_addr
    node RW0_rdata_0_22 = bits(mem_0_22.O, 0, 0)
    mem_0_22.I <= bits(RW0_wdata, 22, 22)
    mem_0_22.OEB <= not(and(and(not(RW0_wmode), RW0_en), UInt<1>("h1")))
    mem_0_22.WEB <= not(and(and(RW0_wmode, bits(RW0_wmask, 22, 22)), UInt<1>("h1")))
    mem_0_22.CSB <= not(and(RW0_en, UInt<1>("h1")))
    mem_0_23.CE <= RW0_clk
    mem_0_23.A <= RW0_addr
    node RW0_rdata_0_23 = bits(mem_0_23.O, 0, 0)
    mem_0_23.I <= bits(RW0_wdata, 23, 23)
    mem_0_23.OEB <= not(and(and(not(RW0_wmode), RW0_en), UInt<1>("h1")))
    mem_0_23.WEB <= not(and(and(RW0_wmode, bits(RW0_wmask, 23, 23)), UInt<1>("h1")))
    mem_0_23.CSB <= not(and(RW0_en, UInt<1>("h1")))
    mem_0_24.CE <= RW0_clk
    mem_0_24.A <= RW0_addr
    node RW0_rdata_0_24 = bits(mem_0_24.O, 0, 0)
    mem_0_24.I <= bits(RW0_wdata, 24, 24)
    mem_0_24.OEB <= not(and(and(not(RW0_wmode), RW0_en), UInt<1>("h1")))
    mem_0_24.WEB <= not(and(and(RW0_wmode, bits(RW0_wmask, 24, 24)), UInt<1>("h1")))
    mem_0_24.CSB <= not(and(RW0_en, UInt<1>("h1")))
    mem_0_25.CE <= RW0_clk
    mem_0_25.A <= RW0_addr
    node RW0_rdata_0_25 = bits(mem_0_25.O, 0, 0)
    mem_0_25.I <= bits(RW0_wdata, 25, 25)
    mem_0_25.OEB <= not(and(and(not(RW0_wmode), RW0_en), UInt<1>("h1")))
    mem_0_25.WEB <= not(and(and(RW0_wmode, bits(RW0_wmask, 25, 25)), UInt<1>("h1")))
    mem_0_25.CSB <= not(and(RW0_en, UInt<1>("h1")))
    mem_0_26.CE <= RW0_clk
    mem_0_26.A <= RW0_addr
    node RW0_rdata_0_26 = bits(mem_0_26.O, 0, 0)
    mem_0_26.I <= bits(RW0_wdata, 26, 26)
    mem_0_26.OEB <= not(and(and(not(RW0_wmode), RW0_en), UInt<1>("h1")))
    mem_0_26.WEB <= not(and(and(RW0_wmode, bits(RW0_wmask, 26, 26)), UInt<1>("h1")))
    mem_0_26.CSB <= not(and(RW0_en, UInt<1>("h1")))
    mem_0_27.CE <= RW0_clk
    mem_0_27.A <= RW0_addr
    node RW0_rdata_0_27 = bits(mem_0_27.O, 0, 0)
    mem_0_27.I <= bits(RW0_wdata, 27, 27)
    mem_0_27.OEB <= not(and(and(not(RW0_wmode), RW0_en), UInt<1>("h1")))
    mem_0_27.WEB <= not(and(and(RW0_wmode, bits(RW0_wmask, 27, 27)), UInt<1>("h1")))
    mem_0_27.CSB <= not(and(RW0_en, UInt<1>("h1")))
    mem_0_28.CE <= RW0_clk
    mem_0_28.A <= RW0_addr
    node RW0_rdata_0_28 = bits(mem_0_28.O, 0, 0)
    mem_0_28.I <= bits(RW0_wdata, 28, 28)
    mem_0_28.OEB <= not(and(and(not(RW0_wmode), RW0_en), UInt<1>("h1")))
    mem_0_28.WEB <= not(and(and(RW0_wmode, bits(RW0_wmask, 28, 28)), UInt<1>("h1")))
    mem_0_28.CSB <= not(and(RW0_en, UInt<1>("h1")))
    mem_0_29.CE <= RW0_clk
    mem_0_29.A <= RW0_addr
    node RW0_rdata_0_29 = bits(mem_0_29.O, 0, 0)
    mem_0_29.I <= bits(RW0_wdata, 29, 29)
    mem_0_29.OEB <= not(and(and(not(RW0_wmode), RW0_en), UInt<1>("h1")))
    mem_0_29.WEB <= not(and(and(RW0_wmode, bits(RW0_wmask, 29, 29)), UInt<1>("h1")))
    mem_0_29.CSB <= not(and(RW0_en, UInt<1>("h1")))
    mem_0_30.CE <= RW0_clk
    mem_0_30.A <= RW0_addr
    node RW0_rdata_0_30 = bits(mem_0_30.O, 0, 0)
    mem_0_30.I <= bits(RW0_wdata, 30, 30)
    mem_0_30.OEB <= not(and(and(not(RW0_wmode), RW0_en), UInt<1>("h1")))
    mem_0_30.WEB <= not(and(and(RW0_wmode, bits(RW0_wmask, 30, 30)), UInt<1>("h1")))
    mem_0_30.CSB <= not(and(RW0_en, UInt<1>("h1")))
    mem_0_31.CE <= RW0_clk
    mem_0_31.A <= RW0_addr
    node RW0_rdata_0_31 = bits(mem_0_31.O, 0, 0)
    mem_0_31.I <= bits(RW0_wdata, 31, 31)
    mem_0_31.OEB <= not(and(and(not(RW0_wmode), RW0_en), UInt<1>("h1")))
    mem_0_31.WEB <= not(and(and(RW0_wmode, bits(RW0_wmask, 31, 31)), UInt<1>("h1")))
    mem_0_31.CSB <= not(and(RW0_en, UInt<1>("h1")))
    node RW0_rdata_0 = cat(RW0_rdata_0_31, cat(RW0_rdata_0_30, cat(RW0_rdata_0_29, cat(RW0_rdata_0_28, cat(RW0_rdata_0_27, cat(RW0_rdata_0_26, cat(RW0_rdata_0_25, cat(RW0_rdata_0_24, cat(RW0_rdata_0_23, cat(RW0_rdata_0_22, cat(RW0_rdata_0_21, cat(RW0_rdata_0_20, cat(RW0_rdata_0_19, cat(RW0_rdata_0_18, cat(RW0_rdata_0_17, cat(RW0_rdata_0_16, cat(RW0_rdata_0_15, cat(RW0_rdata_0_14, cat(RW0_rdata_0_13, cat(RW0_rdata_0_12, cat(RW0_rdata_0_11, cat(RW0_rdata_0_10, cat(RW0_rdata_0_9, cat(RW0_rdata_0_8, cat(RW0_rdata_0_7, cat(RW0_rdata_0_6, cat(RW0_rdata_0_5, cat(RW0_rdata_0_4, cat(RW0_rdata_0_3, cat(RW0_rdata_0_2, cat(RW0_rdata_0_1, RW0_rdata_0_0)))))))))))))))))))))))))))))))
    RW0_rdata <= mux(UInt<1>("h1"), RW0_rdata_0, UInt<32>("h0"))

  extmodule my_sram_1rw_64x8 :
    input A : UInt<6>
    input CE : Clock
    input I : UInt<8>
    output O : UInt<8>
    input CSB : UInt<1>
    input OEB : UInt<1>
    input WEB : UInt<1>

    defname = my_sram_1rw_64x8
"""

  it should "compile, execute and test the boom test" in {
    compileExecuteAndTest(mem, lib, v, output)
  }
}

class SmallTagArrayTest extends MacroCompilerSpec with HasSRAMGenerator with HasSimpleTestGenerator {
  // Test that mapping a smaller memory using a larger lib can still work.
  override def memWidth:      Int = 26
  override def memDepth:      BigInt = BigInt(2)
  override def memMaskGran:   Option[Int] = Some(26)
  override def memPortPrefix: String = ""

  override def libWidth:      Int = 32
  override def libDepth:      BigInt = BigInt(64)
  override def libMaskGran:   Option[Int] = Some(1)
  override def libPortPrefix: String = ""

  override def extraPorts: Seq[MacroExtraPort] = Seq(
    MacroExtraPort(name = "must_be_one", portType = Constant, width = 1, value = 1)
  )

  override def generateBody(): String =
    s"""
       |    inst mem_0_0 of $lib_name
       |    mem_0_0.must_be_one <= UInt<1>("h1")
       |    mem_0_0.clk <= clk
       |    mem_0_0.addr <= addr
       |    node dout_0_0 = bits(mem_0_0.dout, 25, 0)
       |    mem_0_0.din <= bits(din, 25, 0)
       |    mem_0_0.mask <= cat(UInt<1>("h0"), cat(UInt<1>("h0"), cat(UInt<1>("h0"), cat(UInt<1>("h0"), cat(UInt<1>("h0"), cat(UInt<1>("h0"), cat(bits(mask, 0, 0), cat(bits(mask, 0, 0), cat(bits(mask, 0, 0), cat(bits(mask, 0, 0), cat(bits(mask, 0, 0), cat(bits(mask, 0, 0), cat(bits(mask, 0, 0), cat(bits(mask, 0, 0), cat(bits(mask, 0, 0), cat(bits(mask, 0, 0), cat(bits(mask, 0, 0), cat(bits(mask, 0, 0), cat(bits(mask, 0, 0), cat(bits(mask, 0, 0), cat(bits(mask, 0, 0), cat(bits(mask, 0, 0), cat(bits(mask, 0, 0), cat(bits(mask, 0, 0), cat(bits(mask, 0, 0), cat(bits(mask, 0, 0), cat(bits(mask, 0, 0), cat(bits(mask, 0, 0), cat(bits(mask, 0, 0), cat(bits(mask, 0, 0), cat(bits(mask, 0, 0), bits(mask, 0, 0))))))))))))))))))))))))))))))))
       |    mem_0_0.write_en <= and(and(write_en, UInt<1>("h1")), UInt<1>("h1"))
       |    node dout_0 = dout_0_0
       |    dout <= mux(UInt<1>("h1"), dout_0, UInt<26>("h0"))
    """.stripMargin

  it should "compile, execute, and test, the small tag array test" in {
    compileExecuteAndTest(mem, lib, v, output)
  }
}

class RocketChipTest extends MacroCompilerSpec with HasSRAMGenerator {
  val mem = s"mem-RocketChipTest.json"
  val lib = s"lib-RocketChipTest.json"
  val v = s"RocketChipTest.v"

  val libSRAMs = Seq(
    SRAMMacro(
      name = "SRAM1RW1024x8",
      depth = 1024,
      width = 8,
      family = "1rw",
      ports = Seq(
        generateReadWritePort("", 8, BigInt(1024))
      )
    ),
    SRAMMacro(
      name = "SRAM1RW512x32",
      depth = 512,
      width = 32,
      family = "1rw",
      ports = Seq(
        generateReadWritePort("", 32, BigInt(512))
      )
    ),
    SRAMMacro(
      name = "SRAM1RW64x128",
      depth = 64,
      width = 128,
      family = "1rw",
      ports = Seq(
        generateReadWritePort("", 128, BigInt(64))
      )
    ),
    SRAMMacro(
      name = "SRAM1RW64x32",
      depth = 64,
      width = 32,
      family = "1rw",
      ports = Seq(
        generateReadWritePort("", 32, BigInt(64))
      )
    ),
    SRAMMacro(
      name = "SRAM1RW64x8",
      depth = 64,
      width = 8,
      family = "1rw",
      ports = Seq(
        generateReadWritePort("", 8, BigInt(64))
      )
    ),
    SRAMMacro(
      name = "SRAM1RW512x8",
      depth = 512,
      width = 8,
      family = "1rw",
      ports = Seq(
        generateReadWritePort("", 8, BigInt(512))
      )
    ),
    SRAMMacro(
      name = "SRAM2RW64x32",
      depth = 64,
      width = 32,
      family = "1r1w",
      ports = Seq(
        generateReadPort("portA", 32, BigInt(64)),
        generateWritePort("portB", 32, BigInt(64))
      )
    )
  )

  val memSRAMs: Seq[mdf.macrolib.Macro] = mdf.macrolib.Utils
    .readMDFFromString("""
[
  {
    "type": "sram",
    "name": "tag_array_ext",
    "depth": 64,
    "width": 80,
    "ports": [
      {
        "clock port name": "RW0_clk",
        "mask granularity": 20,
        "output port name": "RW0_rdata",
        "input port name": "RW0_wdata",
        "address port name": "RW0_addr",
        "mask port name": "RW0_wmask",
        "chip enable port name": "RW0_en",
        "write enable port name": "RW0_wmode"
      }
    ]
  },
  {
    "type": "sram",
    "name": "T_1090_ext",
    "depth": 512,
    "width": 64,
    "ports": [
      {
        "clock port name": "RW0_clk",
        "output port name": "RW0_rdata",
        "input port name": "RW0_wdata",
        "address port name": "RW0_addr",
        "chip enable port name": "RW0_en",
        "write enable port name": "RW0_wmode"
      }
    ]
  },
  {
    "type": "sram",
    "name": "T_406_ext",
    "depth": 512,
    "width": 64,
    "ports": [
      {
        "clock port name": "RW0_clk",
        "mask granularity": 8,
        "output port name": "RW0_rdata",
        "input port name": "RW0_wdata",
        "address port name": "RW0_addr",
        "mask port name": "RW0_wmask",
        "chip enable port name": "RW0_en",
        "write enable port name": "RW0_wmode"
      }
    ]
  },
  {
    "type": "sram",
    "name": "T_2172_ext",
    "depth": 64,
    "width": 88,
    "ports": [
      {
        "clock port name": "W0_clk",
        "mask granularity": 22,
        "input port name": "W0_data",
        "address port name": "W0_addr",
        "chip enable port name": "W0_en",
        "mask port name": "W0_mask"
      },
      {
        "clock port name": "R0_clk",
        "output port name": "R0_data",
        "address port name": "R0_addr",
        "chip enable port name": "R0_en"
      }
    ]
  }
]
""").getOrElse(List())

  writeToLib(lib, libSRAMs)
  writeToMem(mem, memSRAMs)

  val output = // TODO: check correctness...
    """
circuit T_2172_ext :
  module tag_array_ext :
    input RW0_addr : UInt<6>
    input RW0_clk : Clock
    input RW0_wdata : UInt<80>
    output RW0_rdata : UInt<80>
    input RW0_en : UInt<1>
    input RW0_wmode : UInt<1>
    input RW0_wmask : UInt<4>

    inst mem_0_0 of SRAM1RW64x32
    inst mem_0_1 of SRAM1RW64x32
    inst mem_0_2 of SRAM1RW64x32
    inst mem_0_3 of SRAM1RW64x32
    mem_0_0.clk <= RW0_clk
    mem_0_0.addr <= RW0_addr
    node RW0_rdata_0_0 = bits(mem_0_0.dout, 19, 0)
    mem_0_0.din <= bits(RW0_wdata, 19, 0)
    mem_0_0.write_en <= and(and(RW0_wmode, bits(RW0_wmask, 0, 0)), UInt<1>("h1"))
    mem_0_1.clk <= RW0_clk
    mem_0_1.addr <= RW0_addr
    node RW0_rdata_0_1 = bits(mem_0_1.dout, 19, 0)
    mem_0_1.din <= bits(RW0_wdata, 39, 20)
    mem_0_1.write_en <= and(and(RW0_wmode, bits(RW0_wmask, 1, 1)), UInt<1>("h1"))
    mem_0_2.clk <= RW0_clk
    mem_0_2.addr <= RW0_addr
    node RW0_rdata_0_2 = bits(mem_0_2.dout, 19, 0)
    mem_0_2.din <= bits(RW0_wdata, 59, 40)
    mem_0_2.write_en <= and(and(RW0_wmode, bits(RW0_wmask, 2, 2)), UInt<1>("h1"))
    mem_0_3.clk <= RW0_clk
    mem_0_3.addr <= RW0_addr
    node RW0_rdata_0_3 = bits(mem_0_3.dout, 19, 0)
    mem_0_3.din <= bits(RW0_wdata, 79, 60)
    mem_0_3.write_en <= and(and(RW0_wmode, bits(RW0_wmask, 3, 3)), UInt<1>("h1"))
    node RW0_rdata_0 = cat(RW0_rdata_0_3, cat(RW0_rdata_0_2, cat(RW0_rdata_0_1, RW0_rdata_0_0)))
    RW0_rdata <= mux(UInt<1>("h1"), RW0_rdata_0, UInt<80>("h0"))

  extmodule SRAM1RW64x32 :
    input addr : UInt<6>
    input clk : Clock
    input din : UInt<32>
    output dout : UInt<32>
    input write_en : UInt<1>

    defname = SRAM1RW64x32

  module T_1090_ext :
    input RW0_addr : UInt<9>
    input RW0_clk : Clock
    input RW0_wdata : UInt<64>
    output RW0_rdata : UInt<64>
    input RW0_en : UInt<1>
    input RW0_wmode : UInt<1>

    inst mem_0_0 of SRAM1RW512x32
    inst mem_0_1 of SRAM1RW512x32
    mem_0_0.clk <= RW0_clk
    mem_0_0.addr <= RW0_addr
    node RW0_rdata_0_0 = bits(mem_0_0.dout, 31, 0)
    mem_0_0.din <= bits(RW0_wdata, 31, 0)
    mem_0_0.write_en <= and(and(RW0_wmode, UInt<1>("h1")), UInt<1>("h1"))
    mem_0_1.clk <= RW0_clk
    mem_0_1.addr <= RW0_addr
    node RW0_rdata_0_1 = bits(mem_0_1.dout, 31, 0)
    mem_0_1.din <= bits(RW0_wdata, 63, 32)
    mem_0_1.write_en <= and(and(RW0_wmode, UInt<1>("h1")), UInt<1>("h1"))
    node RW0_rdata_0 = cat(RW0_rdata_0_1, RW0_rdata_0_0)
    RW0_rdata <= mux(UInt<1>("h1"), RW0_rdata_0, UInt<64>("h0"))

  extmodule SRAM1RW512x32 :
    input addr : UInt<9>
    input clk : Clock
    input din : UInt<32>
    output dout : UInt<32>
    input write_en : UInt<1>

    defname = SRAM1RW512x32


  module T_406_ext :
    input RW0_addr : UInt<9>
    input RW0_clk : Clock
    input RW0_wdata : UInt<64>
    output RW0_rdata : UInt<64>
    input RW0_en : UInt<1>
    input RW0_wmode : UInt<1>
    input RW0_wmask : UInt<8>

    inst mem_0_0 of SRAM1RW512x8
    inst mem_0_1 of SRAM1RW512x8
    inst mem_0_2 of SRAM1RW512x8
    inst mem_0_3 of SRAM1RW512x8
    inst mem_0_4 of SRAM1RW512x8
    inst mem_0_5 of SRAM1RW512x8
    inst mem_0_6 of SRAM1RW512x8
    inst mem_0_7 of SRAM1RW512x8
    mem_0_0.clk <= RW0_clk
    mem_0_0.addr <= RW0_addr
    node RW0_rdata_0_0 = bits(mem_0_0.dout, 7, 0)
    mem_0_0.din <= bits(RW0_wdata, 7, 0)
    mem_0_0.write_en <= and(and(RW0_wmode, bits(RW0_wmask, 0, 0)), UInt<1>("h1"))
    mem_0_1.clk <= RW0_clk
    mem_0_1.addr <= RW0_addr
    node RW0_rdata_0_1 = bits(mem_0_1.dout, 7, 0)
    mem_0_1.din <= bits(RW0_wdata, 15, 8)
    mem_0_1.write_en <= and(and(RW0_wmode, bits(RW0_wmask, 1, 1)), UInt<1>("h1"))
    mem_0_2.clk <= RW0_clk
    mem_0_2.addr <= RW0_addr
    node RW0_rdata_0_2 = bits(mem_0_2.dout, 7, 0)
    mem_0_2.din <= bits(RW0_wdata, 23, 16)
    mem_0_2.write_en <= and(and(RW0_wmode, bits(RW0_wmask, 2, 2)), UInt<1>("h1"))
    mem_0_3.clk <= RW0_clk
    mem_0_3.addr <= RW0_addr
    node RW0_rdata_0_3 = bits(mem_0_3.dout, 7, 0)
    mem_0_3.din <= bits(RW0_wdata, 31, 24)
    mem_0_3.write_en <= and(and(RW0_wmode, bits(RW0_wmask, 3, 3)), UInt<1>("h1"))
    mem_0_4.clk <= RW0_clk
    mem_0_4.addr <= RW0_addr
    node RW0_rdata_0_4 = bits(mem_0_4.dout, 7, 0)
    mem_0_4.din <= bits(RW0_wdata, 39, 32)
    mem_0_4.write_en <= and(and(RW0_wmode, bits(RW0_wmask, 4, 4)), UInt<1>("h1"))
    mem_0_5.clk <= RW0_clk
    mem_0_5.addr <= RW0_addr
    node RW0_rdata_0_5 = bits(mem_0_5.dout, 7, 0)
    mem_0_5.din <= bits(RW0_wdata, 47, 40)
    mem_0_5.write_en <= and(and(RW0_wmode, bits(RW0_wmask, 5, 5)), UInt<1>("h1"))
    mem_0_6.clk <= RW0_clk
    mem_0_6.addr <= RW0_addr
    node RW0_rdata_0_6 = bits(mem_0_6.dout, 7, 0)
    mem_0_6.din <= bits(RW0_wdata, 55, 48)
    mem_0_6.write_en <= and(and(RW0_wmode, bits(RW0_wmask, 6, 6)), UInt<1>("h1"))
    mem_0_7.clk <= RW0_clk
    mem_0_7.addr <= RW0_addr
    node RW0_rdata_0_7 = bits(mem_0_7.dout, 7, 0)
    mem_0_7.din <= bits(RW0_wdata, 63, 56)
    mem_0_7.write_en <= and(and(RW0_wmode, bits(RW0_wmask, 7, 7)), UInt<1>("h1"))
    node RW0_rdata_0 = cat(RW0_rdata_0_7, cat(RW0_rdata_0_6, cat(RW0_rdata_0_5, cat(RW0_rdata_0_4, cat(RW0_rdata_0_3, cat(RW0_rdata_0_2, cat(RW0_rdata_0_1, RW0_rdata_0_0)))))))
    RW0_rdata <= mux(UInt<1>("h1"), RW0_rdata_0, UInt<64>("h0"))

  extmodule SRAM1RW512x8 :
    input addr : UInt<9>
    input clk : Clock
    input din : UInt<8>
    output dout : UInt<8>
    input write_en : UInt<1>

    defname = SRAM1RW512x8


  module T_2172_ext :
    input W0_addr : UInt<6>
    input W0_clk : Clock
    input W0_data : UInt<88>
    input W0_en : UInt<1>
    input W0_mask : UInt<4>
    input R0_addr : UInt<6>
    input R0_clk : Clock
    output R0_data : UInt<88>
    input R0_en : UInt<1>

    inst mem_0_0 of SRAM2RW64x32
    inst mem_0_1 of SRAM2RW64x32
    inst mem_0_2 of SRAM2RW64x32
    inst mem_0_3 of SRAM2RW64x32
    mem_0_0.portB_clk <= W0_clk
    mem_0_0.portB_addr <= W0_addr
    mem_0_0.portB_din <= bits(W0_data, 21, 0)
    mem_0_0.portB_write_en <= and(and(UInt<1>("h1"), bits(W0_mask, 0, 0)), UInt<1>("h1"))
    mem_0_1.portB_clk <= W0_clk
    mem_0_1.portB_addr <= W0_addr
    mem_0_1.portB_din <= bits(W0_data, 43, 22)
    mem_0_1.portB_write_en <= and(and(UInt<1>("h1"), bits(W0_mask, 1, 1)), UInt<1>("h1"))
    mem_0_2.portB_clk <= W0_clk
    mem_0_2.portB_addr <= W0_addr
    mem_0_2.portB_din <= bits(W0_data, 65, 44)
    mem_0_2.portB_write_en <= and(and(UInt<1>("h1"), bits(W0_mask, 2, 2)), UInt<1>("h1"))
    mem_0_3.portB_clk <= W0_clk
    mem_0_3.portB_addr <= W0_addr
    mem_0_3.portB_din <= bits(W0_data, 87, 66)
    mem_0_3.portB_write_en <= and(and(UInt<1>("h1"), bits(W0_mask, 3, 3)), UInt<1>("h1"))
    mem_0_0.portA_clk <= R0_clk
    mem_0_0.portA_addr <= R0_addr
    node R0_data_0_0 = bits(mem_0_0.portA_dout, 21, 0)
    mem_0_1.portA_clk <= R0_clk
    mem_0_1.portA_addr <= R0_addr
    node R0_data_0_1 = bits(mem_0_1.portA_dout, 21, 0)
    mem_0_2.portA_clk <= R0_clk
    mem_0_2.portA_addr <= R0_addr
    node R0_data_0_2 = bits(mem_0_2.portA_dout, 21, 0)
    mem_0_3.portA_clk <= R0_clk
    mem_0_3.portA_addr <= R0_addr
    node R0_data_0_3 = bits(mem_0_3.portA_dout, 21, 0)
    node R0_data_0 = cat(R0_data_0_3, cat(R0_data_0_2, cat(R0_data_0_1, R0_data_0_0)))
    R0_data <= mux(UInt<1>("h1"), R0_data_0, UInt<88>("h0"))

  extmodule SRAM2RW64x32 :
    input portA_addr : UInt<6>
    input portA_clk : Clock
    output portA_dout : UInt<32>
    input portB_addr : UInt<6>
    input portB_clk : Clock
    input portB_din : UInt<32>
    input portB_write_en : UInt<1>

    defname = SRAM2RW64x32
"""

  // TODO FIXME: Enable this test when firrtl #644 https://github.com/freechipsproject/firrtl/issues/644 is fixed
  "rocket example" should "work" in {
    pending
  }
  //~ compileExecuteAndTest(mem, lib, v, output)
}
