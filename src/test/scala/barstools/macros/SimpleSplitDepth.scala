package barstools.macros

// Test the depth splitting aspect of the memory compiler.
// This file is for simple tests: one read-write port, powers of two sizes, etc.
// For example, implementing a 4096x32 memory using four 1024x32 memories.

trait HasSimpleDepthTestGenerator extends HasSimpleTestGenerator {
  this: MacroCompilerSpec with HasSRAMGenerator =>
  def width: Int

  override lazy val memWidth: Int = width
  override lazy val libWidth: Int = width

  // Generate a depth-splitting body.
  override def generateBody(): String = {
    val output = new StringBuilder

    if (selectBits > 0) {
      output.append(
        s"""
    node ${memPortPrefix}_addr_sel = bits(${memPortPrefix}_addr, ${mem_addr_width - 1}, $lib_addr_width)
    reg ${memPortPrefix}_addr_sel_reg : UInt<$selectBits>, ${memPortPrefix}_clk with :
      reset => (UInt<1>("h0"), ${memPortPrefix}_addr_sel_reg)
    ${memPortPrefix}_addr_sel_reg <= mux(UInt<1>("h1"), ${memPortPrefix}_addr_sel, ${memPortPrefix}_addr_sel_reg)
"""
      )
    }

    for (i <- 0 until depthInstances) {

      val maskStatement = generateMaskStatement(0, i)
      val enableIdentifier =
        if (selectBits > 0) s"""eq(${memPortPrefix}_addr_sel, UInt<$selectBits>("h${i.toHexString}"))"""
        else "UInt<1>(\"h1\")"
      val chipEnable = s"""UInt<1>("h1")"""
      val writeEnable =
        if (memMaskGran.isEmpty) s"and(${memPortPrefix}_write_en, $chipEnable)" else s"${memPortPrefix}_write_en"
      output.append(
        s"""
    inst mem_${i}_0 of $lib_name
    mem_${i}_0.${libPortPrefix}_clk <= ${memPortPrefix}_clk
    mem_${i}_0.${libPortPrefix}_addr <= ${memPortPrefix}_addr
    node ${memPortPrefix}_dout_${i}_0 = bits(mem_${i}_0.${libPortPrefix}_dout, ${width - 1}, 0)
    mem_${i}_0.${libPortPrefix}_din <= bits(${memPortPrefix}_din, ${width - 1}, 0)
    $maskStatement
    mem_${i}_0.${libPortPrefix}_write_en <= and(and($writeEnable, UInt<1>("h1")), $enableIdentifier)
    node ${memPortPrefix}_dout_$i = ${memPortPrefix}_dout_${i}_0
  """
      )
    }
    def generate_outer_dout_tree(i: Int, depthInstances: Int): String = {
      if (i > depthInstances - 1) {
        s"""UInt<$libWidth>("h0")"""
      } else {
        s"""mux(eq(${memPortPrefix}_addr_sel_reg, UInt<%d>("h%s")), ${memPortPrefix}_dout_%d, %s)""".format(
          selectBits,
          i.toHexString,
          i,
          generate_outer_dout_tree(i + 1, depthInstances)
        )
      }
    }
    output.append(s"  ${memPortPrefix}_dout <= ")
    if (selectBits > 0) {
      output.append(generate_outer_dout_tree(0, depthInstances))
    } else {
      output.append(s"""mux(UInt<1>("h1"), ${memPortPrefix}_dout_0, UInt<$libWidth>("h0"))""")
    }

    output.toString
  }
}

// Try different widths
class SplitDepth4096x32_rw extends MacroCompilerSpec with HasSRAMGenerator with HasSimpleDepthTestGenerator {
  override lazy val width = 32
  override lazy val memDepth = BigInt(4096)
  override lazy val libDepth = BigInt(1024)

  compileExecuteAndTest(mem, lib, v, output)
}

class SplitDepth4096x16_rw extends MacroCompilerSpec with HasSRAMGenerator with HasSimpleDepthTestGenerator {
  override lazy val width = 16
  override lazy val memDepth = BigInt(4096)
  override lazy val libDepth = BigInt(1024)

  compileExecuteAndTest(mem, lib, v, output)
}

class SplitDepth32768x8_rw extends MacroCompilerSpec with HasSRAMGenerator with HasSimpleDepthTestGenerator {
  override lazy val width = 8
  override lazy val memDepth = BigInt(32768)
  override lazy val libDepth = BigInt(1024)

  compileExecuteAndTest(mem, lib, v, output)
}

class SplitDepth4096x8_rw extends MacroCompilerSpec with HasSRAMGenerator with HasSimpleDepthTestGenerator {
  override lazy val width = 8
  override lazy val memDepth = BigInt(4096)
  override lazy val libDepth = BigInt(1024)

  compileExecuteAndTest(mem, lib, v, output)
}

class SplitDepth2048x8_rw extends MacroCompilerSpec with HasSRAMGenerator with HasSimpleDepthTestGenerator {
  override lazy val width = 8
  override lazy val memDepth = BigInt(2048)
  override lazy val libDepth = BigInt(1024)

  compileExecuteAndTest(mem, lib, v, output)
}

class SplitDepth1024x8_rw extends MacroCompilerSpec with HasSRAMGenerator with HasSimpleDepthTestGenerator {
  override lazy val width = 8
  override lazy val memDepth = BigInt(1024)
  override lazy val libDepth = BigInt(1024)

  compileExecuteAndTest(mem, lib, v, output)
}

// Non power of two
class SplitDepth2000x8_rw extends MacroCompilerSpec with HasSRAMGenerator with HasSimpleDepthTestGenerator {
  override lazy val width = 8
  override lazy val memDepth = BigInt(2000)
  override lazy val libDepth = BigInt(1024)

  compileExecuteAndTest(mem, lib, v, output)
}

class SplitDepth2049x8_rw extends MacroCompilerSpec with HasSRAMGenerator with HasSimpleDepthTestGenerator {
  override lazy val width = 8
  override lazy val memDepth = BigInt(2049)
  override lazy val libDepth = BigInt(1024)

  compileExecuteAndTest(mem, lib, v, output)
}

// Masked RAMs

// Test for mem mask == lib mask (i.e. mask is a write enable bit)
class SplitDepth2048x32_mrw_lib32 extends MacroCompilerSpec with HasSRAMGenerator with HasSimpleDepthTestGenerator {
  override lazy val width = 32
  override lazy val memDepth = BigInt(2048)
  override lazy val libDepth = BigInt(1024)
  override lazy val memMaskGran: Option[Int] = Some(32)
  override lazy val libMaskGran: Option[Int] = Some(32)

  compileExecuteAndTest(mem, lib, v, output)
}

class SplitDepth2048x8_mrw_lib8 extends MacroCompilerSpec with HasSRAMGenerator with HasSimpleDepthTestGenerator {
  override lazy val width = 8
  override lazy val memDepth = BigInt(2048)
  override lazy val libDepth = BigInt(1024)
  override lazy val memMaskGran: Option[Int] = Some(8)
  override lazy val libMaskGran: Option[Int] = Some(8)

  compileExecuteAndTest(mem, lib, v, output)
}

// Non-bit level mask
class SplitDepth2048x64_mrw_mem32_lib8
    extends MacroCompilerSpec
    with HasSRAMGenerator
    with HasSimpleDepthTestGenerator {
  override lazy val width = 64
  override lazy val memDepth = BigInt(2048)
  override lazy val libDepth = BigInt(1024)
  override lazy val memMaskGran: Option[Int] = Some(32)
  override lazy val libMaskGran: Option[Int] = Some(8)

  compileExecuteAndTest(mem, lib, v, output)
}

// Bit level mask
class SplitDepth2048x32_mrw_mem16_lib1
    extends MacroCompilerSpec
    with HasSRAMGenerator
    with HasSimpleDepthTestGenerator {
  override lazy val width = 32
  override lazy val memDepth = BigInt(2048)
  override lazy val libDepth = BigInt(1024)
  override lazy val memMaskGran: Option[Int] = Some(16)
  override lazy val libMaskGran: Option[Int] = Some(1)

  compileExecuteAndTest(mem, lib, v, output)
}

class SplitDepth2048x32_mrw_mem8_lib1 extends MacroCompilerSpec with HasSRAMGenerator with HasSimpleDepthTestGenerator {
  override lazy val width = 32
  override lazy val memDepth = BigInt(2048)
  override lazy val libDepth = BigInt(1024)
  override lazy val memMaskGran: Option[Int] = Some(8)
  override lazy val libMaskGran: Option[Int] = Some(1)

  compileExecuteAndTest(mem, lib, v, output)
}

class SplitDepth2048x32_mrw_mem4_lib1 extends MacroCompilerSpec with HasSRAMGenerator with HasSimpleDepthTestGenerator {
  override lazy val width = 32
  override lazy val memDepth = BigInt(2048)
  override lazy val libDepth = BigInt(1024)
  override lazy val memMaskGran: Option[Int] = Some(4)
  override lazy val libMaskGran: Option[Int] = Some(1)

  compileExecuteAndTest(mem, lib, v, output)
}

class SplitDepth2048x32_mrw_mem2_lib1 extends MacroCompilerSpec with HasSRAMGenerator with HasSimpleDepthTestGenerator {
  override lazy val width = 32
  override lazy val memDepth = BigInt(2048)
  override lazy val libDepth = BigInt(1024)
  override lazy val memMaskGran: Option[Int] = Some(2)
  override lazy val libMaskGran: Option[Int] = Some(1)

  compileExecuteAndTest(mem, lib, v, output)
}

// Non-powers of 2 mask sizes
class SplitDepth2048x32_mrw_mem3_lib1 extends MacroCompilerSpec with HasSRAMGenerator with HasSimpleDepthTestGenerator {
  override lazy val width = 32
  override lazy val memDepth = BigInt(2048)
  override lazy val libDepth = BigInt(1024)
  override lazy val memMaskGran: Option[Int] = Some(3)
  override lazy val libMaskGran: Option[Int] = Some(1)

  (it should "be enabled when non-power of two masks are supported").is(pending)
  //compileExecuteAndTest(mem, lib, v, output)
}

class SplitDepth2048x32_mrw_mem7_lib1 extends MacroCompilerSpec with HasSRAMGenerator with HasSimpleDepthTestGenerator {
  override lazy val width = 32
  override lazy val memDepth = BigInt(2048)
  override lazy val libDepth = BigInt(1024)
  override lazy val memMaskGran: Option[Int] = Some(7)
  override lazy val libMaskGran: Option[Int] = Some(1)

  (it should "be enabled when non-power of two masks are supported").is(pending)
  //compileExecuteAndTest(mem, lib, v, output)
}

class SplitDepth2048x32_mrw_mem9_lib1 extends MacroCompilerSpec with HasSRAMGenerator with HasSimpleDepthTestGenerator {
  override lazy val width = 32
  override lazy val memDepth = BigInt(2048)
  override lazy val libDepth = BigInt(1024)
  override lazy val memMaskGran: Option[Int] = Some(9)
  override lazy val libMaskGran: Option[Int] = Some(1)

  (it should "be enabled when non-power of two masks are supported").is(pending)
  //compileExecuteAndTest(mem, lib, v, output)
}

// Try an extra port
class SplitDepth2048x8_extraPort extends MacroCompilerSpec with HasSRAMGenerator with HasSimpleDepthTestGenerator {
  import mdf.macrolib._

  override lazy val width = 8
  override lazy val memDepth = BigInt(2048)
  override lazy val libDepth = BigInt(1024)
  override lazy val extraPorts = List(
    MacroExtraPort(name = "extra_port", width = 8, portType = Constant, value = 0xff)
  )
  override lazy val extraTag = "extraPort"

  override def generateOutput(): String =
    """
circuit target_memory :
  module target_memory :
    input outer_addr : UInt<11>
    input outer_clk : Clock
    input outer_din : UInt<8>
    output outer_dout : UInt<8>
    input outer_write_en : UInt<1>

    node outer_addr_sel = bits(outer_addr, 10, 10)
    reg outer_addr_sel_reg : UInt<1>, outer_clk with :
      reset => (UInt<1>("h0"), outer_addr_sel_reg)
    outer_addr_sel_reg <= mux(UInt<1>("h1"), outer_addr_sel, outer_addr_sel_reg)

    inst mem_0_0 of awesome_lib_mem
    mem_0_0.extra_port <= UInt<8>("hff")
    mem_0_0.lib_clk <= outer_clk
    mem_0_0.lib_addr <= outer_addr
    node outer_dout_0_0 = bits(mem_0_0.lib_dout, 7, 0)
    mem_0_0.lib_din <= bits(outer_din, 7, 0)

    mem_0_0.lib_write_en <= and(and(and(outer_write_en, UInt<1>("h1")), UInt<1>("h1")), eq(outer_addr_sel, UInt<1>("h0")))
    node outer_dout_0 = outer_dout_0_0

    inst mem_1_0 of awesome_lib_mem
    mem_1_0.extra_port <= UInt<8>("hff")
    mem_1_0.lib_clk <= outer_clk
    mem_1_0.lib_addr <= outer_addr
    node outer_dout_1_0 = bits(mem_1_0.lib_dout, 7, 0)
    mem_1_0.lib_din <= bits(outer_din, 7, 0)

    mem_1_0.lib_write_en <= and(and(and(outer_write_en, UInt<1>("h1")), UInt<1>("h1")), eq(outer_addr_sel, UInt<1>("h1")))
    node outer_dout_1 = outer_dout_1_0
    outer_dout <= mux(eq(outer_addr_sel_reg, UInt<1>("h0")), outer_dout_0, mux(eq(outer_addr_sel_reg, UInt<1>("h1")), outer_dout_1, UInt<8>("h0")))
  extmodule awesome_lib_mem :
    input lib_addr : UInt<10>
    input lib_clk : Clock
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
  lazy val memDepth = BigInt(2048)
  lazy val libDepth = BigInt(1024)

  override val memPrefix: String = testDir
  override val libPrefix: String = testDir

  import mdf.macrolib._

  "Non-masked split lib; split mem" should "split fine" in {
    val lib = "lib-split_depth-r-w-split-lib-split-mem.json"
    val mem = "mem-split_depth-r-w-split-lib-split-mem.json"
    val v = "split_depth-r-w-split-lib-split-mem.v"

    val libMacro = SRAMMacro(
      name = "awesome_lib_mem",
      width = width,
      depth = libDepth,
      family = "1r1w",
      ports = Seq(
        generateReadPort("innerA", width, libDepth),
        generateWritePort("innerB", width, libDepth)
      )
    )

    val memMacro = SRAMMacro(
      name = "target_memory",
      width = width,
      depth = memDepth,
      family = "1r1w",
      ports = Seq(
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
    input outerB_addr : UInt<11>
    input outerB_clk : Clock
    output outerB_dout : UInt<8>
    input outerA_addr : UInt<11>
    input outerA_clk : Clock
    input outerA_din : UInt<8>
    input outerA_write_en : UInt<1>

    node outerB_addr_sel = bits(outerB_addr, 10, 10)
    reg outerB_addr_sel_reg : UInt<1>, outerB_clk with :
      reset => (UInt<1>("h0"), outerB_addr_sel_reg)
    outerB_addr_sel_reg <= mux(UInt<1>("h1"), outerB_addr_sel, outerB_addr_sel_reg)
    node outerA_addr_sel = bits(outerA_addr, 10, 10)
    inst mem_0_0 of awesome_lib_mem
    mem_0_0.innerB_clk <= outerA_clk
    mem_0_0.innerB_addr <= outerA_addr
    mem_0_0.innerB_din <= bits(outerA_din, 7, 0)
    mem_0_0.innerB_write_en <= and(and(and(outerA_write_en, UInt<1>("h1")), UInt<1>("h1")), eq(outerA_addr_sel, UInt<1>("h0")))
    mem_0_0.innerA_clk <= outerB_clk
    mem_0_0.innerA_addr <= outerB_addr
    node outerB_dout_0_0 = bits(mem_0_0.innerA_dout, 7, 0)
    node outerB_dout_0 = outerB_dout_0_0
    inst mem_1_0 of awesome_lib_mem
    mem_1_0.innerB_clk <= outerA_clk
    mem_1_0.innerB_addr <= outerA_addr
    mem_1_0.innerB_din <= bits(outerA_din, 7, 0)
    mem_1_0.innerB_write_en <= and(and(and(outerA_write_en, UInt<1>("h1")), UInt<1>("h1")), eq(outerA_addr_sel, UInt<1>("h1")))
    mem_1_0.innerA_clk <= outerB_clk
    mem_1_0.innerA_addr <= outerB_addr
    node outerB_dout_1_0 = bits(mem_1_0.innerA_dout, 7, 0)
    node outerB_dout_1 = outerB_dout_1_0
    outerB_dout <= mux(eq(outerB_addr_sel_reg, UInt<1>("h0")), outerB_dout_0, mux(eq(outerB_addr_sel_reg, UInt<1>("h1")), outerB_dout_1, UInt<8>("h0")))

  extmodule awesome_lib_mem :
    input innerA_addr : UInt<10>
    input innerA_clk : Clock
    output innerA_dout : UInt<8>
    input innerB_addr : UInt<10>
    input innerB_clk : Clock
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
      name = "target_memory",
      width = width,
      depth = memDepth,
      family = "1r1w",
      ports = Seq(
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
      name = "awesome_lib_mem",
      width = width,
      depth = libDepth,
      family = "1rw",
      ports = Seq(
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
  lazy val memDepth = BigInt(2048)
  lazy val libDepth = BigInt(1024)
  lazy val memMaskGran: Option[Int] = Some(8)
  lazy val libMaskGran: Option[Int] = Some(1)

  override val memPrefix: String = testDir
  override val libPrefix: String = testDir

  import mdf.macrolib._

  "Masked split lib; split mem" should "split fine" in {
    val lib = "lib-split_depth-r-mw-split-lib-split-mem.json"
    val mem = "mem-split_depth-r-mw-split-lib-split-mem.json"
    val v = "split_depth-r-mw-split-lib-split-mem.v"

    val libMacro = SRAMMacro(
      name = "awesome_lib_mem",
      width = width,
      depth = libDepth,
      family = "1r1w",
      ports = Seq(
        generateReadPort("innerA", width, libDepth),
        generateWritePort("innerB", width, libDepth, libMaskGran)
      )
    )

    val memMacro = SRAMMacro(
      name = "target_memory",
      width = width,
      depth = memDepth,
      family = "1r1w",
      ports = Seq(
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
    input outerB_addr : UInt<11>
    input outerB_clk : Clock
    output outerB_dout : UInt<8>
    input outerA_addr : UInt<11>
    input outerA_clk : Clock
    input outerA_din : UInt<8>
    input outerA_write_en : UInt<1>
    input outerA_mask : UInt<1>

    node outerB_addr_sel = bits(outerB_addr, 10, 10)
    reg outerB_addr_sel_reg : UInt<1>, outerB_clk with :
      reset => (UInt<1>("h0"), outerB_addr_sel_reg)
    outerB_addr_sel_reg <= mux(UInt<1>("h1"), outerB_addr_sel, outerB_addr_sel_reg)
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
    outerB_dout <= mux(eq(outerB_addr_sel_reg, UInt<1>("h0")), outerB_dout_0, mux(eq(outerB_addr_sel_reg, UInt<1>("h1")), outerB_dout_1, UInt<8>("h0")))

  extmodule awesome_lib_mem :
    input innerA_addr : UInt<10>
    input innerA_clk : Clock
    output innerA_dout : UInt<8>
    input innerB_addr : UInt<10>
    input innerB_clk : Clock
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
      name = "target_memory",
      width = width,
      depth = memDepth,
      family = "1r1w",
      ports = Seq(
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
      name = "awesome_lib_mem",
      width = width,
      depth = libDepth,
      family = "1rw",
      ports = Seq(
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
