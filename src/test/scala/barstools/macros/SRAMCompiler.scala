package barstools.macros

import mdf.macrolib

class SRAMCompiler extends MacroCompilerSpec with HasSRAMGenerator with HasSimpleWidthTestGenerator {
  val compiler: macrolib.SRAMCompiler = generateSRAMCompiler("awesome", "A")
  val verilog = s"v-SRAMCompiler.v"
  override lazy val depth = BigInt(16)
  override lazy val memWidth = 8
  override lazy val libWidth = 8
  override lazy val mem_name = "mymem"
  override lazy val memPortPrefix = "X"
  override lazy val lib_name = "mygroup_8x16_SVT"
  override lazy val libPortPrefix = "A"

  writeToLib(lib, Seq(compiler))

  writeToMem(mem, Seq(generateSRAM("mymem", "X", 8, 16)))

  compileExecuteAndTest(mem, Some(lib), verilog, output = output, useCompiler = true)
}
