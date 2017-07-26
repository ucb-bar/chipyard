package barstools.macros

import firrtl.Utils.ceilLog2
import mdf.macrolib._

// Test the depth splitting aspect of the memory compiler.
// This file is for simple tests: one read-write port, powers of two sizes, etc.
// For example, implementing a 4096x32 memory using four 1024x32 memories.

trait HasSimpleDepthTestGenerator {
  this: MacroCompilerSpec with HasSRAMGenerator =>
    // Override these with "override lazy val".
    // Why lazy? These are used in the constructor here so overriding non-lazily
    // would be too late.
    def width: Int
    def mem_depth: Int
    def lib_depth: Int
    def mem_maskGran: Option[Int] = None
    def lib_maskGran: Option[Int] = None
    def extraPorts: Seq[mdf.macrolib.MacroExtraPort] = List()
    def extraTag: String = ""

    require (mem_depth >= lib_depth)

    override val memPrefix = testDir
    override val libPrefix = testDir

    // Convenience variables to check if a mask exists.
    val memHasMask = mem_maskGran != None
    val libHasMask = lib_maskGran != None
    // We need to figure out how many mask bits there are in the mem.
    val memMaskBits = if (memHasMask) width / mem_maskGran.get else 0
    val libMaskBits = if (libHasMask) width / lib_maskGran.get else 0
    // Generate "mrw" vs "rw" tags.
    val memTag = (if (memHasMask) "m" else "") + "rw" + (if (mem_maskGran.nonEmpty) s"_gran${mem_maskGran.get}" else "")
    val libTag = (if (libHasMask) "m" else "") + "rw" + (if (lib_maskGran.nonEmpty) s"_gran${lib_maskGran.get}" else "")

    val extraTagPrefixed = if (extraTag == "") "" else ("-" + extraTag)

    val mem = s"mem-${mem_depth}x${width}-${memTag}${extraTagPrefixed}.json"
    val lib = s"lib-${lib_depth}x${width}-${libTag}${extraTagPrefixed}.json"
    val v = s"split_depth_${mem_depth}x${width}_${memTag}${extraTagPrefixed}.v"

    val mem_name = "target_memory"
    val mem_addr_width = ceilLog2(mem_depth)

    val lib_name = "awesome_lib_mem"
    val lib_addr_width = ceilLog2(lib_depth)

    writeToLib(lib, Seq(generateSRAM(lib_name, "lib", width, lib_depth, lib_maskGran, extraPorts)))
    writeToMem(mem, Seq(generateSRAM(mem_name, "outer", width, mem_depth, mem_maskGran)))

    // Number of lib instances needed to hold the mem.
    // Round up (e.g. 1.5 instances = effectively 2 instances)
    val expectedInstances = math.ceil(mem_depth.toFloat / lib_depth).toInt
    val selectBits = mem_addr_width - lib_addr_width

    val headerMask = if (memHasMask) s"input outer_mask : UInt<${memMaskBits}>" else ""
    val header = s"""
circuit $mem_name :
  module $mem_name :
    input outer_clk : Clock
    input outer_addr : UInt<$mem_addr_width>
    input outer_din : UInt<$width>
    output outer_dout : UInt<$width>
    input outer_write_en : UInt<1>
    ${headerMask}
"""

    val footerMask = if (libHasMask) s"input lib_mask : UInt<${libMaskBits}>" else ""
    val footer = s"""
  extmodule $lib_name :
    input lib_clk : Clock
    input lib_addr : UInt<$lib_addr_width>
    input lib_din : UInt<$width>
    output lib_dout : UInt<$width>
    input lib_write_en : UInt<1>
    ${footerMask}

    defname = $lib_name
"""

    var output = header

    if (selectBits > 0) {
      output +=
s"""
    node outer_addr_sel = bits(outer_addr, ${mem_addr_width - 1}, $lib_addr_width)
"""
    }

    for (i <- 0 to expectedInstances - 1) {
      // We only support simple masks for now (either libMask == memMask or libMask == 1)
      val maskStatement = if (libHasMask) {
        if (lib_maskGran.get == mem_maskGran.get) {
          s"""mem_${i}_0.lib_mask <= bits(outer_mask, 0, 0)"""
        } else if (lib_maskGran.get == 1) {
          // Construct a mask string.
          // Each bit gets the # of bits specified in maskGran.
          // Specify in descending order (MSB first)

          // This builds an array like m[1], m[1], m[0], m[0]
          val maskBitsArr: Seq[String] = ((memMaskBits - 1 to 0 by -1) flatMap (maskBit => {
            ((0 to mem_maskGran.get - 1) map (_ => s"bits(outer_mask, ${maskBit}, ${maskBit})"))
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

    output += footer
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
class SplitDepth2000x8_rw extends MacroCompilerSpec with HasSRAMGenerator with HasSimpleDepthTestGenerator {
  override lazy val width = 8
  override lazy val mem_depth = 2000
  override lazy val lib_depth = 1024

  compile(mem, lib, v, false)
  execute(mem, lib, false, output)
}

class SplitDepth2049x8_rw extends MacroCompilerSpec with HasSRAMGenerator with HasSimpleDepthTestGenerator {
  override lazy val width = 8
  override lazy val mem_depth = 2049
  override lazy val lib_depth = 1024

  compile(mem, lib, v, false)
  execute(mem, lib, false, output)
}

// Masked RAMs

// Test for mem mask == lib mask (i.e. mask is a write enable bit)
class SplitDepth2048x32_mrw_lib32 extends MacroCompilerSpec with HasSRAMGenerator with HasSimpleDepthTestGenerator {
  override lazy val width = 32
  override lazy val mem_depth = 2048
  override lazy val lib_depth = 1024
  override lazy val mem_maskGran = Some(32)
  override lazy val lib_maskGran = Some(32)

  compile(mem, lib, v, false)
  execute(mem, lib, false, output)
}

class SplitDepth2048x8_mrw_lib8 extends MacroCompilerSpec with HasSRAMGenerator with HasSimpleDepthTestGenerator {
  override lazy val width = 8
  override lazy val mem_depth = 2048
  override lazy val lib_depth = 1024
  override lazy val mem_maskGran = Some(8)
  override lazy val lib_maskGran = Some(8)

  compile(mem, lib, v, false)
  execute(mem, lib, false, output)
}

// Non-bit level mask
class SplitDepth2048x64_mrw_mem32_lib8 extends MacroCompilerSpec with HasSRAMGenerator with HasSimpleDepthTestGenerator {
  override lazy val width = 64
  override lazy val mem_depth = 2048
  override lazy val lib_depth = 1024
  override lazy val mem_maskGran = Some(32)
  override lazy val lib_maskGran = Some(8)

  it should "be enabled when non-bitmasked memories are supported" is (pending)
  //compile(mem, lib, v, false)
  //execute(mem, lib, false, output)
}

// Bit level mask
class SplitDepth2048x32_mrw_mem16_lib1 extends MacroCompilerSpec with HasSRAMGenerator with HasSimpleDepthTestGenerator {
  override lazy val width = 32
  override lazy val mem_depth = 2048
  override lazy val lib_depth = 1024
  override lazy val mem_maskGran = Some(16)
  override lazy val lib_maskGran = Some(1)

  compile(mem, lib, v, false)
  execute(mem, lib, false, output)
}

class SplitDepth2048x32_mrw_mem8_lib1 extends MacroCompilerSpec with HasSRAMGenerator with HasSimpleDepthTestGenerator {
  override lazy val width = 32
  override lazy val mem_depth = 2048
  override lazy val lib_depth = 1024
  override lazy val mem_maskGran = Some(8)
  override lazy val lib_maskGran = Some(1)

  compile(mem, lib, v, false)
  execute(mem, lib, false, output)
}

class SplitDepth2048x32_mrw_mem4_lib1 extends MacroCompilerSpec with HasSRAMGenerator with HasSimpleDepthTestGenerator {
  override lazy val width = 32
  override lazy val mem_depth = 2048
  override lazy val lib_depth = 1024
  override lazy val mem_maskGran = Some(4)
  override lazy val lib_maskGran = Some(1)

  compile(mem, lib, v, false)
  execute(mem, lib, false, output)
}

class SplitDepth2048x32_mrw_mem2_lib1 extends MacroCompilerSpec with HasSRAMGenerator with HasSimpleDepthTestGenerator {
  override lazy val width = 32
  override lazy val mem_depth = 2048
  override lazy val lib_depth = 1024
  override lazy val mem_maskGran = Some(2)
  override lazy val lib_maskGran = Some(1)

  compile(mem, lib, v, false)
  execute(mem, lib, false, output)
}

// Non-powers of 2 mask sizes
class SplitDepth2048x32_mrw_mem3_lib1 extends MacroCompilerSpec with HasSRAMGenerator with HasSimpleDepthTestGenerator {
  override lazy val width = 32
  override lazy val mem_depth = 2048
  override lazy val lib_depth = 1024
  override lazy val mem_maskGran = Some(3)
  override lazy val lib_maskGran = Some(1)

  it should "be enabled when non-power of two masks are supported" is (pending)
  //compile(mem, lib, v, false)
  //execute(mem, lib, false, output)
}

class SplitDepth2048x32_mrw_mem7_lib1 extends MacroCompilerSpec with HasSRAMGenerator with HasSimpleDepthTestGenerator {
  override lazy val width = 32
  override lazy val mem_depth = 2048
  override lazy val lib_depth = 1024
  override lazy val mem_maskGran = Some(7)
  override lazy val lib_maskGran = Some(1)

  it should "be enabled when non-power of two masks are supported" is (pending)
  //compile(mem, lib, v, false)
  //execute(mem, lib, false, output)
}

class SplitDepth2048x32_mrw_mem9_lib1 extends MacroCompilerSpec with HasSRAMGenerator with HasSimpleDepthTestGenerator {
  override lazy val width = 32
  override lazy val mem_depth = 2048
  override lazy val lib_depth = 1024
  override lazy val mem_maskGran = Some(9)
  override lazy val lib_maskGran = Some(1)

  it should "be enabled when non-power of two masks are supported" is (pending)
  //compile(mem, lib, v, false)
  //execute(mem, lib, false, output)
}

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

// Try an extra port
class SplitDepth2048x8_extraPort extends MacroCompilerSpec with HasSRAMGenerator with HasSimpleDepthTestGenerator {
  import mdf.macrolib._

  override lazy val width = 8
  override lazy val mem_depth = 2048
  override lazy val lib_depth = 1024
  override lazy val extraPorts = List(
    MacroExtraPort(name="extra_port", width=8, portType=Constant, value=0xff)
  )
  override lazy val extraTag = "extraPort"

  val outputCustom =
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
  compile(mem, lib, v, false)
  execute(mem, lib, false, outputCustom)
}
