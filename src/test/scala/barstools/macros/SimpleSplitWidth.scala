package barstools.macros

// Test the width splitting aspect of the memory compiler.
// For example, implementing a 1024x32 memory using four 1024x8 memories.

trait HasSimpleWidthTestGenerator extends HasSimpleTestGenerator {
  this: MacroCompilerSpec with HasSRAMGenerator =>
  def depth: BigInt

  override lazy val memDepth: BigInt = depth
  override lazy val libDepth: BigInt = depth

  override def generateBody(): String = {
    val output = new StringBuilder

    // Generate mem_0_<i> lines for number of width instances.
    output.append(
      (0 until widthInstances).map { i: Int =>
        s"""
    inst mem_0_$i of $lib_name
"""
      }.reduceLeft(_ + _)
    )

    // Generate submemory connection blocks.
    output.append((for (i <- 0 until widthInstances) yield {
      // Width of this submemory.
      val myMemWidth = if (i == widthInstances - 1) lastWidthBits else usableLibWidth
      // Base bit of this submemory.
      // e.g. if libWidth is 8 and this is submemory 2 (0-indexed), then this
      // would be 16.
      val myBaseBit = usableLibWidth * i

      val maskStatement = generateMaskStatement(i, 0)

      // We need to use writeEnable as a crude "mask" if mem has a mask but
      // lib does not.
      val writeEnableBit = if (libMaskGran.isEmpty && memMaskGran.isDefined) {
        val outerMaskBit = myBaseBit / memMaskGran.get
        s"bits(outer_mask, $outerMaskBit, $outerMaskBit)"
      } else """UInt<1>("h1")"""
      val chipEnable = s"""UInt<1>("h1")"""
      val writeEnableExpr =
        if (libMaskGran.isEmpty) s"and(${memPortPrefix}_write_en, $chipEnable)" else s"${memPortPrefix}_write_en"

      s"""
    mem_0_$i.${libPortPrefix}_clk <= ${memPortPrefix}_clk
    mem_0_$i.${libPortPrefix}_addr <= ${memPortPrefix}_addr
    node ${memPortPrefix}_dout_0_$i = bits(mem_0_$i.${libPortPrefix}_dout, ${myMemWidth - 1}, 0)
    mem_0_$i.${libPortPrefix}_din <= bits(${memPortPrefix}_din, ${myBaseBit + myMemWidth - 1}, $myBaseBit)
    $maskStatement
    mem_0_$i.${libPortPrefix}_write_en <= and(and($writeEnableExpr, $writeEnableBit), UInt<1>("h1"))
"""
    }).reduceLeft(_ + _))

    // Generate final output that concats together the sub-memories.
    // e.g. cat(outer_dout_0_2, cat(outer_dout_0_1, outer_dout_0_0))
    output.append {
      val doutStatements = (widthInstances - 1 to 0 by -1).map(i => s"${memPortPrefix}_dout_0_$i")
      val catStmt = doutStatements.init.foldRight(doutStatements.last)((l: String, r: String) => s"cat($l, $r)")
      s"""
    node ${memPortPrefix}_dout_0 = $catStmt
"""
    }

    output.append(s"""
    ${memPortPrefix}_dout <= mux(UInt<1>("h1"), ${memPortPrefix}_dout_0, UInt<$memWidth>("h0"))
""")
    output.toString
  }
}

// Try different widths against a base memory width of 8.
class SplitWidth1024x128_rw extends MacroCompilerSpec with HasSRAMGenerator with HasSimpleWidthTestGenerator {
  override lazy val depth = BigInt(1024)
  override lazy val memWidth = 128
  override lazy val libWidth = 8

  compileExecuteAndTest(mem, lib, v, output)
}

class SplitWidth1024x64_rw extends MacroCompilerSpec with HasSRAMGenerator with HasSimpleWidthTestGenerator {
  override lazy val depth = BigInt(1024)
  override lazy val memWidth = 64
  override lazy val libWidth = 8

  compileExecuteAndTest(mem, lib, v, output)
}

class SplitWidth1024x32_rw extends MacroCompilerSpec with HasSRAMGenerator with HasSimpleWidthTestGenerator {
  override lazy val depth = BigInt(1024)
  override lazy val memWidth = 32
  override lazy val libWidth = 8

  compileExecuteAndTest(mem, lib, v, output)
}

class SplitWidth1024x16_rw extends MacroCompilerSpec with HasSRAMGenerator with HasSimpleWidthTestGenerator {
  override lazy val depth = BigInt(1024)
  override lazy val memWidth = 16
  override lazy val libWidth = 8

  compileExecuteAndTest(mem, lib, v, output)
}

class SplitWidth1024x8_rw extends MacroCompilerSpec with HasSRAMGenerator with HasSimpleWidthTestGenerator {
  override lazy val depth = BigInt(1024)
  override lazy val memWidth = 8
  override lazy val libWidth = 8

  compileExecuteAndTest(mem, lib, v, output)
}

// Try different widths against a base memory width of 16.
class SplitWidth1024x128_lib16_rw extends MacroCompilerSpec with HasSRAMGenerator with HasSimpleWidthTestGenerator {
  override lazy val depth = BigInt(1024)
  override lazy val memWidth = 128
  override lazy val libWidth = 16

  compileExecuteAndTest(mem, lib, v, output)
}

class SplitWidth1024x64_lib16_rw extends MacroCompilerSpec with HasSRAMGenerator with HasSimpleWidthTestGenerator {
  override lazy val depth = BigInt(1024)
  override lazy val memWidth = 64
  override lazy val libWidth = 16

  compileExecuteAndTest(mem, lib, v, output)
}

class SplitWidth1024x32_lib16_rw extends MacroCompilerSpec with HasSRAMGenerator with HasSimpleWidthTestGenerator {
  override lazy val depth = BigInt(1024)
  override lazy val memWidth = 32
  override lazy val libWidth = 16

  compileExecuteAndTest(mem, lib, v, output)
}

class SplitWidth1024x16_lib16_rw extends MacroCompilerSpec with HasSRAMGenerator with HasSimpleWidthTestGenerator {
  override lazy val depth = BigInt(1024)
  override lazy val memWidth = 16
  override lazy val libWidth = 16

  compileExecuteAndTest(mem, lib, v, output)
}

// Try different widths against a base memory width of 8 but depth 512 instead of 1024.
class SplitWidth512x128_rw extends MacroCompilerSpec with HasSRAMGenerator with HasSimpleWidthTestGenerator {
  override lazy val depth = BigInt(512)
  override lazy val memWidth = 128
  override lazy val libWidth = 8

  compileExecuteAndTest(mem, lib, v, output)
}

class SplitWidth512x64_rw extends MacroCompilerSpec with HasSRAMGenerator with HasSimpleWidthTestGenerator {
  override lazy val depth = BigInt(512)
  override lazy val memWidth = 64
  override lazy val libWidth = 8

  compileExecuteAndTest(mem, lib, v, output)
}

class SplitWidth512x32_rw extends MacroCompilerSpec with HasSRAMGenerator with HasSimpleWidthTestGenerator {
  override lazy val depth = BigInt(512)
  override lazy val memWidth = 32
  override lazy val libWidth = 8

  compileExecuteAndTest(mem, lib, v, output)
}

class SplitWidth512x16_rw extends MacroCompilerSpec with HasSRAMGenerator with HasSimpleWidthTestGenerator {
  override lazy val depth = BigInt(512)
  override lazy val memWidth = 16
  override lazy val libWidth = 8

  compileExecuteAndTest(mem, lib, v, output)
}

class SplitWidth512x8_rw extends MacroCompilerSpec with HasSRAMGenerator with HasSimpleWidthTestGenerator {
  override lazy val depth = BigInt(512)
  override lazy val memWidth = 8
  override lazy val libWidth = 8

  compileExecuteAndTest(mem, lib, v, output)
}

// Try non-power of two widths against a base memory width of 8.
class SplitWidth1024x67_rw extends MacroCompilerSpec with HasSRAMGenerator with HasSimpleWidthTestGenerator {
  override lazy val depth = BigInt(1024)
  override lazy val memWidth = 67
  override lazy val libWidth = 8

  compileExecuteAndTest(mem, lib, v, output)
}

class SplitWidth1024x60_rw extends MacroCompilerSpec with HasSRAMGenerator with HasSimpleWidthTestGenerator {
  override lazy val depth = BigInt(1024)
  override lazy val memWidth = 60
  override lazy val libWidth = 8

  compileExecuteAndTest(mem, lib, v, output)
}

class SplitWidth1024x42_rw extends MacroCompilerSpec with HasSRAMGenerator with HasSimpleWidthTestGenerator {
  override lazy val depth = BigInt(1024)
  override lazy val memWidth = 42
  override lazy val libWidth = 8

  compileExecuteAndTest(mem, lib, v, output)
}

class SplitWidth1024x20_rw extends MacroCompilerSpec with HasSRAMGenerator with HasSimpleWidthTestGenerator {
  override lazy val depth = BigInt(1024)
  override lazy val memWidth = 20
  override lazy val libWidth = 8

  compileExecuteAndTest(mem, lib, v, output)
}

class SplitWidth1024x17_rw extends MacroCompilerSpec with HasSRAMGenerator with HasSimpleWidthTestGenerator {
  override lazy val depth = BigInt(1024)
  override lazy val memWidth = 17
  override lazy val libWidth = 8

  compileExecuteAndTest(mem, lib, v, output)
}

class SplitWidth1024x15_rw extends MacroCompilerSpec with HasSRAMGenerator with HasSimpleWidthTestGenerator {
  override lazy val depth = BigInt(1024)
  override lazy val memWidth = 15
  override lazy val libWidth = 8

  compileExecuteAndTest(mem, lib, v, output)
}

class SplitWidth1024x9_rw extends MacroCompilerSpec with HasSRAMGenerator with HasSimpleWidthTestGenerator {
  override lazy val depth = BigInt(1024)
  override lazy val memWidth = 9
  override lazy val libWidth = 8

  compileExecuteAndTest(mem, lib, v, output)
}

// Try against a non-power of two base memory width.
class SplitWidth1024x64_mem11_rw extends MacroCompilerSpec with HasSRAMGenerator with HasSimpleWidthTestGenerator {
  override lazy val depth = BigInt(1024)
  override lazy val memWidth = 64
  override lazy val libWidth = 11

  compileExecuteAndTest(mem, lib, v, output)
}

class SplitWidth1024x33_mem11_rw extends MacroCompilerSpec with HasSRAMGenerator with HasSimpleWidthTestGenerator {
  override lazy val depth = BigInt(1024)
  override lazy val memWidth = 33
  override lazy val libWidth = 11

  compileExecuteAndTest(mem, lib, v, output)
}

class SplitWidth1024x16_mem11_rw extends MacroCompilerSpec with HasSRAMGenerator with HasSimpleWidthTestGenerator {
  override lazy val depth = BigInt(1024)
  override lazy val memWidth = 16
  override lazy val libWidth = 11

  compileExecuteAndTest(mem, lib, v, output)
}

// Masked RAM

class SplitWidth1024x8_memGran_8_libGran_1_rw
    extends MacroCompilerSpec
    with HasSRAMGenerator
    with HasSimpleWidthTestGenerator {
  override lazy val depth = BigInt(1024)
  override lazy val memWidth = 8
  override lazy val libWidth = 8
  override lazy val memMaskGran: Option[Int] = Some(8)
  override lazy val libMaskGran: Option[Int] = Some(1)

  compileExecuteAndTest(mem, lib, v, output)
}

class SplitWidth1024x16_memGran_8_libGran_1_rw
    extends MacroCompilerSpec
    with HasSRAMGenerator
    with HasSimpleWidthTestGenerator {
  override lazy val depth = BigInt(1024)
  override lazy val memWidth = 16
  override lazy val libWidth = 8
  override lazy val memMaskGran: Option[Int] = Some(8)
  override lazy val libMaskGran: Option[Int] = Some(1)

  compileExecuteAndTest(mem, lib, v, output)
}

class SplitWidth1024x16_memGran_8_libGran_8_rw
    extends MacroCompilerSpec
    with HasSRAMGenerator
    with HasSimpleWidthTestGenerator {
  override lazy val depth = BigInt(1024)
  override lazy val memWidth = 16
  override lazy val libWidth = 8
  override lazy val memMaskGran: Option[Int] = Some(8)
  override lazy val libMaskGran: Option[Int] = Some(8)

  compileExecuteAndTest(mem, lib, v, output)
}

class SplitWidth1024x128_memGran_8_libGran_1_rw
    extends MacroCompilerSpec
    with HasSRAMGenerator
    with HasSimpleWidthTestGenerator {
  override lazy val depth = BigInt(1024)
  override lazy val memWidth = 128
  override lazy val libWidth = 32
  override lazy val memMaskGran: Option[Int] = Some(8)
  override lazy val libMaskGran: Option[Int] = Some(1)

  compileExecuteAndTest(mem, lib, v, output)
}

class SplitWidth1024x16_memGran_4_libGran_1_rw
    extends MacroCompilerSpec
    with HasSRAMGenerator
    with HasSimpleWidthTestGenerator {
  override lazy val depth = BigInt(1024)
  override lazy val memWidth = 16
  override lazy val libWidth = 8
  override lazy val memMaskGran: Option[Int] = Some(4)
  override lazy val libMaskGran: Option[Int] = Some(1)

  compileExecuteAndTest(mem, lib, v, output)
}

class SplitWidth1024x16_memGran_2_libGran_1_rw
    extends MacroCompilerSpec
    with HasSRAMGenerator
    with HasSimpleWidthTestGenerator {
  override lazy val depth = BigInt(1024)
  override lazy val memWidth = 16
  override lazy val libWidth = 8
  override lazy val memMaskGran: Option[Int] = Some(2)
  override lazy val libMaskGran: Option[Int] = Some(1)

  compileExecuteAndTest(mem, lib, v, output)
}

class SplitWidth1024x16_memGran_16_libGran_1_rw
    extends MacroCompilerSpec
    with HasSRAMGenerator
    with HasSimpleWidthTestGenerator {
  override lazy val depth = BigInt(1024)
  override lazy val memWidth = 16
  override lazy val libWidth = 8
  override lazy val memMaskGran: Option[Int] = Some(16)
  override lazy val libMaskGran: Option[Int] = Some(1)

  compileExecuteAndTest(mem, lib, v, output)
}

// Non-masked mem, masked lib

class SplitWidth1024x16_libGran_8_rw extends MacroCompilerSpec with HasSRAMGenerator with HasSimpleWidthTestGenerator {
  override lazy val depth = BigInt(1024)
  override lazy val memWidth = 16
  override lazy val libWidth = 8
  override lazy val libMaskGran: Option[Int] = Some(8)

  compileExecuteAndTest(mem, lib, v, output)
}

class SplitWidth1024x16_libGran_1_rw extends MacroCompilerSpec with HasSRAMGenerator with HasSimpleWidthTestGenerator {
  override lazy val depth = BigInt(1024)
  override lazy val memWidth = 16
  override lazy val libWidth = 8
  override lazy val libMaskGran: Option[Int] = Some(1)

  compileExecuteAndTest(mem, lib, v, output)
}

// Non-memMask and non-1 libMask

class SplitWidth1024x16_memGran_8_libGran_2_rw
    extends MacroCompilerSpec
    with HasSRAMGenerator
    with HasSimpleWidthTestGenerator {
  override lazy val depth = BigInt(1024)
  override lazy val memWidth = 16
  override lazy val libWidth = 8
  override lazy val memMaskGran: Option[Int] = Some(8)
  override lazy val libMaskGran: Option[Int] = Some(2)

  compileExecuteAndTest(mem, lib, v, output)
}

// Non-power of two memGran

class SplitWidth1024x16_memGran_9_libGran_1_rw
    extends MacroCompilerSpec
    with HasSRAMGenerator
    with HasSimpleWidthTestGenerator {
  override lazy val depth = BigInt(1024)
  override lazy val memWidth = 16
  override lazy val libWidth = 8
  override lazy val memMaskGran: Option[Int] = Some(9)
  override lazy val libMaskGran: Option[Int] = Some(1)

  (it should "be enabled when non-power of two masks are supported").is(pending)
  //~ compile(mem, lib, v, false)
  //~ execute(mem, lib, false, output)
}

// Read enable

class SplitWidth1024x32_readEnable_Lib
    extends MacroCompilerSpec
    with HasSRAMGenerator
    with HasSimpleWidthTestGenerator {
  import mdf.macrolib._

  override lazy val depth = BigInt(1024)
  override lazy val memWidth = 32
  override lazy val libWidth = 8

  override def generateLibSRAM(): SRAMMacro = {
    SRAMMacro(
      name = lib_name,
      width = libWidth,
      depth = libDepth,
      family = "1rw",
      ports = Seq(
        generateTestPort(
          "lib",
          Some(libWidth),
          Some(libDepth),
          maskGran = libMaskGran,
          write = true,
          writeEnable = true,
          read = true,
          readEnable = true
        )
      )
    )
  }

  override def generateBody() =
    """
    inst mem_0_0 of awesome_lib_mem
    inst mem_0_1 of awesome_lib_mem
    inst mem_0_2 of awesome_lib_mem
    inst mem_0_3 of awesome_lib_mem
    mem_0_0.lib_clk <= outer_clk
    mem_0_0.lib_addr <= outer_addr
    node outer_dout_0_0 = bits(mem_0_0.lib_dout, 7, 0)
    mem_0_0.lib_din <= bits(outer_din, 7, 0)
    mem_0_0.lib_read_en <= and(and(not(outer_write_en), UInt<1>("h1")), UInt<1>("h1"))
    mem_0_0.lib_write_en <= and(and(and(outer_write_en, UInt<1>("h1")), UInt<1>("h1")), UInt<1>("h1"))
    mem_0_1.lib_clk <= outer_clk
    mem_0_1.lib_addr <= outer_addr
    node outer_dout_0_1 = bits(mem_0_1.lib_dout, 7, 0)
    mem_0_1.lib_din <= bits(outer_din, 15, 8)
    mem_0_1.lib_read_en <= and(and(not(outer_write_en), UInt<1>("h1")), UInt<1>("h1"))
    mem_0_1.lib_write_en <= and(and(and(outer_write_en, UInt<1>("h1")), UInt<1>("h1")), UInt<1>("h1"))
    mem_0_2.lib_clk <= outer_clk
    mem_0_2.lib_addr <= outer_addr
    node outer_dout_0_2 = bits(mem_0_2.lib_dout, 7, 0)
    mem_0_2.lib_din <= bits(outer_din, 23, 16)
    mem_0_2.lib_read_en <= and(and(not(outer_write_en), UInt<1>("h1")), UInt<1>("h1"))
    mem_0_2.lib_write_en <= and(and(and(outer_write_en, UInt<1>("h1")), UInt<1>("h1")), UInt<1>("h1"))
    mem_0_3.lib_clk <= outer_clk
    mem_0_3.lib_addr <= outer_addr
    node outer_dout_0_3 = bits(mem_0_3.lib_dout, 7, 0)
    mem_0_3.lib_din <= bits(outer_din, 31, 24)
    mem_0_3.lib_read_en <= and(and(not(outer_write_en), UInt<1>("h1")), UInt<1>("h1"))
    mem_0_3.lib_write_en <= and(and(and(outer_write_en, UInt<1>("h1")), UInt<1>("h1")), UInt<1>("h1"))
    node outer_dout_0 = cat(outer_dout_0_3, cat(outer_dout_0_2, cat(outer_dout_0_1, outer_dout_0_0)))
    outer_dout <= mux(UInt<1>("h1"), outer_dout_0, UInt<32>("h0"))
"""

  compileExecuteAndTest(mem, lib, v, output)
}

class SplitWidth1024x32_readEnable_Mem
    extends MacroCompilerSpec
    with HasSRAMGenerator
    with HasSimpleWidthTestGenerator {
  import mdf.macrolib._

  override lazy val depth = BigInt(1024)
  override lazy val memWidth = 32
  override lazy val libWidth = 8

  override def generateMemSRAM(): SRAMMacro = {
    SRAMMacro(
      name = mem_name,
      width = memWidth,
      depth = memDepth,
      family = "1rw",
      ports = Seq(
        generateTestPort(
          "outer",
          Some(memWidth),
          Some(memDepth),
          maskGran = memMaskGran,
          write = true,
          writeEnable = true,
          read = true,
          readEnable = true
        )
      )
    )
  }

  // No need to override body here due to the lack of a readEnable in the lib.

  compileExecuteAndTest(mem, lib, v, output)
}

class SplitWidth1024x32_readEnable_LibMem
    extends MacroCompilerSpec
    with HasSRAMGenerator
    with HasSimpleWidthTestGenerator {
  import mdf.macrolib._

  override lazy val depth = BigInt(1024)
  override lazy val memWidth = 32
  override lazy val libWidth = 8

  override def generateLibSRAM(): SRAMMacro = {
    SRAMMacro(
      name = lib_name,
      width = libWidth,
      depth = libDepth,
      family = "1rw",
      ports = Seq(
        generateTestPort(
          "lib",
          Some(libWidth),
          Some(libDepth),
          maskGran = libMaskGran,
          write = true,
          writeEnable = true,
          read = true,
          readEnable = true
        )
      )
    )
  }

  override def generateMemSRAM(): SRAMMacro = {
    SRAMMacro(
      name = mem_name,
      width = memWidth,
      depth = memDepth,
      family = "1rw",
      ports = Seq(
        generateTestPort(
          "outer",
          Some(memWidth),
          Some(memDepth),
          maskGran = memMaskGran,
          write = true,
          writeEnable = true,
          read = true,
          readEnable = true
        )
      )
    )
  }

  override def generateBody() =
    """
    inst mem_0_0 of awesome_lib_mem
    inst mem_0_1 of awesome_lib_mem
    inst mem_0_2 of awesome_lib_mem
    inst mem_0_3 of awesome_lib_mem
    mem_0_0.lib_clk <= outer_clk
    mem_0_0.lib_addr <= outer_addr
    node outer_dout_0_0 = bits(mem_0_0.lib_dout, 7, 0)
    mem_0_0.lib_din <= bits(outer_din, 7, 0)
    mem_0_0.lib_read_en <= and(outer_read_en, UInt<1>("h1"))
    mem_0_0.lib_write_en <= and(and(and(outer_write_en, UInt<1>("h1")), UInt<1>("h1")), UInt<1>("h1"))
    mem_0_1.lib_clk <= outer_clk
    mem_0_1.lib_addr <= outer_addr
    node outer_dout_0_1 = bits(mem_0_1.lib_dout, 7, 0)
    mem_0_1.lib_din <= bits(outer_din, 15, 8)
    mem_0_1.lib_read_en <= and(outer_read_en, UInt<1>("h1"))
    mem_0_1.lib_write_en <= and(and(and(outer_write_en, UInt<1>("h1")), UInt<1>("h1")), UInt<1>("h1"))
    mem_0_2.lib_clk <= outer_clk
    mem_0_2.lib_addr <= outer_addr
    node outer_dout_0_2 = bits(mem_0_2.lib_dout, 7, 0)
    mem_0_2.lib_din <= bits(outer_din, 23, 16)
    mem_0_2.lib_read_en <= and(outer_read_en, UInt<1>("h1"))
    mem_0_2.lib_write_en <= and(and(and(outer_write_en, UInt<1>("h1")), UInt<1>("h1")), UInt<1>("h1"))
    mem_0_3.lib_clk <= outer_clk
    mem_0_3.lib_addr <= outer_addr
    node outer_dout_0_3 = bits(mem_0_3.lib_dout, 7, 0)
    mem_0_3.lib_din <= bits(outer_din, 31, 24)
    mem_0_3.lib_read_en <= and(outer_read_en, UInt<1>("h1"))
    mem_0_3.lib_write_en <= and(and(and(outer_write_en, UInt<1>("h1")), UInt<1>("h1")), UInt<1>("h1"))
    node outer_dout_0 = cat(outer_dout_0_3, cat(outer_dout_0_2, cat(outer_dout_0_1, outer_dout_0_0)))
    outer_dout <= mux(UInt<1>("h1"), outer_dout_0, UInt<32>("h0"))
"""

  compileExecuteAndTest(mem, lib, v, output)
}
