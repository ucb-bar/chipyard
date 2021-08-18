package barstools.macros

// Test that the memory compiler works fine for compiling multi-port memories.
// TODO: extend test generator to also automatically generate multi-ported memories.

class SplitWidth_2rw extends MacroCompilerSpec with HasSRAMGenerator with HasSimpleWidthTestGenerator {
  import mdf.macrolib._

  override lazy val depth = BigInt(1024)
  override lazy val memWidth = 64
  override lazy val memMaskGran: Option[Int] = Some(16)
  override lazy val libWidth = 16

  override def generateMemSRAM(): SRAMMacro = {
    SRAMMacro(
      name = mem_name,
      width = memWidth,
      depth = memDepth,
      family = "2rw",
      ports = Seq(
        generateTestPort(
          "portA",
          memWidth,
          Some(memDepth),
          maskGran = memMaskGran,
          write = true,
          writeEnable = true,
          read = true,
          readEnable = true
        ),
        generateTestPort(
          "portB",
          memWidth,
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

  override def generateLibSRAM(): SRAMMacro = {
    SRAMMacro(
      name = lib_name,
      width = libWidth,
      depth = libDepth,
      family = "2rw",
      ports = Seq(
        generateTestPort(
          "portA",
          libWidth,
          libDepth,
          write = true,
          writeEnable = true,
          read = true,
          readEnable = true
        ),
        generateTestPort(
          "portB",
          libWidth,
          libDepth,
          write = true,
          writeEnable = true,
          read = true,
          readEnable = true
        )
      )
    )
  }

  override def generateHeaderPorts(): String = {
    generateReadWriteHeaderPort("portA", readEnable = true, Some(memMaskBits)) + "\n" + generateReadWriteHeaderPort(
      "portB",
      readEnable = true,
      Some(memMaskBits)
    )
  }

  override def generateFooterPorts(): String = {
    generateReadWriteFooterPort("portA", readEnable = true, None) + "\n" + generateReadWriteFooterPort(
      "portB",
      readEnable = true,
      None
    )
  }

  override def generateBody() =
    """
    inst mem_0_0 of awesome_lib_mem
    inst mem_0_1 of awesome_lib_mem
    inst mem_0_2 of awesome_lib_mem
    inst mem_0_3 of awesome_lib_mem
    mem_0_0.portA_clk <= portA_clk
    mem_0_0.portA_addr <= portA_addr
    node portA_dout_0_0 = bits(mem_0_0.portA_dout, 15, 0)
    mem_0_0.portA_din <= bits(portA_din, 15, 0)
    mem_0_0.portA_read_en <= and(portA_read_en, UInt<1>("h1"))
    mem_0_0.portA_write_en <= and(and(and(portA_write_en, UInt<1>("h1")), bits(portA_mask, 0, 0)), UInt<1>("h1"))
    mem_0_1.portA_clk <= portA_clk
    mem_0_1.portA_addr <= portA_addr
    node portA_dout_0_1 = bits(mem_0_1.portA_dout, 15, 0)
    mem_0_1.portA_din <= bits(portA_din, 31, 16)
    mem_0_1.portA_read_en <= and(portA_read_en, UInt<1>("h1"))
    mem_0_1.portA_write_en <= and(and(and(portA_write_en, UInt<1>("h1")), bits(portA_mask, 1, 1)), UInt<1>("h1"))
    mem_0_2.portA_clk <= portA_clk
    mem_0_2.portA_addr <= portA_addr
    node portA_dout_0_2 = bits(mem_0_2.portA_dout, 15, 0)
    mem_0_2.portA_din <= bits(portA_din, 47, 32)
    mem_0_2.portA_read_en <= and(portA_read_en, UInt<1>("h1"))
    mem_0_2.portA_write_en <= and(and(and(portA_write_en, UInt<1>("h1")), bits(portA_mask, 2, 2)), UInt<1>("h1"))
    mem_0_3.portA_clk <= portA_clk
    mem_0_3.portA_addr <= portA_addr
    node portA_dout_0_3 = bits(mem_0_3.portA_dout, 15, 0)
    mem_0_3.portA_din <= bits(portA_din, 63, 48)
    mem_0_3.portA_read_en <= and(portA_read_en, UInt<1>("h1"))
    mem_0_3.portA_write_en <= and(and(and(portA_write_en, UInt<1>("h1")), bits(portA_mask, 3, 3)), UInt<1>("h1"))
    node portA_dout_0 = cat(portA_dout_0_3, cat(portA_dout_0_2, cat(portA_dout_0_1, portA_dout_0_0)))
    mem_0_0.portB_clk <= portB_clk
    mem_0_0.portB_addr <= portB_addr
    node portB_dout_0_0 = bits(mem_0_0.portB_dout, 15, 0)
    mem_0_0.portB_din <= bits(portB_din, 15, 0)
    mem_0_0.portB_read_en <= and(portB_read_en, UInt<1>("h1"))
    mem_0_0.portB_write_en <= and(and(and(portB_write_en, UInt<1>("h1")), bits(portB_mask, 0, 0)), UInt<1>("h1"))
    mem_0_1.portB_clk <= portB_clk
    mem_0_1.portB_addr <= portB_addr
    node portB_dout_0_1 = bits(mem_0_1.portB_dout, 15, 0)
    mem_0_1.portB_din <= bits(portB_din, 31, 16)
    mem_0_1.portB_read_en <= and(portB_read_en, UInt<1>("h1"))
    mem_0_1.portB_write_en <= and(and(and(portB_write_en, UInt<1>("h1")), bits(portB_mask, 1, 1)), UInt<1>("h1"))
    mem_0_2.portB_clk <= portB_clk
    mem_0_2.portB_addr <= portB_addr
    node portB_dout_0_2 = bits(mem_0_2.portB_dout, 15, 0)
    mem_0_2.portB_din <= bits(portB_din, 47, 32)
    mem_0_2.portB_read_en <= and(portB_read_en, UInt<1>("h1"))
    mem_0_2.portB_write_en <= and(and(and(portB_write_en, UInt<1>("h1")), bits(portB_mask, 2, 2)), UInt<1>("h1"))
    mem_0_3.portB_clk <= portB_clk
    mem_0_3.portB_addr <= portB_addr
    node portB_dout_0_3 = bits(mem_0_3.portB_dout, 15, 0)
    mem_0_3.portB_din <= bits(portB_din, 63, 48)
    mem_0_3.portB_read_en <= and(portB_read_en, UInt<1>("h1"))
    mem_0_3.portB_write_en <= and(and(and(portB_write_en, UInt<1>("h1")), bits(portB_mask, 3, 3)), UInt<1>("h1"))
    node portB_dout_0 = cat(portB_dout_0_3, cat(portB_dout_0_2, cat(portB_dout_0_1, portB_dout_0_0)))
    portA_dout <= mux(UInt<1>("h1"), portA_dout_0, UInt<64>("h0"))
    portB_dout <= mux(UInt<1>("h1"), portB_dout_0, UInt<64>("h0"))
"""

  compileExecuteAndTest(mem, lib, v, output)
}

class SplitWidth_1r_1w extends MacroCompilerSpec with HasSRAMGenerator with HasSimpleWidthTestGenerator {
  import mdf.macrolib._

  override lazy val depth = BigInt(1024)
  override lazy val memWidth = 64
  override lazy val memMaskGran: Option[Int] = Some(16)
  override lazy val libWidth = 16

  override def generateMemSRAM(): SRAMMacro = {
    SRAMMacro(
      name = mem_name,
      width = memWidth,
      depth = memDepth,
      family = "1r1w",
      ports = Seq(
        generateTestPort(
          "portA",
          memWidth,
          Some(memDepth),
          maskGran = memMaskGran,
          write = false,
          read = true,
          readEnable = true
        ),
        generateTestPort(
          "portB",
          memWidth,
          Some(memDepth),
          maskGran = memMaskGran,
          write = true,
          writeEnable = true,
          read = false
        )
      )
    )
  }

  override def generateLibSRAM(): SRAMMacro = {
    SRAMMacro(
      name = lib_name,
      width = libWidth,
      depth = libDepth,
      family = "1r1w",
      ports = Seq(
        generateTestPort(
          "portA",
          libWidth,
          libDepth,
          write = false,
          read = true,
          readEnable = true
        ),
        generateTestPort("portB", libWidth, libDepth, write = true, writeEnable = true, read = false)
      )
    )
  }

  override def generateHeaderPorts(): String = {
    generatePort(
      "portA",
      mem_addr_width,
      memWidth,
      write = false,
      writeEnable = false,
      read = true,
      readEnable = true,
      Some(memMaskBits)
    ) + "\n" +
      generatePort(
        "portB",
        mem_addr_width,
        memWidth,
        write = true,
        writeEnable = true,
        read = false,
        readEnable = false,
        Some(memMaskBits)
      )
  }

  override def generateFooterPorts(): String = {
    generatePort(
      "portA",
      lib_addr_width,
      libWidth,
      write = false,
      writeEnable = false,
      read = true,
      readEnable = true,
      None
    ) + "\n" +
      generatePort(
        "portB",
        lib_addr_width,
        libWidth,
        write = true,
        writeEnable = true,
        read = false,
        readEnable = false,
        None
      )
  }

  override def generateBody() =
    """
    inst mem_0_0 of awesome_lib_mem
    inst mem_0_1 of awesome_lib_mem
    inst mem_0_2 of awesome_lib_mem
    inst mem_0_3 of awesome_lib_mem
    mem_0_0.portB_clk <= portB_clk
    mem_0_0.portB_addr <= portB_addr
    mem_0_0.portB_din <= bits(portB_din, 15, 0)
    mem_0_0.portB_write_en <= and(and(and(portB_write_en, UInt<1>("h1")), bits(portB_mask, 0, 0)), UInt<1>("h1"))
    mem_0_1.portB_clk <= portB_clk
    mem_0_1.portB_addr <= portB_addr
    mem_0_1.portB_din <= bits(portB_din, 31, 16)
    mem_0_1.portB_write_en <= and(and(and(portB_write_en, UInt<1>("h1")), bits(portB_mask, 1, 1)), UInt<1>("h1"))
    mem_0_2.portB_clk <= portB_clk
    mem_0_2.portB_addr <= portB_addr
    mem_0_2.portB_din <= bits(portB_din, 47, 32)
    mem_0_2.portB_write_en <= and(and(and(portB_write_en, UInt<1>("h1")), bits(portB_mask, 2, 2)), UInt<1>("h1"))
    mem_0_3.portB_clk <= portB_clk
    mem_0_3.portB_addr <= portB_addr
    mem_0_3.portB_din <= bits(portB_din, 63, 48)
    mem_0_3.portB_write_en <= and(and(and(portB_write_en, UInt<1>("h1")), bits(portB_mask, 3, 3)), UInt<1>("h1"))
    mem_0_0.portA_clk <= portA_clk
    mem_0_0.portA_addr <= portA_addr
    node portA_dout_0_0 = bits(mem_0_0.portA_dout, 15, 0)
    mem_0_0.portA_read_en <= and(portA_read_en, UInt<1>("h1"))
    mem_0_1.portA_clk <= portA_clk
    mem_0_1.portA_addr <= portA_addr
    node portA_dout_0_1 = bits(mem_0_1.portA_dout, 15, 0)
    mem_0_1.portA_read_en <= and(portA_read_en, UInt<1>("h1"))
    mem_0_2.portA_clk <= portA_clk
    mem_0_2.portA_addr <= portA_addr
    node portA_dout_0_2 = bits(mem_0_2.portA_dout, 15, 0)
    mem_0_2.portA_read_en <= and(portA_read_en, UInt<1>("h1"))
    mem_0_3.portA_clk <= portA_clk
    mem_0_3.portA_addr <= portA_addr
    node portA_dout_0_3 = bits(mem_0_3.portA_dout, 15, 0)
    mem_0_3.portA_read_en <= and(portA_read_en, UInt<1>("h1"))
    node portA_dout_0 = cat(portA_dout_0_3, cat(portA_dout_0_2, cat(portA_dout_0_1, portA_dout_0_0)))
    portA_dout <= mux(UInt<1>("h1"), portA_dout_0, UInt<64>("h0"))
"""

  compileExecuteAndTest(mem, lib, v, output)
}

class SplitWidth_2rw_differentMasks extends MacroCompilerSpec with HasSRAMGenerator with HasSimpleWidthTestGenerator {
  import mdf.macrolib._

  override lazy val depth = BigInt(1024)
  override lazy val memWidth = 64
  override lazy val memMaskGran: Option[Int] = Some(16)
  override lazy val libWidth = 16

  lazy val memMaskGranB = 8 // these generators are run at constructor time

  override def generateMemSRAM(): SRAMMacro = {
    SRAMMacro(
      name = mem_name,
      width = memWidth,
      depth = memDepth,
      family = "2rw",
      ports = Seq(
        generateTestPort(
          "portA",
          memWidth,
          Some(memDepth),
          maskGran = memMaskGran,
          write = true,
          writeEnable = true,
          read = true,
          readEnable = true
        ),
        generateTestPort(
          "portB",
          memWidth,
          Some(memDepth),
          maskGran = Some(memMaskGranB),
          write = true,
          writeEnable = true,
          read = true,
          readEnable = true
        )
      )
    )
  }

  override def generateLibSRAM(): SRAMMacro = {
    SRAMMacro(
      name = lib_name,
      width = libWidth,
      depth = libDepth,
      family = "2rw",
      ports = Seq(
        generateTestPort(
          "portA",
          libWidth,
          libDepth,
          write = true,
          writeEnable = true,
          read = true,
          readEnable = true
        ),
        generateTestPort(
          "portB",
          libWidth,
          libDepth,
          write = true,
          writeEnable = true,
          read = true,
          readEnable = true
        )
      )
    )
  }

  override def generateHeaderPorts(): String = {
    generateReadWriteHeaderPort("portA", readEnable = true, Some(memMaskBits)) + "\n" + generateReadWriteHeaderPort(
      "portB",
      readEnable = true,
      Some(memWidth / memMaskGranB)
    )
  }

  override def generateFooterPorts(): String = {
    generateReadWriteFooterPort("portA", readEnable = true, None) + "\n" + generateReadWriteFooterPort(
      "portB",
      readEnable = true,
      None
    )
  }

  override def generateBody() =
    """
    inst mem_0_0 of awesome_lib_mem
    inst mem_0_1 of awesome_lib_mem
    inst mem_0_2 of awesome_lib_mem
    inst mem_0_3 of awesome_lib_mem
    inst mem_0_4 of awesome_lib_mem
    inst mem_0_5 of awesome_lib_mem
    inst mem_0_6 of awesome_lib_mem
    inst mem_0_7 of awesome_lib_mem
    mem_0_0.portA_clk <= portA_clk
    mem_0_0.portA_addr <= portA_addr
    node portA_dout_0_0 = bits(mem_0_0.portA_dout, 7, 0)
    mem_0_0.portA_din <= bits(portA_din, 7, 0)
    mem_0_0.portA_read_en <= and(portA_read_en, UInt<1>("h1"))
    mem_0_0.portA_write_en <= and(and(and(portA_write_en, UInt<1>("h1")), bits(portA_mask, 0, 0)), UInt<1>("h1"))
    mem_0_1.portA_clk <= portA_clk
    mem_0_1.portA_addr <= portA_addr
    node portA_dout_0_1 = bits(mem_0_1.portA_dout, 7, 0)
    mem_0_1.portA_din <= bits(portA_din, 15, 8)
    mem_0_1.portA_read_en <= and(portA_read_en, UInt<1>("h1"))
    mem_0_1.portA_write_en <= and(and(and(portA_write_en, UInt<1>("h1")), bits(portA_mask, 0, 0)), UInt<1>("h1"))
    mem_0_2.portA_clk <= portA_clk
    mem_0_2.portA_addr <= portA_addr
    node portA_dout_0_2 = bits(mem_0_2.portA_dout, 7, 0)
    mem_0_2.portA_din <= bits(portA_din, 23, 16)
    mem_0_2.portA_read_en <= and(portA_read_en, UInt<1>("h1"))
    mem_0_2.portA_write_en <= and(and(and(portA_write_en, UInt<1>("h1")), bits(portA_mask, 1, 1)), UInt<1>("h1"))
    mem_0_3.portA_clk <= portA_clk
    mem_0_3.portA_addr <= portA_addr
    node portA_dout_0_3 = bits(mem_0_3.portA_dout, 7, 0)
    mem_0_3.portA_din <= bits(portA_din, 31, 24)
    mem_0_3.portA_read_en <= and(portA_read_en, UInt<1>("h1"))
    mem_0_3.portA_write_en <= and(and(and(portA_write_en, UInt<1>("h1")), bits(portA_mask, 1, 1)), UInt<1>("h1"))
    mem_0_4.portA_clk <= portA_clk
    mem_0_4.portA_addr <= portA_addr
    node portA_dout_0_4 = bits(mem_0_4.portA_dout, 7, 0)
    mem_0_4.portA_din <= bits(portA_din, 39, 32)
    mem_0_4.portA_read_en <= and(portA_read_en, UInt<1>("h1"))
    mem_0_4.portA_write_en <= and(and(and(portA_write_en, UInt<1>("h1")), bits(portA_mask, 2, 2)), UInt<1>("h1"))
    mem_0_5.portA_clk <= portA_clk
    mem_0_5.portA_addr <= portA_addr
    node portA_dout_0_5 = bits(mem_0_5.portA_dout, 7, 0)
    mem_0_5.portA_din <= bits(portA_din, 47, 40)
    mem_0_5.portA_read_en <= and(portA_read_en, UInt<1>("h1"))
    mem_0_5.portA_write_en <= and(and(and(portA_write_en, UInt<1>("h1")), bits(portA_mask, 2, 2)), UInt<1>("h1"))
    mem_0_6.portA_clk <= portA_clk
    mem_0_6.portA_addr <= portA_addr
    node portA_dout_0_6 = bits(mem_0_6.portA_dout, 7, 0)
    mem_0_6.portA_din <= bits(portA_din, 55, 48)
    mem_0_6.portA_read_en <= and(portA_read_en, UInt<1>("h1"))
    mem_0_6.portA_write_en <= and(and(and(portA_write_en, UInt<1>("h1")), bits(portA_mask, 3, 3)), UInt<1>("h1"))
    mem_0_7.portA_clk <= portA_clk
    mem_0_7.portA_addr <= portA_addr
    node portA_dout_0_7 = bits(mem_0_7.portA_dout, 7, 0)
    mem_0_7.portA_din <= bits(portA_din, 63, 56)
    mem_0_7.portA_read_en <= and(portA_read_en, UInt<1>("h1"))
    mem_0_7.portA_write_en <= and(and(and(portA_write_en, UInt<1>("h1")), bits(portA_mask, 3, 3)), UInt<1>("h1"))
    node portA_dout_0 = cat(portA_dout_0_7, cat(portA_dout_0_6, cat(portA_dout_0_5, cat(portA_dout_0_4, cat(portA_dout_0_3, cat(portA_dout_0_2, cat(portA_dout_0_1, portA_dout_0_0)))))))
    mem_0_0.portB_clk <= portB_clk
    mem_0_0.portB_addr <= portB_addr
    node portB_dout_0_0 = bits(mem_0_0.portB_dout, 7, 0)
    mem_0_0.portB_din <= bits(portB_din, 7, 0)
    mem_0_0.portB_read_en <= and(portB_read_en, UInt<1>("h1"))
    mem_0_0.portB_write_en <= and(and(and(portB_write_en, UInt<1>("h1")), bits(portB_mask, 0, 0)), UInt<1>("h1"))
    mem_0_1.portB_clk <= portB_clk
    mem_0_1.portB_addr <= portB_addr
    node portB_dout_0_1 = bits(mem_0_1.portB_dout, 7, 0)
    mem_0_1.portB_din <= bits(portB_din, 15, 8)
    mem_0_1.portB_read_en <= and(portB_read_en, UInt<1>("h1"))
    mem_0_1.portB_write_en <= and(and(and(portB_write_en, UInt<1>("h1")), bits(portB_mask, 1, 1)), UInt<1>("h1"))
    mem_0_2.portB_clk <= portB_clk
    mem_0_2.portB_addr <= portB_addr
    node portB_dout_0_2 = bits(mem_0_2.portB_dout, 7, 0)
    mem_0_2.portB_din <= bits(portB_din, 23, 16)
    mem_0_2.portB_read_en <= and(portB_read_en, UInt<1>("h1"))
    mem_0_2.portB_write_en <= and(and(and(portB_write_en, UInt<1>("h1")), bits(portB_mask, 2, 2)), UInt<1>("h1"))
    mem_0_3.portB_clk <= portB_clk
    mem_0_3.portB_addr <= portB_addr
    node portB_dout_0_3 = bits(mem_0_3.portB_dout, 7, 0)
    mem_0_3.portB_din <= bits(portB_din, 31, 24)
    mem_0_3.portB_read_en <= and(portB_read_en, UInt<1>("h1"))
    mem_0_3.portB_write_en <= and(and(and(portB_write_en, UInt<1>("h1")), bits(portB_mask, 3, 3)), UInt<1>("h1"))
    mem_0_4.portB_clk <= portB_clk
    mem_0_4.portB_addr <= portB_addr
    node portB_dout_0_4 = bits(mem_0_4.portB_dout, 7, 0)
    mem_0_4.portB_din <= bits(portB_din, 39, 32)
    mem_0_4.portB_read_en <= and(portB_read_en, UInt<1>("h1"))
    mem_0_4.portB_write_en <= and(and(and(portB_write_en, UInt<1>("h1")), bits(portB_mask, 4, 4)), UInt<1>("h1"))
    mem_0_5.portB_clk <= portB_clk
    mem_0_5.portB_addr <= portB_addr
    node portB_dout_0_5 = bits(mem_0_5.portB_dout, 7, 0)
    mem_0_5.portB_din <= bits(portB_din, 47, 40)
    mem_0_5.portB_read_en <= and(portB_read_en, UInt<1>("h1"))
    mem_0_5.portB_write_en <= and(and(and(portB_write_en, UInt<1>("h1")), bits(portB_mask, 5, 5)), UInt<1>("h1"))
    mem_0_6.portB_clk <= portB_clk
    mem_0_6.portB_addr <= portB_addr
    node portB_dout_0_6 = bits(mem_0_6.portB_dout, 7, 0)
    mem_0_6.portB_din <= bits(portB_din, 55, 48)
    mem_0_6.portB_read_en <= and(portB_read_en, UInt<1>("h1"))
    mem_0_6.portB_write_en <= and(and(and(portB_write_en, UInt<1>("h1")), bits(portB_mask, 6, 6)), UInt<1>("h1"))
    mem_0_7.portB_clk <= portB_clk
    mem_0_7.portB_addr <= portB_addr
    node portB_dout_0_7 = bits(mem_0_7.portB_dout, 7, 0)
    mem_0_7.portB_din <= bits(portB_din, 63, 56)
    mem_0_7.portB_read_en <= and(portB_read_en, UInt<1>("h1"))
    mem_0_7.portB_write_en <= and(and(and(portB_write_en, UInt<1>("h1")), bits(portB_mask, 7, 7)), UInt<1>("h1"))
    node portB_dout_0 = cat(portB_dout_0_7, cat(portB_dout_0_6, cat(portB_dout_0_5, cat(portB_dout_0_4, cat(portB_dout_0_3, cat(portB_dout_0_2, cat(portB_dout_0_1, portB_dout_0_0)))))))
    portA_dout <= mux(UInt<1>("h1"), portA_dout_0, UInt<64>("h0"))
    portB_dout <= mux(UInt<1>("h1"), portB_dout_0, UInt<64>("h0"))
"""

  compileExecuteAndTest(mem, lib, v, output)
}
