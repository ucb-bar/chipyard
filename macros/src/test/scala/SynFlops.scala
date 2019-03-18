package barstools.macros

// Test flop synthesis of the memory compiler.

trait HasSynFlopsTestGenerator extends HasSimpleTestGenerator {
  this: MacroCompilerSpec with HasSRAMGenerator =>
    def generateFlops: String = {
s"""
    mem ram :
      data-type => UInt<${libWidth}>
      depth => ${libDepth}
      read-latency => 1
      write-latency => 1
      readwriter => RW_0
      read-under-write => undefined
    ram.RW_0.clk <= ${libPortPrefix}_clk
    ram.RW_0.addr <= ${libPortPrefix}_addr
    ram.RW_0.en <= UInt<1>("h1")
    ram.RW_0.wmode <= ${libPortPrefix}_write_en
    ${libPortPrefix}_dout <= ram.RW_0.rdata
    ram.RW_0.wdata <= ${libPortPrefix}_din
    ram.RW_0.wmask <= UInt<1>("h1")
"""
    }

    // If there is no lib, put the flops definition into the body.
    abstract override def generateBody = {
      if (this.isInstanceOf[HasNoLibTestGenerator]) generateFlops else super.generateBody
    }

    // If there is no lib, don't generate a footer, since the flops definition
    // will be in the body.
    override def generateFooter = {
      if (this.isInstanceOf[HasNoLibTestGenerator]) "" else
s"""
  module ${lib_name} :
${generateFooterPorts}

${generateFlops}
"""
    }

}

class Synflops2048x8_noLib extends MacroCompilerSpec with HasSRAMGenerator with HasNoLibTestGenerator with HasSynFlopsTestGenerator {
  override lazy val memDepth = 2048
  override lazy val memWidth = 8

  compileExecuteAndTest(mem, None, v, output, true)
}

class Synflops2048x16_noLib extends MacroCompilerSpec with HasSRAMGenerator with HasNoLibTestGenerator with HasSynFlopsTestGenerator {
  override lazy val memDepth = 2048
  override lazy val memWidth = 16

  compileExecuteAndTest(mem, None, v, output, true)
}

class Synflops8192x16_noLib extends MacroCompilerSpec with HasSRAMGenerator with HasNoLibTestGenerator with HasSynFlopsTestGenerator {
  override lazy val memDepth = 8192
  override lazy val memWidth = 16

  compileExecuteAndTest(mem, None, v, output, true)
}

class Synflops2048x16_depth_Lib extends MacroCompilerSpec with HasSRAMGenerator with HasSimpleDepthTestGenerator with HasSynFlopsTestGenerator {
  override lazy val memDepth = 2048
  override lazy val libDepth = 1024
  override lazy val width = 16

  compileExecuteAndTest(mem, lib, v, output, true)
}

class Synflops2048x64_width_Lib extends MacroCompilerSpec with HasSRAMGenerator with HasSimpleWidthTestGenerator with HasSynFlopsTestGenerator {
  override lazy val memWidth = 64
  override lazy val libWidth = 8
  override lazy val depth = 1024

  compileExecuteAndTest(mem, lib, v, output, true)
}

class Synflops_SplitPorts_Read_Write extends MacroCompilerSpec with HasSRAMGenerator with HasSimpleDepthTestGenerator with HasSynFlopsTestGenerator {
  import mdf.macrolib._

  override lazy val memDepth = 2048
  override lazy val libDepth = 1024
  override lazy val width = 8

  override def generateLibSRAM = SRAMMacro(
    name=lib_name,
    width=width,
    depth=libDepth,
    family="1r1w",
    ports=Seq(
      generateReadPort("innerA", width, libDepth),
      generateWritePort("innerB", width, libDepth)
    )
  )

  override def generateMemSRAM = SRAMMacro(
    name=mem_name,
    width=width,
    depth=memDepth,
    family="1r1w",
    ports=Seq(
      generateReadPort("outerB", width, memDepth),
      generateWritePort("outerA", width, memDepth)
    )
  )

  override def generateHeader =
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
"""

  override def generateBody =
"""
    node outerB_addr_sel = bits(outerB_addr, 10, 10)
    reg outerB_addr_sel_reg : UInt<1>, outerB_clk with :
      reset => (UInt<1>("h0"), outerB_addr_sel_reg)
    outerB_addr_sel_reg <= mux(UInt<1>("h1"), outerB_addr_sel, outerB_addr_sel_reg)
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
    outerB_dout <= mux(eq(outerB_addr_sel_reg, UInt<1>("h0")), outerB_dout_0, mux(eq(outerB_addr_sel_reg, UInt<1>("h1")), outerB_dout_1, UInt<1>("h0")))
"""

  override def generateFooterPorts =
"""
    input innerA_addr : UInt<10>
    input innerA_clk : Clock
    output innerA_dout : UInt<8>
    input innerB_addr : UInt<10>
    input innerB_clk : Clock
    input innerB_din : UInt<8>
    input innerB_write_en : UInt<1>
"""

  override def generateFlops =
"""
    mem ram :
      data-type => UInt<8>
      depth => 1024
      read-latency => 1
      write-latency => 1
      reader => R_0
      writer => W_0
      read-under-write => undefined
    ram.R_0.clk <= innerA_clk
    ram.R_0.addr <= innerA_addr
    ram.R_0.en <= UInt<1>("h1")
    innerA_dout <= ram.R_0.data
    ram.W_0.clk <= innerB_clk
    ram.W_0.addr <= innerB_addr
    ram.W_0.en <= innerB_write_en
    ram.W_0.data <= innerB_din
    ram.W_0.mask <= UInt<1>("h1")
"""

  "Non-masked split lib; split mem" should "syn flops fine" in {
    compileExecuteAndTest(mem, lib, v, output, true)
  }
}

class Synflops_SplitPorts_MaskedMem_Read_MaskedWrite extends MacroCompilerSpec with HasSRAMGenerator with HasSimpleDepthTestGenerator with HasSynFlopsTestGenerator {
  import mdf.macrolib._

  override lazy val memDepth = 2048
  override lazy val libDepth = 1024
  override lazy val width = 8
  override lazy val memMaskGran = Some(8)
  override lazy val libMaskGran = Some(1)

  override def generateLibSRAM = SRAMMacro(
    name=lib_name,
    width=width,
    depth=libDepth,
    family="1r1w",
    ports=Seq(
      generateReadPort("innerA", width, libDepth),
      generateWritePort("innerB", width, libDepth, libMaskGran)
    )
  )

  override def generateMemSRAM = SRAMMacro(
    name=mem_name,
    width=width,
    depth=memDepth,
    family="1r1w",
    ports=Seq(
      generateReadPort("outerB", width, memDepth),
      generateWritePort("outerA", width, memDepth, memMaskGran)
    )
  )

  override def generateHeader =
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
"""

  override def generateBody =
"""
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
    outerB_dout <= mux(eq(outerB_addr_sel_reg, UInt<1>("h0")), outerB_dout_0, mux(eq(outerB_addr_sel_reg, UInt<1>("h1")), outerB_dout_1, UInt<1>("h0")))
"""

  override def generateFooterPorts =
"""
    input innerA_addr : UInt<10>
    input innerA_clk : Clock
    output innerA_dout : UInt<8>
    input innerB_addr : UInt<10>
    input innerB_clk : Clock
    input innerB_din : UInt<8>
    input innerB_write_en : UInt<1>
    input innerB_mask : UInt<8>
"""

  override def generateFlops =
"""
    mem ram :
      data-type => UInt<1>[8]
      depth => 1024
      read-latency => 1
      write-latency => 1
      reader => R_0
      writer => W_0
      read-under-write => undefined
    ram.R_0.clk <= innerA_clk
    ram.R_0.addr <= innerA_addr
    ram.R_0.en <= UInt<1>("h1")
    innerA_dout <= cat(ram.R_0.data[7], cat(ram.R_0.data[6], cat(ram.R_0.data[5], cat(ram.R_0.data[4], cat(ram.R_0.data[3], cat(ram.R_0.data[2], cat(ram.R_0.data[1], ram.R_0.data[0])))))))
    ram.W_0.clk <= innerB_clk
    ram.W_0.addr <= innerB_addr
    ram.W_0.en <= innerB_write_en
    ram.W_0.data[0] <= bits(innerB_din, 0, 0)
    ram.W_0.data[1] <= bits(innerB_din, 1, 1)
    ram.W_0.data[2] <= bits(innerB_din, 2, 2)
    ram.W_0.data[3] <= bits(innerB_din, 3, 3)
    ram.W_0.data[4] <= bits(innerB_din, 4, 4)
    ram.W_0.data[5] <= bits(innerB_din, 5, 5)
    ram.W_0.data[6] <= bits(innerB_din, 6, 6)
    ram.W_0.data[7] <= bits(innerB_din, 7, 7)
    ram.W_0.mask[0] <= bits(innerB_mask, 0, 0)
    ram.W_0.mask[1] <= bits(innerB_mask, 1, 1)
    ram.W_0.mask[2] <= bits(innerB_mask, 2, 2)
    ram.W_0.mask[3] <= bits(innerB_mask, 3, 3)
    ram.W_0.mask[4] <= bits(innerB_mask, 4, 4)
    ram.W_0.mask[5] <= bits(innerB_mask, 5, 5)
    ram.W_0.mask[6] <= bits(innerB_mask, 6, 6)
    ram.W_0.mask[7] <= bits(innerB_mask, 7, 7)
"""

  "masked split lib; masked split mem" should "syn flops fine" in {
    compileExecuteAndTest(mem, lib, v, output, true)
  }
}
