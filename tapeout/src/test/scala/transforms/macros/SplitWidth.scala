package barstools.tapeout.transforms.macros

import java.io.File

class SplitWidth2048x16_mrw extends MacroCompilerSpec {
  val mem = new File(macroDir, "mem-2048x16-mrw.json")
  val lib = new File(macroDir, "lib-2048x8-mrw.json")
  val v = new File(testDir, "split_width_2048x16_mrw.v")
  val output =
"""
circuit name_of_sram_module :
  module name_of_sram_module :
    input clock : Clock
    input RW0A : UInt<11>
    input RW0I : UInt<16>
    output RW0O : UInt<16>
    input RW0E : UInt<1>
    input RW0W : UInt<1>
    input RW0M : UInt<2>

    inst mem_0_0 of vendor_sram
    inst mem_0_1 of vendor_sram
    mem_0_0.clock <= clock
    mem_0_0.RW0A <= RW0A
    node RW0O_0_0 = bits(mem_0_0.RW0O, 7, 0)
    mem_0_0.RW0I <= bits(RW0I, 7, 0)
    mem_0_0.RW0M <= bits(RW0M, 0, 0)
    mem_0_0.RW0W <= and(RW0W, UInt<1>("h1"))
    mem_0_0.RW0E <= and(RW0E, UInt<1>("h1"))
    mem_0_1.clock <= clock
    mem_0_1.RW0A <= RW0A
    node RW0O_0_1 = bits(mem_0_1.RW0O, 7, 0)
    mem_0_1.RW0I <= bits(RW0I, 15, 8)
    mem_0_1.RW0M <= bits(RW0M, 1, 1)
    mem_0_1.RW0W <= and(RW0W, UInt<1>("h1"))
    mem_0_1.RW0E <= and(RW0E, UInt<1>("h1"))
    node RW0O_0 = cat(RW0O_0_1, RW0O_0_0)
    RW0O <= mux(UInt<1>("h1"), RW0O_0, UInt<1>("h0"))

  extmodule vendor_sram :
    input clock : Clock
    input RW0A : UInt<11>
    input RW0I : UInt<8>
    output RW0O : UInt<8>
    input RW0E : UInt<1>
    input RW0W : UInt<1>
    input RW0M : UInt<1>

    defname = vendor_sram
"""
  compile(mem, Some(lib), v, false)
  execute(Some(mem), Some(lib), false, output)
}

class SplitWidth2048x16_mrw_Uneven extends MacroCompilerSpec {
  val mem = new File(macroDir, "mem-2048x16-mrw.json")
  val lib = new File(macroDir, "lib-2048x10-rw.json")
  val v = new File(testDir, "split_width_2048x16_mrw_uneven.v")
  val output =
"""
circuit name_of_sram_module :
  module name_of_sram_module :
    input clock : Clock
    input RW0A : UInt<11>
    input RW0I : UInt<16>
    output RW0O : UInt<16>
    input RW0E : UInt<1>
    input RW0W : UInt<1>
    input RW0M : UInt<2>

    inst mem_0_0 of vendor_sram
    inst mem_0_1 of vendor_sram
    mem_0_0.clock <= clock
    mem_0_0.RW0A <= RW0A
    node RW0O_0_0 = bits(mem_0_0.RW0O, 7, 0)
    mem_0_0.RW0I <= bits(RW0I, 7, 0)
    mem_0_0.RW0W <= and(and(RW0W, bits(RW0M, 0, 0)), UInt<1>("h1"))
    mem_0_0.RW0E <= and(RW0E, UInt<1>("h1"))
    mem_0_1.clock <= clock
    mem_0_1.RW0A <= RW0A
    node RW0O_0_1 = bits(mem_0_1.RW0O, 7, 0)
    mem_0_1.RW0I <= bits(RW0I, 15, 8)
    mem_0_1.RW0W <= and(and(RW0W, bits(RW0M, 1, 1)), UInt<1>("h1"))
    mem_0_1.RW0E <= and(RW0E, UInt<1>("h1"))
    node RW0O_0 = cat(RW0O_0_1, RW0O_0_0)
    RW0O <= mux(UInt<1>("h1"), RW0O_0, UInt<1>("h0"))

  extmodule vendor_sram :
    input clock : Clock
    input RW0A : UInt<11>
    input RW0I : UInt<10>
    output RW0O : UInt<10>
    input RW0E : UInt<1>
    input RW0W : UInt<1>

    defname = vendor_sram
"""
  compile(mem, Some(lib), v, false)
  execute(Some(mem), Some(lib), false, output)
}

class SplitWidth2048x16_mrw_VeryUneven extends MacroCompilerSpec {
  val mem = new File(macroDir, "mem-2048x16-mrw-2.json")
  val lib = new File(macroDir, "lib-2048x10-rw.json")
  val v = new File(testDir, "split_width_2048x16_mrw_very_uneven.v")
  val output =
"""
circuit name_of_sram_module :
  module name_of_sram_module :
    input clock : Clock
    input RW0A : UInt<11>
    input RW0I : UInt<16>
    output RW0O : UInt<16>
    input RW0E : UInt<1>
    input RW0W : UInt<1>
    input RW0M : UInt<8>

    inst mem_0_0 of vendor_sram
    inst mem_0_1 of vendor_sram
    inst mem_0_2 of vendor_sram
    inst mem_0_3 of vendor_sram
    inst mem_0_4 of vendor_sram
    inst mem_0_5 of vendor_sram
    inst mem_0_6 of vendor_sram
    inst mem_0_7 of vendor_sram
    mem_0_0.clock <= clock
    mem_0_0.RW0A <= RW0A
    node RW0O_0_0 = bits(mem_0_0.RW0O, 1, 0)
    mem_0_0.RW0I <= bits(RW0I, 1, 0)
    mem_0_0.RW0W <= and(and(RW0W, bits(RW0M, 0, 0)), UInt<1>("h1"))
    mem_0_0.RW0E <= and(RW0E, UInt<1>("h1"))
    mem_0_1.clock <= clock
    mem_0_1.RW0A <= RW0A
    node RW0O_0_1 = bits(mem_0_1.RW0O, 1, 0)
    mem_0_1.RW0I <= bits(RW0I, 3, 2)
    mem_0_1.RW0W <= and(and(RW0W, bits(RW0M, 1, 1)), UInt<1>("h1"))
    mem_0_1.RW0E <= and(RW0E, UInt<1>("h1"))
    mem_0_2.clock <= clock
    mem_0_2.RW0A <= RW0A
    node RW0O_0_2 = bits(mem_0_2.RW0O, 1, 0)
    mem_0_2.RW0I <= bits(RW0I, 5, 4)
    mem_0_2.RW0W <= and(and(RW0W, bits(RW0M, 2, 2)), UInt<1>("h1"))
    mem_0_2.RW0E <= and(RW0E, UInt<1>("h1"))
    mem_0_3.clock <= clock
    mem_0_3.RW0A <= RW0A
    node RW0O_0_3 = bits(mem_0_3.RW0O, 1, 0)
    mem_0_3.RW0I <= bits(RW0I, 7, 6)
    mem_0_3.RW0W <= and(and(RW0W, bits(RW0M, 3, 3)), UInt<1>("h1"))
    mem_0_3.RW0E <= and(RW0E, UInt<1>("h1"))
    mem_0_4.clock <= clock
    mem_0_4.RW0A <= RW0A
    node RW0O_0_4 = bits(mem_0_4.RW0O, 1, 0)
    mem_0_4.RW0I <= bits(RW0I, 9, 8)
    mem_0_4.RW0W <= and(and(RW0W, bits(RW0M, 4, 4)), UInt<1>("h1"))
    mem_0_4.RW0E <= and(RW0E, UInt<1>("h1"))
    mem_0_5.clock <= clock
    mem_0_5.RW0A <= RW0A
    node RW0O_0_5 = bits(mem_0_5.RW0O, 1, 0)
    mem_0_5.RW0I <= bits(RW0I, 11, 10)
    mem_0_5.RW0W <= and(and(RW0W, bits(RW0M, 5, 5)), UInt<1>("h1"))
    mem_0_5.RW0E <= and(RW0E, UInt<1>("h1"))
    mem_0_6.clock <= clock
    mem_0_6.RW0A <= RW0A
    node RW0O_0_6 = bits(mem_0_6.RW0O, 1, 0)
    mem_0_6.RW0I <= bits(RW0I, 13, 12)
    mem_0_6.RW0W <= and(and(RW0W, bits(RW0M, 6, 6)), UInt<1>("h1"))
    mem_0_6.RW0E <= and(RW0E, UInt<1>("h1"))
    mem_0_7.clock <= clock
    mem_0_7.RW0A <= RW0A
    node RW0O_0_7 = bits(mem_0_7.RW0O, 1, 0)
    mem_0_7.RW0I <= bits(RW0I, 15, 14)
    mem_0_7.RW0W <= and(and(RW0W, bits(RW0M, 7, 7)), UInt<1>("h1"))
    mem_0_7.RW0E <= and(RW0E, UInt<1>("h1"))
    node RW0O_0 = cat(RW0O_0_7, cat(RW0O_0_6, cat(RW0O_0_5, cat(RW0O_0_4, cat(RW0O_0_3, cat(RW0O_0_2, cat(RW0O_0_1, RW0O_0_0)))))))
    RW0O <= mux(UInt<1>("h1"), RW0O_0, UInt<1>("h0"))

  extmodule vendor_sram :
    input clock : Clock
    input RW0A : UInt<11>
    input RW0I : UInt<10>
    output RW0O : UInt<10>
    input RW0E : UInt<1>
    input RW0W : UInt<1>

    defname = vendor_sram
"""
  compile(mem, Some(lib), v, false)
  execute(Some(mem), Some(lib), false, output)
}

class SplitWidth2048x16_mrw_ReadEnable extends MacroCompilerSpec {
  val mem = new File(macroDir, "mem-2048x16-mrw.json")
  val lib = new File(macroDir, "lib-2048x8-mrw-re.json")
  val v = new File(testDir, "split_width_2048x16_mrw_read_enable.v")
  val output =
"""
circuit name_of_sram_module :
  module name_of_sram_module :
    input clock : Clock
    input RW0A : UInt<11>
    input RW0I : UInt<16>
    output RW0O : UInt<16>
    input RW0E : UInt<1>
    input RW0W : UInt<1>
    input RW0M : UInt<2>

    inst mem_0_0 of vendor_sram
    inst mem_0_1 of vendor_sram
    mem_0_0.clock <= clock
    mem_0_0.RW0A <= RW0A
    node RW0O_0_0 = bits(mem_0_0.RW0O, 7, 0)
    mem_0_0.RW0I <= bits(RW0I, 7, 0)
    mem_0_0.RW0R <= not(and(not(RW0W), UInt<1>("h1")))
    mem_0_0.RW0M <= bits(RW0M, 0, 0)
    mem_0_0.RW0W <= and(RW0W, UInt<1>("h1"))
    mem_0_0.RW0E <= and(RW0E, UInt<1>("h1"))
    mem_0_1.clock <= clock
    mem_0_1.RW0A <= RW0A
    node RW0O_0_1 = bits(mem_0_1.RW0O, 7, 0)
    mem_0_1.RW0I <= bits(RW0I, 15, 8)
    mem_0_1.RW0R <= not(and(not(RW0W), UInt<1>("h1")))
    mem_0_1.RW0M <= bits(RW0M, 1, 1)
    mem_0_1.RW0W <= and(RW0W, UInt<1>("h1"))
    mem_0_1.RW0E <= and(RW0E, UInt<1>("h1"))
    node RW0O_0 = cat(RW0O_0_1, RW0O_0_0)
    RW0O <= mux(UInt<1>("h1"), RW0O_0, UInt<1>("h0"))

  extmodule vendor_sram :
    input clock : Clock
    input RW0A : UInt<11>
    input RW0I : UInt<8>
    output RW0O : UInt<8>
    input RW0E : UInt<1>
    input RW0R : UInt<1>
    input RW0W : UInt<1>
    input RW0M : UInt<1>

    defname = vendor_sram
"""
  compile(mem, Some(lib), v, false)
  execute(Some(mem), Some(lib), false, output)
}

class SplitWidth2048x16_n28 extends MacroCompilerSpec {
  val mem = new File(macroDir, "mem-2048x16-mrw.json")
  val lib = new File(macroDir, "lib-2048x16-n28.json")
  val v = new File(testDir, "split_width_2048x16_n28.v")
  val output =
"""
circuit name_of_sram_module :
  module name_of_sram_module :
    input clock : Clock
    input RW0A : UInt<11>
    input RW0I : UInt<16>
    output RW0O : UInt<16>
    input RW0E : UInt<1>
    input RW0W : UInt<1>
    input RW0M : UInt<2>

    inst mem_0_0 of vendor_sram_16
    mem_0_0.clock <= clock
    mem_0_0.RW0A <= RW0A
    node RW0O_0_0 = bits(mem_0_0.RW0O, 15, 0)
    mem_0_0.RW0I <= bits(RW0I, 15, 0)
    mem_0_0.RW0M <= cat(bits(RW0M, 1, 1), cat(bits(RW0M, 1, 1), cat(bits(RW0M, 1, 1), cat(bits(RW0M, 1, 1), cat(bits(RW0M, 1, 1), cat(bits(RW0M, 1, 1), cat(bits(RW0M, 1, 1), cat(bits(RW0M, 1, 1), cat(bits(RW0M, 0, 0), cat(bits(RW0M, 0, 0), cat(bits(RW0M, 0, 0), cat(bits(RW0M, 0, 0), cat(bits(RW0M, 0, 0), cat(bits(RW0M, 0, 0), cat(bits(RW0M, 0, 0), bits(RW0M, 0, 0))))))))))))))))
    mem_0_0.RW0W <= and(RW0W, UInt<1>("h1"))
    mem_0_0.RW0E <= and(RW0E, UInt<1>("h1"))
    node RW0O_0 = RW0O_0_0
    RW0O <= mux(UInt<1>("h1"), RW0O_0, UInt<1>("h0"))

  extmodule vendor_sram_16 :
    input clock : Clock
    input RW0A : UInt<11>
    input RW0I : UInt<16>
    output RW0O : UInt<16>
    input RW0E : UInt<1>
    input RW0W : UInt<1>
    input RW0M : UInt<16>

    defname = vendor_sram_16
"""
  compile(mem, Some(lib), v, false)
  execute(Some(mem), Some(lib), false, output)
}

class SplitWidth2048x20_mrw_UnevenMask extends MacroCompilerSpec {
  val mem = new File(macroDir, "mem-2048x20-mrw.json")
  val lib = new File(macroDir, "lib-2048x8-mrw.json")
  val v = new File(testDir, "split_width_2048x20_mrw_uneven_mask.v")
  val output =
"""
circuit name_of_sram_module :
  module name_of_sram_module :
    input clock : Clock
    input RW0A : UInt<11>
    input RW0I : UInt<20>
    output RW0O : UInt<20>
    input RW0E : UInt<1>
    input RW0W : UInt<1>
    input RW0M : UInt<2>

    inst mem_0_0 of vendor_sram
    inst mem_0_1 of vendor_sram
    inst mem_0_2 of vendor_sram
    inst mem_0_3 of vendor_sram
    mem_0_0.clock <= clock
    mem_0_0.RW0A <= RW0A
    node RW0O_0_0 = bits(mem_0_0.RW0O, 7, 0)
    mem_0_0.RW0I <= bits(RW0I, 7, 0)
    mem_0_0.RW0M <= bits(RW0M, 0, 0)
    mem_0_0.RW0W <= and(RW0W, UInt<1>("h1"))
    mem_0_0.RW0E <= and(RW0E, UInt<1>("h1"))
    mem_0_1.clock <= clock
    mem_0_1.RW0A <= RW0A
    node RW0O_0_1 = bits(mem_0_1.RW0O, 1, 0)
    mem_0_1.RW0I <= bits(RW0I, 9, 8)
    mem_0_1.RW0M <= bits(RW0M, 0, 0)
    mem_0_1.RW0W <= and(RW0W, UInt<1>("h1"))
    mem_0_1.RW0E <= and(RW0E, UInt<1>("h1"))
    mem_0_2.clock <= clock
    mem_0_2.RW0A <= RW0A
    node RW0O_0_2 = bits(mem_0_2.RW0O, 7, 0)
    mem_0_2.RW0I <= bits(RW0I, 17, 10)
    mem_0_2.RW0M <= bits(RW0M, 1, 1)
    mem_0_2.RW0W <= and(RW0W, UInt<1>("h1"))
    mem_0_2.RW0E <= and(RW0E, UInt<1>("h1"))
    mem_0_3.clock <= clock
    mem_0_3.RW0A <= RW0A
    node RW0O_0_3 = bits(mem_0_3.RW0O, 1, 0)
    mem_0_3.RW0I <= bits(RW0I, 19, 18)
    mem_0_3.RW0M <= bits(RW0M, 1, 1)
    mem_0_3.RW0W <= and(RW0W, UInt<1>("h1"))
    mem_0_3.RW0E <= and(RW0E, UInt<1>("h1"))
    node RW0O_0 = cat(RW0O_0_3, cat(RW0O_0_2, cat(RW0O_0_1, RW0O_0_0)))
    RW0O <= mux(UInt<1>("h1"), RW0O_0, UInt<1>("h0"))

  extmodule vendor_sram :
    input clock : Clock
    input RW0A : UInt<11>
    input RW0I : UInt<8>
    output RW0O : UInt<8>
    input RW0E : UInt<1>
    input RW0W : UInt<1>
    input RW0M : UInt<1>

    defname = vendor_sram
"""
  compile(mem, Some(lib), v, false)
  execute(Some(mem), Some(lib), false, output)
}

class SplitWidth24x52 extends MacroCompilerSpec {
  val mem = new File(macroDir, "mem-24x52-r-w.json")
  val lib = new File(macroDir, "lib-32x32-2rw.json")
  val v = new File(testDir, "split_width_24x52.v")
  val output =
"""
circuit entries_info_ext :
  module entries_info_ext :
    input R0_clk : Clock
    input R0_addr : UInt<5>
    output R0_data : UInt<52>
    input R0_en : UInt<1>
    input W0_clk : Clock
    input W0_addr : UInt<5>
    input W0_data : UInt<52>
    input W0_en : UInt<1>

    inst mem_0_0 of SRAM2RW32x32
    inst mem_0_1 of SRAM2RW32x32
    mem_0_0.CE1 <= W0_clk
    mem_0_0.A1 <= W0_addr
    mem_0_0.I1 <= bits(W0_data, 31, 0)
    mem_0_0.OEB1 <= not(and(not(UInt<1>("h1")), UInt<1>("h1")))
    mem_0_0.WEB1 <= not(and(and(UInt<1>("h1"), UInt<1>("h1")), UInt<1>("h1")))
    mem_0_0.CSB1 <= not(and(W0_en, UInt<1>("h1")))
    mem_0_1.CE1 <= W0_clk
    mem_0_1.A1 <= W0_addr
    mem_0_1.I1 <= bits(W0_data, 51, 32)
    mem_0_1.OEB1 <= not(and(not(UInt<1>("h1")), UInt<1>("h1")))
    mem_0_1.WEB1 <= not(and(and(UInt<1>("h1"), UInt<1>("h1")), UInt<1>("h1")))
    mem_0_1.CSB1 <= not(and(W0_en, UInt<1>("h1")))
    mem_0_0.CE2 <= R0_clk
    mem_0_0.A2 <= R0_addr
    node R0_data_0_0 = bits(mem_0_0.O2, 31, 0)
    mem_0_0.OEB2 <= not(and(not(UInt<1>("h0")), UInt<1>("h1")))
    mem_0_0.WEB2 <= not(and(and(UInt<1>("h0"), UInt<1>("h1")), UInt<1>("h1")))
    mem_0_0.CSB2 <= not(and(R0_en, UInt<1>("h1")))
    mem_0_1.CE2 <= R0_clk
    mem_0_1.A2 <= R0_addr
    node R0_data_0_1 = bits(mem_0_1.O2, 19, 0)
    mem_0_1.OEB2 <= not(and(not(UInt<1>("h0")), UInt<1>("h1")))
    mem_0_1.WEB2 <= not(and(and(UInt<1>("h0"), UInt<1>("h1")), UInt<1>("h1")))
    mem_0_1.CSB2 <= not(and(R0_en, UInt<1>("h1")))
    node R0_data_0 = cat(R0_data_0_1, R0_data_0_0)
    R0_data <= mux(UInt<1>("h1"), R0_data_0, UInt<1>("h0"))

  extmodule SRAM2RW32x32 :
    input CE1 : Clock
    input A1 : UInt<5>
    input I1 : UInt<32>
    output O1 : UInt<32>
    input CSB1 : UInt<1>
    input OEB1 : UInt<1>
    input WEB1 : UInt<1>
    input CE2 : Clock
    input A2 : UInt<5>
    input I2 : UInt<32>
    output O2 : UInt<32>
    input CSB2 : UInt<1>
    input OEB2 : UInt<1>
    input WEB2 : UInt<1>

    defname = SRAM2RW32x32
"""
  compile(mem, Some(lib), v, false)
  execute(Some(mem), Some(lib), false, output)
}

class SplitWidth32x160 extends MacroCompilerSpec {
  val mem = new File(macroDir, "mem-32x160-mrw.json")
  val lib = new File(macroDir, "lib-32x80-mrw.json")
  val v = new File(testDir, "split_width_32x160.v")
  val output =
"""
circuit name_of_sram_module :
  module name_of_sram_module :
    input clock : Clock
    input RW0A : UInt<5>
    input RW0I : UInt<160>
    output RW0O : UInt<160>
    input RW0E : UInt<1>
    input RW0W : UInt<1>
    input RW0M : UInt<8>

    inst mem_0_0 of vendor_sram
    inst mem_0_1 of vendor_sram
    mem_0_0.clock <= clock
    mem_0_0.RW0A <= RW0A
    node RW0O_0_0 = bits(mem_0_0.RW0O, 79, 0)
    mem_0_0.RW0I <= bits(RW0I, 79, 0)
    mem_0_0.RW0M <= cat(bits(RW0M, 3, 3), cat(bits(RW0M, 3, 3), cat(bits(RW0M, 3, 3), cat(bits(RW0M, 3, 3), cat(bits(RW0M, 3, 3), cat(bits(RW0M, 3, 3), cat(bits(RW0M, 3, 3), cat(bits(RW0M, 3, 3), cat(bits(RW0M, 3, 3), cat(bits(RW0M, 3, 3), cat(bits(RW0M, 3, 3), cat(bits(RW0M, 3, 3), cat(bits(RW0M, 3, 3), cat(bits(RW0M, 3, 3), cat(bits(RW0M, 3, 3), cat(bits(RW0M, 3, 3), cat(bits(RW0M, 3, 3), cat(bits(RW0M, 3, 3), cat(bits(RW0M, 3, 3), cat(bits(RW0M, 3, 3), cat(bits(RW0M, 2, 2), cat(bits(RW0M, 2, 2), cat(bits(RW0M, 2, 2), cat(bits(RW0M, 2, 2), cat(bits(RW0M, 2, 2), cat(bits(RW0M, 2, 2), cat(bits(RW0M, 2, 2), cat(bits(RW0M, 2, 2), cat(bits(RW0M, 2, 2), cat(bits(RW0M, 2, 2), cat(bits(RW0M, 2, 2), cat(bits(RW0M, 2, 2), cat(bits(RW0M, 2, 2), cat(bits(RW0M, 2, 2), cat(bits(RW0M, 2, 2), cat(bits(RW0M, 2, 2), cat(bits(RW0M, 2, 2), cat(bits(RW0M, 2, 2), cat(bits(RW0M, 2, 2), cat(bits(RW0M, 2, 2), cat(bits(RW0M, 1, 1), cat(bits(RW0M, 1, 1), cat(bits(RW0M, 1, 1), cat(bits(RW0M, 1, 1), cat(bits(RW0M, 1, 1), cat(bits(RW0M, 1, 1), cat(bits(RW0M, 1, 1), cat(bits(RW0M, 1, 1), cat(bits(RW0M, 1, 1), cat(bits(RW0M, 1, 1), cat(bits(RW0M, 1, 1), cat(bits(RW0M, 1, 1), cat(bits(RW0M, 1, 1), cat(bits(RW0M, 1, 1), cat(bits(RW0M, 1, 1), cat(bits(RW0M, 1, 1), cat(bits(RW0M, 1, 1), cat(bits(RW0M, 1, 1), cat(bits(RW0M, 1, 1), cat(bits(RW0M, 1, 1), cat(bits(RW0M, 0, 0), cat(bits(RW0M, 0, 0), cat(bits(RW0M, 0, 0), cat(bits(RW0M, 0, 0), cat(bits(RW0M, 0, 0), cat(bits(RW0M, 0, 0), cat(bits(RW0M, 0, 0), cat(bits(RW0M, 0, 0), cat(bits(RW0M, 0, 0), cat(bits(RW0M, 0, 0), cat(bits(RW0M, 0, 0), cat(bits(RW0M, 0, 0), cat(bits(RW0M, 0, 0), cat(bits(RW0M, 0, 0), cat(bits(RW0M, 0, 0), cat(bits(RW0M, 0, 0), cat(bits(RW0M, 0, 0), cat(bits(RW0M, 0, 0), cat(bits(RW0M, 0, 0), bits(RW0M, 0, 0))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))
    mem_0_0.RW0W <= and(RW0W, UInt<1>("h1"))
    mem_0_0.RW0E <= and(RW0E, UInt<1>("h1"))
    mem_0_1.clock <= clock
    mem_0_1.RW0A <= RW0A
    node RW0O_0_1 = bits(mem_0_1.RW0O, 79, 0)
    mem_0_1.RW0I <= bits(RW0I, 159, 80)
    mem_0_1.RW0M <= cat(bits(RW0M, 7, 7), cat(bits(RW0M, 7, 7), cat(bits(RW0M, 7, 7), cat(bits(RW0M, 7, 7), cat(bits(RW0M, 7, 7), cat(bits(RW0M, 7, 7), cat(bits(RW0M, 7, 7), cat(bits(RW0M, 7, 7), cat(bits(RW0M, 7, 7), cat(bits(RW0M, 7, 7), cat(bits(RW0M, 7, 7), cat(bits(RW0M, 7, 7), cat(bits(RW0M, 7, 7), cat(bits(RW0M, 7, 7), cat(bits(RW0M, 7, 7), cat(bits(RW0M, 7, 7), cat(bits(RW0M, 7, 7), cat(bits(RW0M, 7, 7), cat(bits(RW0M, 7, 7), cat(bits(RW0M, 7, 7), cat(bits(RW0M, 6, 6), cat(bits(RW0M, 6, 6), cat(bits(RW0M, 6, 6), cat(bits(RW0M, 6, 6), cat(bits(RW0M, 6, 6), cat(bits(RW0M, 6, 6), cat(bits(RW0M, 6, 6), cat(bits(RW0M, 6, 6), cat(bits(RW0M, 6, 6), cat(bits(RW0M, 6, 6), cat(bits(RW0M, 6, 6), cat(bits(RW0M, 6, 6), cat(bits(RW0M, 6, 6), cat(bits(RW0M, 6, 6), cat(bits(RW0M, 6, 6), cat(bits(RW0M, 6, 6), cat(bits(RW0M, 6, 6), cat(bits(RW0M, 6, 6), cat(bits(RW0M, 6, 6), cat(bits(RW0M, 6, 6), cat(bits(RW0M, 5, 5), cat(bits(RW0M, 5, 5), cat(bits(RW0M, 5, 5), cat(bits(RW0M, 5, 5), cat(bits(RW0M, 5, 5), cat(bits(RW0M, 5, 5), cat(bits(RW0M, 5, 5), cat(bits(RW0M, 5, 5), cat(bits(RW0M, 5, 5), cat(bits(RW0M, 5, 5), cat(bits(RW0M, 5, 5), cat(bits(RW0M, 5, 5), cat(bits(RW0M, 5, 5), cat(bits(RW0M, 5, 5), cat(bits(RW0M, 5, 5), cat(bits(RW0M, 5, 5), cat(bits(RW0M, 5, 5), cat(bits(RW0M, 5, 5), cat(bits(RW0M, 5, 5), cat(bits(RW0M, 5, 5), cat(bits(RW0M, 4, 4), cat(bits(RW0M, 4, 4), cat(bits(RW0M, 4, 4), cat(bits(RW0M, 4, 4), cat(bits(RW0M, 4, 4), cat(bits(RW0M, 4, 4), cat(bits(RW0M, 4, 4), cat(bits(RW0M, 4, 4), cat(bits(RW0M, 4, 4), cat(bits(RW0M, 4, 4), cat(bits(RW0M, 4, 4), cat(bits(RW0M, 4, 4), cat(bits(RW0M, 4, 4), cat(bits(RW0M, 4, 4), cat(bits(RW0M, 4, 4), cat(bits(RW0M, 4, 4), cat(bits(RW0M, 4, 4), cat(bits(RW0M, 4, 4), cat(bits(RW0M, 4, 4), bits(RW0M, 4, 4))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))
    mem_0_1.RW0W <= and(RW0W, UInt<1>("h1"))
    mem_0_1.RW0E <= and(RW0E, UInt<1>("h1"))
    node RW0O_0 = cat(RW0O_0_1, RW0O_0_0)
    RW0O <= mux(UInt<1>("h1"), RW0O_0, UInt<1>("h0"))

  extmodule vendor_sram :
    input clock : Clock
    input RW0A : UInt<5>
    input RW0I : UInt<80>
    output RW0O : UInt<80>
    input RW0E : UInt<1>
    input RW0W : UInt<1>
    input RW0M : UInt<80>

    defname = vendor_sram 
"""
  compile(mem, Some(lib), v, false)
  execute(Some(mem), Some(lib), false, output)
}
