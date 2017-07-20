package barstools.tapeout.transforms.macros.test

import firrtl.Utils.ceilLog2
import mdf.macrolib._

// Test the depth splitting aspect of the memory compiler.
// For example, implementing a 4096x32 memory using four 1024x32 memories.

trait HasSimpleDepthTestGenerator {
  this: MacroCompilerSpec with HasSRAMGenerator =>
    // Override these with "override lazy val".
    // Why lazy? These are used in the constructor here so overriding non-lazily
    // would be too late.
    def width: Int
    def mem_depth: Int
    def lib_depth: Int

    require (mem_depth >= lib_depth)

    override val memPrefix = testDir
    override val libPrefix = testDir

    val mem = s"mem-${mem_depth}x${width}-rw.json"
    val lib = s"lib-${lib_depth}x${width}-rw.json"
    val v = s"split_depth_${mem_depth}x${width}_rw.v"

    val mem_name = "target_memory"
    val mem_addr_width = ceilLog2(mem_depth)

    val lib_name = "awesome_lib_mem"
    val lib_addr_width = ceilLog2(lib_depth)

    writeToLib(lib, Seq(generateSRAM(lib_name, "lib", width, lib_depth)))
    writeToMem(mem, Seq(generateSRAM(mem_name, "outer", width, mem_depth)))

    val expectedInstances = mem_depth / lib_depth
    val selectBits = mem_addr_width - lib_addr_width
    var output =
s"""
circuit $mem_name :
  module $mem_name :
    input outer_clk : Clock
    input outer_addr : UInt<$mem_addr_width>
    input outer_din : UInt<$width>
    output outer_dout : UInt<$width>
    input outer_write_en : UInt<1>
"""

    if (selectBits > 0) {
      output +=
s"""
    node outer_addr_sel = bits(outer_addr, ${mem_addr_width - 1}, $lib_addr_width)
"""
    }

    for (i <- 0 to expectedInstances - 1) {
      val enableIdentifier = if (selectBits > 0) s"""eq(outer_addr_sel, UInt<${selectBits}>("h${i.toHexString}"))""" else "UInt<1>(\"h1\")"
      output +=
s"""
    inst mem_${i}_0 of awesome_lib_mem
    mem_${i}_0.lib_clk <= outer_clk
    mem_${i}_0.lib_addr <= outer_addr
    node outer_dout_${i}_0 = bits(mem_${i}_0.lib_dout, ${width - 1}, 0)
    mem_${i}_0.lib_din <= bits(outer_din, ${width - 1}, 0)
    mem_${i}_0.lib_write_en <= and(and(outer_write_en, UInt<1>("h1")), ${enableIdentifier})
    node outer_dout_${i} = outer_dout_${i}_0
"""
    }
    def generate_outer_dout_tree(i:Int, expectedInstances: Int): String = {
      if (i > expectedInstances - 1) {
        "UInt<1>(\"h0\")"
      } else {
        "mux(eq(outer_addr_sel, UInt<%d>(\"h%s\")), outer_dout_%d, %s)".format(
          selectBits, i.toHexString, i, generate_outer_dout_tree(i + 1, expectedInstances)
        )
      }
    }
    output += "    outer_dout <= "
    if (selectBits > 0) {
      output += generate_outer_dout_tree(0, expectedInstances)
    } else {
      output += """mux(UInt<1>("h1"), outer_dout_0, UInt<1>("h0"))"""
    }

    output +=
s"""
  extmodule $lib_name :
    input lib_clk : Clock
    input lib_addr : UInt<$lib_addr_width>
    input lib_din : UInt<$width>
    output lib_dout : UInt<$width>
    input lib_write_en : UInt<1>

    defname = $lib_name
"""
}

// Try different widths
class SplitDepth4096x32_rw extends MacroCompilerSpec with HasSRAMGenerator with HasSimpleDepthTestGenerator {
  override lazy val width = 32
  override lazy val mem_depth = 4096
  override lazy val lib_depth = 1024

  compile(mem, lib, v, false)
  execute(mem, lib, false, output)
}

class SplitDepth4096x16_rw extends MacroCompilerSpec with HasSRAMGenerator with HasSimpleDepthTestGenerator {
  override lazy val width = 16
  override lazy val mem_depth = 4096
  override lazy val lib_depth = 1024

  compile(mem, lib, v, false)
  execute(mem, lib, false, output)
}

class SplitDepth32768x8_rw extends MacroCompilerSpec with HasSRAMGenerator with HasSimpleDepthTestGenerator {
  override lazy val width = 8
  override lazy val mem_depth = 32768
  override lazy val lib_depth = 1024

  compile(mem, lib, v, false)
  execute(mem, lib, false, output)
}

class SplitDepth4096x8_rw extends MacroCompilerSpec with HasSRAMGenerator with HasSimpleDepthTestGenerator {
  override lazy val width = 8
  override lazy val mem_depth = 4096
  override lazy val lib_depth = 1024

  compile(mem, lib, v, false)
  execute(mem, lib, false, output)
}

class SplitDepth2048x8_rw extends MacroCompilerSpec with HasSRAMGenerator with HasSimpleDepthTestGenerator {
  override lazy val width = 8
  override lazy val mem_depth = 2048
  override lazy val lib_depth = 1024

  compile(mem, lib, v, false)
  execute(mem, lib, false, output)
}

class SplitDepth1024x8_rw extends MacroCompilerSpec with HasSRAMGenerator with HasSimpleDepthTestGenerator {
  override lazy val width = 8
  override lazy val mem_depth = 1024
  override lazy val lib_depth = 1024

  compile(mem, lib, v, false)
  execute(mem, lib, false, output)
}

// Non power of two
class SplitDepth1024x8_rw extends MacroCompilerSpec with HasSRAMGenerator with HasSimpleDepthTestGenerator {
  override lazy val width = 8
  override lazy val mem_depth = 1024
  override lazy val lib_depth = 1024

  compile(mem, lib, v, false)
  execute(mem, lib, false, output)
}

// Masked RAMs

class SplitDepth2048x8_mrw extends MacroCompilerSpec {
  val mem = "mem-2048x8-mrw.json"
  val lib = "lib-1024x8-mrw.json"
  val v = "split_depth_2048x8_mrw.v"
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
  compile(mem, lib, v, false)
  execute(mem, lib, false, output)
}

//~ class SplitDepth2048x8_n28 extends MacroCompilerSpec {
  //~ val mem = new File(macroDir, "mem-2048x8-mrw.json")
  //~ val lib = new File(macroDir, "lib-1024x8-n28.json")
  //~ val v = new File(testDir, "split_depth_2048x8_n28.v")
  //~ val output =
//~ """
//~ circuit name_of_sram_module :
  //~ module name_of_sram_module :
    //~ input clock : Clock
    //~ input RW0A : UInt<11>
    //~ input RW0I : UInt<8>
    //~ output RW0O : UInt<8>
    //~ input RW0E : UInt<1>
    //~ input RW0W : UInt<1>
    //~ input RW0M : UInt<1>

    //~ node RW0A_sel = bits(RW0A, 10, 10)
    //~ inst mem_0_0 of vendor_sram
    //~ mem_0_0.clock <= clock
    //~ mem_0_0.RW0A <= RW0A
    //~ node RW0O_0_0 = bits(mem_0_0.RW0O, 7, 0)
    //~ mem_0_0.RW0I <= bits(RW0I, 7, 0)
    //~ mem_0_0.RW0M <= cat(bits(RW0M, 0, 0), cat(bits(RW0M, 0, 0), cat(bits(RW0M, 0, 0), cat(bits(RW0M, 0, 0), cat(bits(RW0M, 0, 0), cat(bits(RW0M, 0, 0), cat(bits(RW0M, 0, 0), bits(RW0M, 0, 0))))))))
    //~ mem_0_0.RW0W <= and(RW0W, eq(RW0A_sel, UInt<1>("h0")))
    //~ mem_0_0.RW0E <= and(RW0E, eq(RW0A_sel, UInt<1>("h0")))
    //~ node RW0O_0 = RW0O_0_0
    //~ inst mem_1_0 of vendor_sram
    //~ mem_1_0.clock <= clock
    //~ mem_1_0.RW0A <= RW0A
    //~ node RW0O_1_0 = bits(mem_1_0.RW0O, 7, 0)
    //~ mem_1_0.RW0I <= bits(RW0I, 7, 0)
    //~ mem_1_0.RW0M <= cat(bits(RW0M, 0, 0), cat(bits(RW0M, 0, 0), cat(bits(RW0M, 0, 0), cat(bits(RW0M, 0, 0), cat(bits(RW0M, 0, 0), cat(bits(RW0M, 0, 0), cat(bits(RW0M, 0, 0), bits(RW0M, 0, 0))))))))
    //~ mem_1_0.RW0W <= and(RW0W, eq(RW0A_sel, UInt<1>("h1")))
    //~ mem_1_0.RW0E <= and(RW0E, eq(RW0A_sel, UInt<1>("h1")))
    //~ node RW0O_1 = RW0O_1_0
    //~ RW0O <= mux(eq(RW0A_sel, UInt<1>("h0")), RW0O_0, mux(eq(RW0A_sel, UInt<1>("h1")), RW0O_1, UInt<1>("h0")))

  //~ extmodule vendor_sram :
    //~ input clock : Clock
    //~ input RW0A : UInt<10>
    //~ input RW0I : UInt<8>
    //~ output RW0O : UInt<8>
    //~ input RW0E : UInt<1>
    //~ input RW0W : UInt<1>
    //~ input RW0M : UInt<8>

    //~ defname = vendor_sram
//~ """
  //~ compile(mem, lib, v, false)
  //~ execute(mem, lib, false, output)
//~ }

//~ class SplitDepth2048x8_r_mw extends MacroCompilerSpec {
  //~ val mem = new File(macroDir, "mem-2048x8-r-mw.json")
  //~ val lib = new File(macroDir, "lib-1024x8-r-mw.json")
  //~ val v = new File(testDir, "split_depth_2048x8_r_mw.v")
  //~ val output =
//~ """
//~ circuit name_of_sram_module :
  //~ module name_of_sram_module :
    //~ input clock : Clock
    //~ input W0A : UInt<11>
    //~ input W0I : UInt<8>
    //~ input W0E : UInt<1>
    //~ input W0M : UInt<1>
    //~ input clock : Clock
    //~ input R0A : UInt<11>
    //~ output R0O : UInt<8>

    //~ node W0A_sel = bits(W0A, 10, 10)
    //~ node R0A_sel = bits(R0A, 10, 10)
    //~ inst mem_0_0 of vendor_sram
    //~ mem_0_0.clock <= clock
    //~ mem_0_0.W0A <= W0A
    //~ mem_0_0.W0I <= bits(W0I, 7, 0)
    //~ mem_0_0.W0M <= bits(W0M, 0, 0)
    //~ mem_0_0.W0W <= and(UInt<1>("h1"), eq(W0A_sel, UInt<1>("h0")))
    //~ mem_0_0.W0E <= and(W0E, eq(W0A_sel, UInt<1>("h0")))
    //~ mem_0_0.clock <= clock
    //~ mem_0_0.R0A <= R0A
    //~ node R0O_0_0 = bits(mem_0_0.R0O, 7, 0)
    //~ node R0O_0 = R0O_0_0
    //~ inst mem_1_0 of vendor_sram
    //~ mem_1_0.clock <= clock
    //~ mem_1_0.W0A <= W0A
    //~ mem_1_0.W0I <= bits(W0I, 7, 0)
    //~ mem_1_0.W0M <= bits(W0M, 0, 0)
    //~ mem_1_0.W0W <= and(UInt<1>("h1"), eq(W0A_sel, UInt<1>("h1")))
    //~ mem_1_0.W0E <= and(W0E, eq(W0A_sel, UInt<1>("h1")))
    //~ mem_1_0.clock <= clock
    //~ mem_1_0.R0A <= R0A
    //~ node R0O_1_0 = bits(mem_1_0.R0O, 7, 0)
    //~ node R0O_1 = R0O_1_0
    //~ R0O <= mux(eq(R0A_sel, UInt<1>("h0")), R0O_0, mux(eq(R0A_sel, UInt<1>("h1")), R0O_1, UInt<1>("h0")))

  //~ extmodule vendor_sram :
    //~ input clock : Clock
    //~ input R0A : UInt<10>
    //~ output R0O : UInt<8>
    //~ input clock : Clock
    //~ input W0A : UInt<10>
    //~ input W0I : UInt<8>
    //~ input W0E : UInt<1>
    //~ input W0W : UInt<1>
    //~ input W0M : UInt<1>

    //~ defname = vendor_sram
//~ """
  //~ compile(mem, lib, v, false)
  //~ execute(mem, lib, false, output)
//~ }


//~ class SplitDepth2048x8_mrw_Sleep extends MacroCompilerSpec {
  //~ val mem = new File(macroDir, "mem-2048x8-mrw.json")
  //~ val lib = new File(macroDir, "lib-1024x8-sleep.json")
  //~ val v = new File(testDir, "split_depth_2048x8_sleep.v")
  //~ val output =
//~ """
//~ circuit name_of_sram_module :
  //~ module name_of_sram_module :
    //~ input clock : Clock
    //~ input RW0A : UInt<11>
    //~ input RW0I : UInt<8>
    //~ output RW0O : UInt<8>
    //~ input RW0E : UInt<1>
    //~ input RW0W : UInt<1>
    //~ input RW0M : UInt<1>

    //~ node RW0A_sel = bits(RW0A, 10, 10)
    //~ inst mem_0_0 of vendor_sram
    //~ mem_0_0.sleep <= UInt<1>("h0")
    //~ mem_0_0.clock <= clock
    //~ mem_0_0.RW0A <= RW0A
    //~ node RW0O_0_0 = bits(mem_0_0.RW0O, 7, 0)
    //~ mem_0_0.RW0I <= bits(RW0I, 7, 0)
    //~ mem_0_0.RW0M <= bits(RW0M, 0, 0)
    //~ mem_0_0.RW0W <= and(RW0W, eq(RW0A_sel, UInt<1>("h0")))
    //~ mem_0_0.RW0E <= and(RW0E, eq(RW0A_sel, UInt<1>("h0")))
    //~ node RW0O_0 = RW0O_0_0
    //~ inst mem_1_0 of vendor_sram
    //~ mem_1_0.sleep <= UInt<1>("h0")
    //~ mem_1_0.clock <= clock
    //~ mem_1_0.RW0A <= RW0A
    //~ node RW0O_1_0 = bits(mem_1_0.RW0O, 7, 0)
    //~ mem_1_0.RW0I <= bits(RW0I, 7, 0)
    //~ mem_1_0.RW0M <= bits(RW0M, 0, 0)
    //~ mem_1_0.RW0W <= and(RW0W, eq(RW0A_sel, UInt<1>("h1")))
    //~ mem_1_0.RW0E <= and(RW0E, eq(RW0A_sel, UInt<1>("h1")))
    //~ node RW0O_1 = RW0O_1_0
    //~ RW0O <= mux(eq(RW0A_sel, UInt<1>("h0")), RW0O_0, mux(eq(RW0A_sel, UInt<1>("h1")), RW0O_1, UInt<1>("h0")))

  //~ extmodule vendor_sram :
    //~ input clock : Clock
    //~ input RW0A : UInt<10>
    //~ input RW0I : UInt<8>
    //~ output RW0O : UInt<8>
    //~ input RW0E : UInt<1>
    //~ input RW0W : UInt<1>
    //~ input RW0M : UInt<1>
    //~ input sleep : UInt<1>

    //~ defname = vendor_sram
//~ """
  //~ compile(mem, lib, v, false)
  //~ execute(mem, lib, false, output)
//~ }
