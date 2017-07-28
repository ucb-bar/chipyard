package barstools.macros

import mdf.macrolib._

// Test the depth splitting aspect of the memory compiler.
// This file is for simple tests: one read-write port, powers of two sizes, etc.
// For example, implementing a 4096x32 memory using four 1024x32 memories.

trait HasSimpleDepthTestGenerator extends HasSimpleTestGenerator {
  this: MacroCompilerSpec with HasSRAMGenerator =>
    def width: Int

    override lazy val memWidth = width
    override lazy val libWidth = width

    // Generate a depth-splitting body.
    override def generateBody(): String = {
      var output = ""

      if (selectBits > 0) {
        output +=
  s"""
    node outer_addr_sel = bits(outer_addr, ${mem_addr_width - 1}, $lib_addr_width)
  """
      }

      for (i <- 0 to depthInstances - 1) {
        // We only support simple masks for now (either libMask == memMask or libMask == 1)
        val maskStatement = if (libHasMask) {
          if (libMaskGran.get == memMaskGran.get) {
            s"""mem_${i}_0.lib_mask <= bits(outer_mask, 0, 0)"""
          } else if (libMaskGran.get == 1) {
            // Construct a mask string.
            // Each bit gets the # of bits specified in maskGran.
            // Specify in descending order (MSB first)

            // This builds an array like m[1], m[1], m[0], m[0]
            val maskBitsArr: Seq[String] = ((memMaskBits - 1 to 0 by -1) flatMap (maskBit => {
              ((0 to memMaskGran.get - 1) map (_ => s"bits(outer_mask, ${maskBit}, ${maskBit})"))
            }))
            // Now build it into a recursive string like
            // cat(m[1], cat(m[1], cat(m[0], m[0])))
            val maskBitsStr: String = maskBitsArr.reverse.tail.foldLeft(maskBitsArr.reverse.head)((prev: String, next: String) => s"cat(${next}, ${prev})")
            s"""mem_${i}_0.lib_mask <= ${maskBitsStr}"""
          } else "" // TODO: implement when non-bitmasked memories are supported
        } else "" // No mask

        val enableIdentifier = if (selectBits > 0) s"""eq(outer_addr_sel, UInt<${selectBits}>("h${i.toHexString}"))""" else "UInt<1>(\"h1\")"
        output +=
  s"""
    inst mem_${i}_0 of awesome_lib_mem
    mem_${i}_0.lib_clk <= outer_clk
    mem_${i}_0.lib_addr <= outer_addr
    node outer_dout_${i}_0 = bits(mem_${i}_0.lib_dout, ${width - 1}, 0)
    mem_${i}_0.lib_din <= bits(outer_din, ${width - 1}, 0)
    ${maskStatement}
    mem_${i}_0.lib_write_en <= and(and(outer_write_en, UInt<1>("h1")), ${enableIdentifier})
    node outer_dout_${i} = outer_dout_${i}_0
  """
      }
      def generate_outer_dout_tree(i:Int, depthInstances: Int): String = {
        if (i > depthInstances - 1) {
          "UInt<1>(\"h0\")"
        } else {
          "mux(eq(outer_addr_sel, UInt<%d>(\"h%s\")), outer_dout_%d, %s)".format(
            selectBits, i.toHexString, i, generate_outer_dout_tree(i + 1, depthInstances)
          )
        }
      }
      output += "  outer_dout <= "
      if (selectBits > 0) {
        output += generate_outer_dout_tree(0, depthInstances)
      } else {
        output += """mux(UInt<1>("h1"), outer_dout_0, UInt<1>("h0"))"""
      }

      return output
    }
}

// Try different widths
class SplitDepth4096x32_rw extends MacroCompilerSpec with HasSRAMGenerator with HasSimpleDepthTestGenerator {
  override lazy val width = 32
  override lazy val memDepth = 4096
  override lazy val libDepth = 1024

  compileExecuteAndTest(mem, lib, v, output)
}

class SplitDepth4096x16_rw extends MacroCompilerSpec with HasSRAMGenerator with HasSimpleDepthTestGenerator {
  override lazy val width = 16
  override lazy val memDepth = 4096
  override lazy val libDepth = 1024

  compileExecuteAndTest(mem, lib, v, output)
}

class SplitDepth32768x8_rw extends MacroCompilerSpec with HasSRAMGenerator with HasSimpleDepthTestGenerator {
  override lazy val width = 8
  override lazy val memDepth = 32768
  override lazy val libDepth = 1024

  compileExecuteAndTest(mem, lib, v, output)
}

class SplitDepth4096x8_rw extends MacroCompilerSpec with HasSRAMGenerator with HasSimpleDepthTestGenerator {
  override lazy val width = 8
  override lazy val memDepth = 4096
  override lazy val libDepth = 1024

  compileExecuteAndTest(mem, lib, v, output)
}

class SplitDepth2048x8_rw extends MacroCompilerSpec with HasSRAMGenerator with HasSimpleDepthTestGenerator {
  override lazy val width = 8
  override lazy val memDepth = 2048
  override lazy val libDepth = 1024

  compileExecuteAndTest(mem, lib, v, output)
}

class SplitDepth1024x8_rw extends MacroCompilerSpec with HasSRAMGenerator with HasSimpleDepthTestGenerator {
  override lazy val width = 8
  override lazy val memDepth = 1024
  override lazy val libDepth = 1024

  compileExecuteAndTest(mem, lib, v, output)
}

// Non power of two
class SplitDepth2000x8_rw extends MacroCompilerSpec with HasSRAMGenerator with HasSimpleDepthTestGenerator {
  override lazy val width = 8
  override lazy val memDepth = 2000
  override lazy val libDepth = 1024

  compileExecuteAndTest(mem, lib, v, output)
}

class SplitDepth2049x8_rw extends MacroCompilerSpec with HasSRAMGenerator with HasSimpleDepthTestGenerator {
  override lazy val width = 8
  override lazy val memDepth = 2049
  override lazy val libDepth = 1024

  compileExecuteAndTest(mem, lib, v, output)
}

// Masked RAMs

// Test for mem mask == lib mask (i.e. mask is a write enable bit)
class SplitDepth2048x32_mrw_lib32 extends MacroCompilerSpec with HasSRAMGenerator with HasSimpleDepthTestGenerator {
  override lazy val width = 32
  override lazy val memDepth = 2048
  override lazy val libDepth = 1024
  override lazy val memMaskGran = Some(32)
  override lazy val libMaskGran = Some(32)

  compileExecuteAndTest(mem, lib, v, output)
}

class SplitDepth2048x8_mrw_lib8 extends MacroCompilerSpec with HasSRAMGenerator with HasSimpleDepthTestGenerator {
  override lazy val width = 8
  override lazy val memDepth = 2048
  override lazy val libDepth = 1024
  override lazy val memMaskGran = Some(8)
  override lazy val libMaskGran = Some(8)

  compileExecuteAndTest(mem, lib, v, output)
}

// Non-bit level mask
class SplitDepth2048x64_mrw_mem32_lib8 extends MacroCompilerSpec with HasSRAMGenerator with HasSimpleDepthTestGenerator {
  override lazy val width = 64
  override lazy val memDepth = 2048
  override lazy val libDepth = 1024
  override lazy val memMaskGran = Some(32)
  override lazy val libMaskGran = Some(8)

  it should "be enabled when non-bitmasked memories are supported" is (pending)
  //compileExecuteAndTest(mem, lib, v, output)
}

// Bit level mask
class SplitDepth2048x32_mrw_mem16_lib1 extends MacroCompilerSpec with HasSRAMGenerator with HasSimpleDepthTestGenerator {
  override lazy val width = 32
  override lazy val memDepth = 2048
  override lazy val libDepth = 1024
  override lazy val memMaskGran = Some(16)
  override lazy val libMaskGran = Some(1)

  compileExecuteAndTest(mem, lib, v, output)
}

class SplitDepth2048x32_mrw_mem8_lib1 extends MacroCompilerSpec with HasSRAMGenerator with HasSimpleDepthTestGenerator {
  override lazy val width = 32
  override lazy val memDepth = 2048
  override lazy val libDepth = 1024
  override lazy val memMaskGran = Some(8)
  override lazy val libMaskGran = Some(1)

  compileExecuteAndTest(mem, lib, v, output)
}

class SplitDepth2048x32_mrw_mem4_lib1 extends MacroCompilerSpec with HasSRAMGenerator with HasSimpleDepthTestGenerator {
  override lazy val width = 32
  override lazy val memDepth = 2048
  override lazy val libDepth = 1024
  override lazy val memMaskGran = Some(4)
  override lazy val libMaskGran = Some(1)

  compileExecuteAndTest(mem, lib, v, output)
}

class SplitDepth2048x32_mrw_mem2_lib1 extends MacroCompilerSpec with HasSRAMGenerator with HasSimpleDepthTestGenerator {
  override lazy val width = 32
  override lazy val memDepth = 2048
  override lazy val libDepth = 1024
  override lazy val memMaskGran = Some(2)
  override lazy val libMaskGran = Some(1)

  compileExecuteAndTest(mem, lib, v, output)
}

// Non-powers of 2 mask sizes
class SplitDepth2048x32_mrw_mem3_lib1 extends MacroCompilerSpec with HasSRAMGenerator with HasSimpleDepthTestGenerator {
  override lazy val width = 32
  override lazy val memDepth = 2048
  override lazy val libDepth = 1024
  override lazy val memMaskGran = Some(3)
  override lazy val libMaskGran = Some(1)

  it should "be enabled when non-power of two masks are supported" is (pending)
  //compileExecuteAndTest(mem, lib, v, output)
}

class SplitDepth2048x32_mrw_mem7_lib1 extends MacroCompilerSpec with HasSRAMGenerator with HasSimpleDepthTestGenerator {
  override lazy val width = 32
  override lazy val memDepth = 2048
  override lazy val libDepth = 1024
  override lazy val memMaskGran = Some(7)
  override lazy val libMaskGran = Some(1)

  it should "be enabled when non-power of two masks are supported" is (pending)
  //compileExecuteAndTest(mem, lib, v, output)
}

class SplitDepth2048x32_mrw_mem9_lib1 extends MacroCompilerSpec with HasSRAMGenerator with HasSimpleDepthTestGenerator {
  override lazy val width = 32
  override lazy val memDepth = 2048
  override lazy val libDepth = 1024
  override lazy val memMaskGran = Some(9)
  override lazy val libMaskGran = Some(1)

  it should "be enabled when non-power of two masks are supported" is (pending)
  //compileExecuteAndTest(mem, lib, v, output)
}

// Try an extra port
class SplitDepth2048x8_extraPort extends MacroCompilerSpec with HasSRAMGenerator with HasSimpleDepthTestGenerator {
  import mdf.macrolib._

  override lazy val width = 8
  override lazy val memDepth = 2048
  override lazy val libDepth = 1024
  override lazy val extraPorts = List(
    MacroExtraPort(name="extra_port", width=8, portType=Constant, value=0xff)
  )
  override lazy val extraTag = "extraPort"

  override def generateOutput(): String =
"""
circuit target_memory :
  module target_memory :
    input outer_clk : Clock
    input outer_addr : UInt<11>
    input outer_din : UInt<8>
    output outer_dout : UInt<8>
    input outer_write_en : UInt<1>

    node outer_addr_sel = bits(outer_addr, 10, 10)

    inst mem_0_0 of awesome_lib_mem
    mem_0_0.extra_port <= UInt<8>("hff")
    mem_0_0.lib_clk <= outer_clk
    mem_0_0.lib_addr <= outer_addr
    node outer_dout_0_0 = bits(mem_0_0.lib_dout, 7, 0)
    mem_0_0.lib_din <= bits(outer_din, 7, 0)

    mem_0_0.lib_write_en <= and(and(outer_write_en, UInt<1>("h1")), eq(outer_addr_sel, UInt<1>("h0")))
    node outer_dout_0 = outer_dout_0_0

    inst mem_1_0 of awesome_lib_mem
    mem_1_0.extra_port <= UInt<8>("hff")
    mem_1_0.lib_clk <= outer_clk
    mem_1_0.lib_addr <= outer_addr
    node outer_dout_1_0 = bits(mem_1_0.lib_dout, 7, 0)
    mem_1_0.lib_din <= bits(outer_din, 7, 0)

    mem_1_0.lib_write_en <= and(and(outer_write_en, UInt<1>("h1")), eq(outer_addr_sel, UInt<1>("h1")))
    node outer_dout_1 = outer_dout_1_0
    outer_dout <= mux(eq(outer_addr_sel, UInt<1>("h0")), outer_dout_0, mux(eq(outer_addr_sel, UInt<1>("h1")), outer_dout_1, UInt<1>("h0")))
  extmodule awesome_lib_mem :
    input lib_clk : Clock
    input lib_addr : UInt<10>
    input lib_din : UInt<8>
    output lib_dout : UInt<8>
    input lib_write_en : UInt<1>
    input extra_port : UInt<8>

    defname = awesome_lib_mem
  """

  compileExecuteAndTest(mem, lib, v, output)
}

// Split read and (non-masked) write ports (r+w).
class SplitDepth_SplitPortsNonMasked extends MacroCompilerSpec with HasSRAMGenerator {
  lazy val width = 8
  lazy val memDepth = 2048
  lazy val libDepth = 1024

  override val memPrefix = testDir
  override val libPrefix = testDir

  import mdf.macrolib._

  "Non-masked split lib; split mem" should "split fine" in {
    val lib = "lib-split_depth-r-w-split-lib-split-mem.json"
    val mem = "mem-split_depth-r-w-split-lib-split-mem.json"
    val v = "split_depth-r-w-split-lib-split-mem.v"

    val libMacro = SRAMMacro(
      macroType=SRAM,
      name="awesome_lib_mem",
      width=width,
      depth=libDepth,
      family="1r1w",
      ports=Seq(
        generateReadPort("innerA", width, libDepth),
        generateWritePort("innerB", width, libDepth)
      )
    )

    val memMacro = SRAMMacro(
      macroType=SRAM,
      name="target_memory",
      width=width,
      depth=memDepth,
      family="1r1w",
      ports=Seq(
        generateReadPort("outerB", width, memDepth),
        generateWritePort("outerA", width, memDepth)
      )
    )

    writeToLib(mem, Seq(memMacro))
    writeToLib(lib, Seq(libMacro))

    val output =
"""
circuit target_memory :
  module target_memory :
    input outerB_clk : Clock
    input outerB_addr : UInt<11>
    output outerB_dout : UInt<8>
    input outerA_clk : Clock
    input outerA_addr : UInt<11>
    input outerA_din : UInt<8>
    input outerA_write_en : UInt<1>

    node outerB_addr_sel = bits(outerB_addr, 10, 10)
    node outerA_addr_sel = bits(outerA_addr, 10, 10)
    inst mem_0_0 of awesome_lib_mem
    mem_0_0.innerB_clk <= outerA_clk
    mem_0_0.innerB_addr <= outerA_addr
    mem_0_0.innerB_din <= bits(outerA_din, 7, 0)
    mem_0_0.innerB_write_en <= and(and(outerA_write_en, UInt<1>("h1")), eq(outerA_addr_sel, UInt<1>("h0")))
    mem_0_0.innerA_clk <= outerB_clk
    mem_0_0.innerA_addr <= outerB_addr
    node outerB_dout_0_0 = bits(mem_0_0.innerA_dout, 7, 0)
    node outerB_dout_0 = outerB_dout_0_0
    inst mem_1_0 of awesome_lib_mem
    mem_1_0.innerB_clk <= outerA_clk
    mem_1_0.innerB_addr <= outerA_addr
    mem_1_0.innerB_din <= bits(outerA_din, 7, 0)
    mem_1_0.innerB_write_en <= and(and(outerA_write_en, UInt<1>("h1")), eq(outerA_addr_sel, UInt<1>("h1")))
    mem_1_0.innerA_clk <= outerB_clk
    mem_1_0.innerA_addr <= outerB_addr
    node outerB_dout_1_0 = bits(mem_1_0.innerA_dout, 7, 0)
    node outerB_dout_1 = outerB_dout_1_0
    outerB_dout <= mux(eq(outerB_addr_sel, UInt<1>("h0")), outerB_dout_0, mux(eq(outerB_addr_sel, UInt<1>("h1")), outerB_dout_1, UInt<1>("h0")))

  extmodule awesome_lib_mem :
    input innerA_clk : Clock
    input innerA_addr : UInt<10>
    output innerA_dout : UInt<8>
    input innerB_clk : Clock
    input innerB_addr : UInt<10>
    input innerB_din : UInt<8>
    input innerB_write_en : UInt<1>

    defname = awesome_lib_mem
"""

    compileExecuteAndTest(mem, lib, v, output)
  }

  "Non-masked regular lib; split mem" should "split fine" in {
    // Enable this test when the memory compiler can compile non-matched
    // memories (e.g. mrw mem and r+mw lib).
    // Right now all we can get is a "port count must match" error.
    pending

    val lib = "lib-split_depth-r-w-regular-lib-split-mem.json"
    val mem = "mem-split_depth-r-w-regular-lib-split-mem.json"
    val v = "split_depth-r-w-regular-lib-split-mem.v"

    val memMacro = SRAMMacro(
      macroType=SRAM,
      name="target_memory",
      width=width,
      depth=memDepth,
      family="1r1w",
      ports=Seq(
        generateReadPort("outerB", width, memDepth),
        generateWritePort("outerA", width, memDepth)
      )
    )

    writeToLib(mem, Seq(memMacro))
    writeToLib(lib, Seq(generateSRAM("awesome_lib_mem", "lib", width, libDepth)))

    val output =
"""
TODO
"""

    compileExecuteAndTest(mem, lib, v, output)
  }

  "Non-masked split lib; regular mem" should "split fine" in {
    // Enable this test when the memory compiler can compile non-matched
    // memories (e.g. mrw mem and r+mw lib).
    // Right now all we can get is a "port count must match" error.
    // [edwardw]: does this even make sense? Can we compile a 2-ported memory using 1-ported memories?
    pending

    val lib = "lib-split_depth-r-w-split-lib-regular-mem.json"
    val mem = "mem-split_depth-r-w-split-lib-regular-mem.json"
    val v = "split_depth-r-w-split-lib-regular-mem.v"

    val libMacro = SRAMMacro(
      macroType=SRAM,
      name="awesome_lib_mem",
      width=width,
      depth=libDepth,
      family="1rw",
      ports=Seq(
        generateReadPort("innerA", width, libDepth),
        generateWritePort("innerB", width, libDepth)
      )
    )

    writeToLib(mem, Seq(generateSRAM("target_memory", "outer", width, memDepth)))
    writeToLib(lib, Seq(libMacro))

    val output =
"""
TODO
"""

    compileExecuteAndTest(mem, lib, v, output)
  }
}

// Split read and (masked) write ports (r+mw).
class SplitDepth_SplitPortsMasked extends MacroCompilerSpec with HasSRAMGenerator {
  lazy val width = 8
  lazy val memDepth = 2048
  lazy val libDepth = 1024
  lazy val memMaskGran = Some(8)
  lazy val libMaskGran = Some(1)

  override val memPrefix = testDir
  override val libPrefix = testDir

  import mdf.macrolib._

  "Masked split lib; split mem" should "split fine" in {
    val lib = "lib-split_depth-r-mw-split-lib-split-mem.json"
    val mem = "mem-split_depth-r-mw-split-lib-split-mem.json"
    val v = "split_depth-r-mw-split-lib-split-mem.v"

    val libMacro = SRAMMacro(
      macroType=SRAM,
      name="awesome_lib_mem",
      width=width,
      depth=libDepth,
      family="1r1w",
      ports=Seq(
        generateReadPort("innerA", width, libDepth),
        generateWritePort("innerB", width, libDepth, libMaskGran)
      )
    )

    val memMacro = SRAMMacro(
      macroType=SRAM,
      name="target_memory",
      width=width,
      depth=memDepth,
      family="1r1w",
      ports=Seq(
        generateReadPort("outerB", width, memDepth),
        generateWritePort("outerA", width, memDepth, memMaskGran)
      )
    )

    writeToLib(mem, Seq(memMacro))
    writeToLib(lib, Seq(libMacro))

    val output =
"""
circuit target_memory :
  module target_memory :
    input outerB_clk : Clock
    input outerB_addr : UInt<11>
    output outerB_dout : UInt<8>
    input outerA_clk : Clock
    input outerA_addr : UInt<11>
    input outerA_din : UInt<8>
    input outerA_write_en : UInt<1>
    input outerA_mask : UInt<1>

    node outerB_addr_sel = bits(outerB_addr, 10, 10)
    node outerA_addr_sel = bits(outerA_addr, 10, 10)
    inst mem_0_0 of awesome_lib_mem
    mem_0_0.innerB_clk <= outerA_clk
    mem_0_0.innerB_addr <= outerA_addr
    mem_0_0.innerB_din <= bits(outerA_din, 7, 0)
    mem_0_0.innerB_mask <= cat(bits(outerA_mask, 0, 0), cat(bits(outerA_mask, 0, 0), cat(bits(outerA_mask, 0, 0), cat(bits(outerA_mask, 0, 0), cat(bits(outerA_mask, 0, 0), cat(bits(outerA_mask, 0, 0), cat(bits(outerA_mask, 0, 0), bits(outerA_mask, 0, 0))))))))
    mem_0_0.innerB_write_en <= and(and(outerA_write_en, UInt<1>("h1")), eq(outerA_addr_sel, UInt<1>("h0")))
    mem_0_0.innerA_clk <= outerB_clk
    mem_0_0.innerA_addr <= outerB_addr
    node outerB_dout_0_0 = bits(mem_0_0.innerA_dout, 7, 0)
    node outerB_dout_0 = outerB_dout_0_0
    inst mem_1_0 of awesome_lib_mem
    mem_1_0.innerB_clk <= outerA_clk
    mem_1_0.innerB_addr <= outerA_addr
    mem_1_0.innerB_din <= bits(outerA_din, 7, 0)
    mem_1_0.innerB_mask <= cat(bits(outerA_mask, 0, 0), cat(bits(outerA_mask, 0, 0), cat(bits(outerA_mask, 0, 0), cat(bits(outerA_mask, 0, 0), cat(bits(outerA_mask, 0, 0), cat(bits(outerA_mask, 0, 0), cat(bits(outerA_mask, 0, 0), bits(outerA_mask, 0, 0))))))))
    mem_1_0.innerB_write_en <= and(and(outerA_write_en, UInt<1>("h1")), eq(outerA_addr_sel, UInt<1>("h1")))
    mem_1_0.innerA_clk <= outerB_clk
    mem_1_0.innerA_addr <= outerB_addr
    node outerB_dout_1_0 = bits(mem_1_0.innerA_dout, 7, 0)
    node outerB_dout_1 = outerB_dout_1_0
    outerB_dout <= mux(eq(outerB_addr_sel, UInt<1>("h0")), outerB_dout_0, mux(eq(outerB_addr_sel, UInt<1>("h1")), outerB_dout_1, UInt<1>("h0")))

  extmodule awesome_lib_mem :
    input innerA_clk : Clock
    input innerA_addr : UInt<10>
    output innerA_dout : UInt<8>
    input innerB_clk : Clock
    input innerB_addr : UInt<10>
    input innerB_din : UInt<8>
    input innerB_write_en : UInt<1>
    input innerB_mask : UInt<8>

    defname = awesome_lib_mem
"""

    compileExecuteAndTest(mem, lib, v, output)
  }

  "Non-masked regular lib; split mem" should "split fine" in {
    // Enable this test when the memory compiler can compile non-matched
    // memories (e.g. mrw mem and r+mw lib).
    // Right now all we can get is a "port count must match" error.
    pending

    val lib = "lib-split_depth-r-mw-regular-lib-split-mem.json"
    val mem = "mem-split_depth-r-mw-regular-lib-split-mem.json"
    val v = "split_depth-r-mw-regular-lib-split-mem.v"

    val memMacro = SRAMMacro(
      macroType=SRAM,
      name="target_memory",
      width=width,
      depth=memDepth,
      family="1r1w",
      ports=Seq(
        generateReadPort("outerB", width, memDepth),
        generateWritePort("outerA", width, memDepth, memMaskGran)
      )
    )

    writeToLib(mem, Seq(memMacro))
    writeToLib(lib, Seq(generateSRAM("awesome_lib_mem", "lib", width, libDepth, libMaskGran)))

    val output =
"""
TODO
"""

    compileExecuteAndTest(mem, lib, v, output)
  }

  "Non-masked split lib; regular mem" should "split fine" in {
    // Enable this test when the memory compiler can compile non-matched
    // memories (e.g. mrw mem and r+mw lib).
    // Right now all we can get is a "port count must match" error.
    // [edwardw]: does this even make sense? Can we compile a 2-ported memory using 1-ported memories?
    pending

    val lib = "lib-split_depth-r-mw-split-lib-regular-mem.json"
    val mem = "mem-split_depth-r-mw-split-lib-regular-mem.json"
    val v = "split_depth-r-mw-split-lib-regular-mem.v"

    val libMacro = SRAMMacro(
      macroType=SRAM,
      name="awesome_lib_mem",
      width=width,
      depth=libDepth,
      family="1rw",
      ports=Seq(
        generateReadPort("innerA", width, libDepth),
        generateWritePort("innerB", width, libDepth, libMaskGran)
      )
    )

    writeToLib(mem, Seq(generateSRAM("target_memory", "outer", width, memDepth, memMaskGran)))
    writeToLib(lib, Seq(libMacro))

    val output =
"""
TODO
"""

    compileExecuteAndTest(mem, lib, v, output)
  }
}
