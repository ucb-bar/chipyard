package barstools.tapeout.transforms.macros

import java.io.File

class SplitDepth2048x8_mrw extends MacroCompilerSpec {
  val mem = new File(macroDir, "mem-2048x8-mrw.json")
  val lib = new File(macroDir, "lib-1024x8-mrw.json")
  val v = new File(testDir, "split_depth_2048x8_mrw.v")
  val output =
"""
circuit name_of_sram_module :
  module name_of_sram_module :
    input clock : Clock
    input RW0A : UInt<11>
    input RW0I : UInt<8>
    output RW0O : UInt<8>
    input RW0E : UInt<1>
    input RW0W : UInt<1>
    input RW0M : UInt<1>

    node RW0A_sel = bits(RW0A, 10, 10)
    inst mem_0_0 of vendor_sram
    mem_0_0.clock <= clock
    mem_0_0.RW0A <= RW0A
    node RW0O_0_0 = bits(mem_0_0.RW0O, 7, 0)
    mem_0_0.RW0I <= bits(RW0I, 7, 0)
    mem_0_0.RW0M <= bits(RW0M, 0, 0)
    mem_0_0.RW0W <= and(RW0W, eq(RW0A_sel, UInt<1>("h0")))
    mem_0_0.RW0E <= and(RW0E, eq(RW0A_sel, UInt<1>("h0")))
    node RW0O_0 = RW0O_0_0
    inst mem_1_0 of vendor_sram
    mem_1_0.clock <= clock
    mem_1_0.RW0A <= RW0A
    node RW0O_1_0 = bits(mem_1_0.RW0O, 7, 0)
    mem_1_0.RW0I <= bits(RW0I, 7, 0)
    mem_1_0.RW0M <= bits(RW0M, 0, 0)
    mem_1_0.RW0W <= and(RW0W, eq(RW0A_sel, UInt<1>("h1")))
    mem_1_0.RW0E <= and(RW0E, eq(RW0A_sel, UInt<1>("h1")))
    node RW0O_1 = RW0O_1_0
    RW0O <= mux(eq(RW0A_sel, UInt<1>("h0")), RW0O_0, mux(eq(RW0A_sel, UInt<1>("h1")), RW0O_1, UInt<1>("h0")))

  extmodule vendor_sram :
    input clock : Clock
    input RW0A : UInt<10>
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

class SplitDepth2000x8_mrw extends MacroCompilerSpec {
  val mem = new File(macroDir, "mem-2000x8-mrw.json")
  val lib = new File(macroDir, "lib-1024x8-mrw.json")
  val v = new File(testDir, "split_depth_2000x8_mrw.v")
  val output =
"""
circuit name_of_sram_module :
  module name_of_sram_module :
    input clock : Clock
    input RW0A : UInt<11>
    input RW0I : UInt<8>
    output RW0O : UInt<8>
    input RW0E : UInt<1>
    input RW0W : UInt<1>
    input RW0M : UInt<1>

    node RW0A_sel = bits(RW0A, 10, 10)
    inst mem_0_0 of vendor_sram
    mem_0_0.clock <= clock
    mem_0_0.RW0A <= RW0A
    node RW0O_0_0 = bits(mem_0_0.RW0O, 7, 0)
    mem_0_0.RW0I <= bits(RW0I, 7, 0)
    mem_0_0.RW0M <= bits(RW0M, 0, 0)
    mem_0_0.RW0W <= and(RW0W, eq(RW0A_sel, UInt<1>("h0")))
    mem_0_0.RW0E <= and(RW0E, eq(RW0A_sel, UInt<1>("h0")))
    node RW0O_0 = RW0O_0_0
    inst mem_1_0 of vendor_sram
    mem_1_0.clock <= clock
    mem_1_0.RW0A <= RW0A
    node RW0O_1_0 = bits(mem_1_0.RW0O, 7, 0)
    mem_1_0.RW0I <= bits(RW0I, 7, 0)
    mem_1_0.RW0M <= bits(RW0M, 0, 0)
    mem_1_0.RW0W <= and(RW0W, eq(RW0A_sel, UInt<1>("h1")))
    mem_1_0.RW0E <= and(RW0E, eq(RW0A_sel, UInt<1>("h1")))
    node RW0O_1 = RW0O_1_0
    RW0O <= mux(eq(RW0A_sel, UInt<1>("h0")), RW0O_0, mux(eq(RW0A_sel, UInt<1>("h1")), RW0O_1, UInt<1>("h0")))

  extmodule vendor_sram :
    input clock : Clock
    input RW0A : UInt<10>
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

class SplitDepth2048x8_n28 extends MacroCompilerSpec {
  val mem = new File(macroDir, "mem-2048x8-mrw.json")
  val lib = new File(macroDir, "lib-1024x8-n28.json")
  val v = new File(testDir, "split_depth_2048x8_n28.v")
  val output =
"""
circuit name_of_sram_module :
  module name_of_sram_module :
    input clock : Clock
    input RW0A : UInt<11>
    input RW0I : UInt<8>
    output RW0O : UInt<8>
    input RW0E : UInt<1>
    input RW0W : UInt<1>
    input RW0M : UInt<1>

    node RW0A_sel = bits(RW0A, 10, 10)
    inst mem_0_0 of vendor_sram
    mem_0_0.clock <= clock
    mem_0_0.RW0A <= RW0A
    node RW0O_0_0 = bits(mem_0_0.RW0O, 7, 0)
    mem_0_0.RW0I <= bits(RW0I, 7, 0)
    mem_0_0.RW0M <= cat(bits(RW0M, 0, 0), cat(bits(RW0M, 0, 0), cat(bits(RW0M, 0, 0), cat(bits(RW0M, 0, 0), cat(bits(RW0M, 0, 0), cat(bits(RW0M, 0, 0), cat(bits(RW0M, 0, 0), bits(RW0M, 0, 0))))))))
    mem_0_0.RW0W <= and(RW0W, eq(RW0A_sel, UInt<1>("h0")))
    mem_0_0.RW0E <= and(RW0E, eq(RW0A_sel, UInt<1>("h0")))
    node RW0O_0 = RW0O_0_0
    inst mem_1_0 of vendor_sram
    mem_1_0.clock <= clock
    mem_1_0.RW0A <= RW0A
    node RW0O_1_0 = bits(mem_1_0.RW0O, 7, 0)
    mem_1_0.RW0I <= bits(RW0I, 7, 0)
    mem_1_0.RW0M <= cat(bits(RW0M, 0, 0), cat(bits(RW0M, 0, 0), cat(bits(RW0M, 0, 0), cat(bits(RW0M, 0, 0), cat(bits(RW0M, 0, 0), cat(bits(RW0M, 0, 0), cat(bits(RW0M, 0, 0), bits(RW0M, 0, 0))))))))
    mem_1_0.RW0W <= and(RW0W, eq(RW0A_sel, UInt<1>("h1")))
    mem_1_0.RW0E <= and(RW0E, eq(RW0A_sel, UInt<1>("h1")))
    node RW0O_1 = RW0O_1_0
    RW0O <= mux(eq(RW0A_sel, UInt<1>("h0")), RW0O_0, mux(eq(RW0A_sel, UInt<1>("h1")), RW0O_1, UInt<1>("h0")))

  extmodule vendor_sram :
    input clock : Clock
    input RW0A : UInt<10>
    input RW0I : UInt<8>
    output RW0O : UInt<8>
    input RW0E : UInt<1>
    input RW0W : UInt<1>
    input RW0M : UInt<8>

    defname = vendor_sram
"""
  compile(mem, Some(lib), v, false)
  execute(Some(mem), Some(lib), false, output)
}

class SplitDepth2048x8_r_mw extends MacroCompilerSpec {
  val mem = new File(macroDir, "mem-2048x8-r-mw.json")
  val lib = new File(macroDir, "lib-1024x8-r-mw.json")
  val v = new File(testDir, "split_depth_2048x8_r_mw.v")
  val output =
"""
circuit name_of_sram_module :
  module name_of_sram_module :
    input clock : Clock
    input W0A : UInt<11>
    input W0I : UInt<8>
    input W0E : UInt<1>
    input W0M : UInt<1>
    input clock : Clock
    input R0A : UInt<11>
    output R0O : UInt<8>

    node W0A_sel = bits(W0A, 10, 10)
    node R0A_sel = bits(R0A, 10, 10)
    inst mem_0_0 of vendor_sram
    mem_0_0.clock <= clock
    mem_0_0.W0A <= W0A
    mem_0_0.W0I <= bits(W0I, 7, 0)
    mem_0_0.W0M <= bits(W0M, 0, 0)
    mem_0_0.W0W <= and(UInt<1>("h1"), eq(W0A_sel, UInt<1>("h0")))
    mem_0_0.W0E <= and(W0E, eq(W0A_sel, UInt<1>("h0")))
    mem_0_0.clock <= clock
    mem_0_0.R0A <= R0A
    node R0O_0_0 = bits(mem_0_0.R0O, 7, 0)
    node R0O_0 = R0O_0_0
    inst mem_1_0 of vendor_sram
    mem_1_0.clock <= clock
    mem_1_0.W0A <= W0A
    mem_1_0.W0I <= bits(W0I, 7, 0)
    mem_1_0.W0M <= bits(W0M, 0, 0)
    mem_1_0.W0W <= and(UInt<1>("h1"), eq(W0A_sel, UInt<1>("h1")))
    mem_1_0.W0E <= and(W0E, eq(W0A_sel, UInt<1>("h1")))
    mem_1_0.clock <= clock
    mem_1_0.R0A <= R0A
    node R0O_1_0 = bits(mem_1_0.R0O, 7, 0)
    node R0O_1 = R0O_1_0
    R0O <= mux(eq(R0A_sel, UInt<1>("h0")), R0O_0, mux(eq(R0A_sel, UInt<1>("h1")), R0O_1, UInt<1>("h0")))

  extmodule vendor_sram :
    input clock : Clock
    input R0A : UInt<10>
    output R0O : UInt<8>
    input clock : Clock
    input W0A : UInt<10>
    input W0I : UInt<8>
    input W0E : UInt<1>
    input W0W : UInt<1>
    input W0M : UInt<1>

    defname = vendor_sram
"""
  compile(mem, Some(lib), v, false)
  execute(Some(mem), Some(lib), false, output)
}


class SplitDepth2048x8_mrw_Sleep extends MacroCompilerSpec {
  val mem = new File(macroDir, "mem-2048x8-mrw.json")
  val lib = new File(macroDir, "lib-1024x8-sleep.json")
  val v = new File(testDir, "split_depth_2048x8_sleep.v")
  val output =
"""
circuit name_of_sram_module :
  module name_of_sram_module :
    input clock : Clock
    input RW0A : UInt<11>
    input RW0I : UInt<8>
    output RW0O : UInt<8>
    input RW0E : UInt<1>
    input RW0W : UInt<1>
    input RW0M : UInt<1>

    node RW0A_sel = bits(RW0A, 10, 10)
    inst mem_0_0 of vendor_sram
    mem_0_0.sleep <= UInt<1>("h0")
    mem_0_0.clock <= clock
    mem_0_0.RW0A <= RW0A
    node RW0O_0_0 = bits(mem_0_0.RW0O, 7, 0)
    mem_0_0.RW0I <= bits(RW0I, 7, 0)
    mem_0_0.RW0M <= bits(RW0M, 0, 0)
    mem_0_0.RW0W <= and(RW0W, eq(RW0A_sel, UInt<1>("h0")))
    mem_0_0.RW0E <= and(RW0E, eq(RW0A_sel, UInt<1>("h0")))
    node RW0O_0 = RW0O_0_0
    inst mem_1_0 of vendor_sram
    mem_1_0.sleep <= UInt<1>("h0")
    mem_1_0.clock <= clock
    mem_1_0.RW0A <= RW0A
    node RW0O_1_0 = bits(mem_1_0.RW0O, 7, 0)
    mem_1_0.RW0I <= bits(RW0I, 7, 0)
    mem_1_0.RW0M <= bits(RW0M, 0, 0)
    mem_1_0.RW0W <= and(RW0W, eq(RW0A_sel, UInt<1>("h1")))
    mem_1_0.RW0E <= and(RW0E, eq(RW0A_sel, UInt<1>("h1")))
    node RW0O_1 = RW0O_1_0
    RW0O <= mux(eq(RW0A_sel, UInt<1>("h0")), RW0O_0, mux(eq(RW0A_sel, UInt<1>("h1")), RW0O_1, UInt<1>("h0")))

  extmodule vendor_sram :
    input clock : Clock
    input RW0A : UInt<10>
    input RW0I : UInt<8>
    output RW0O : UInt<8>
    input RW0E : UInt<1>
    input RW0W : UInt<1>
    input RW0M : UInt<1>
    input sleep : UInt<1>

    defname = vendor_sram  
"""
  compile(mem, Some(lib), v, false)
  execute(Some(mem), Some(lib), false, output)
}
