package barstools.tapeout.transforms.macros

import firrtl._
import firrtl.ir.{Circuit, NoInfo}
import firrtl.passes.RemoveEmpty
import firrtl.Parser.parse
import java.io.{File, StringWriter}
import Utils.readJSON

abstract class MacroCompilerSpec extends org.scalatest.FlatSpec with org.scalatest.Matchers {
  val macroDir = new File("tapeout/src/test/resources/macros")
  val testDir = new File("test_run_dir/macros") ; testDir.mkdirs

  def args(mem: File, lib: Option[File], v: File, synflops: Boolean) =
    List("-m", mem.toString, "-v", v.toString) ++
    (lib match { case None => Nil case Some(l) => List("-l", l.toString) }) ++
    (if (synflops) List("--syn-flops") else Nil)

  def compile(mem: File, lib: Option[File], v: File, synflops: Boolean) {
    MacroCompiler.run(args(mem, lib, v, synflops))
  }

  def execute(memFile: Option[File], libFile: Option[File], synflops: Boolean, output: String) {
    require(memFile.isDefined)
    val mems = readJSON(memFile) map (_ map (x => new Macro(x)))
    val libs = readJSON(libFile) map (_ map (x => new Macro(x)))
    val macros = mems.get map (_.blackbox)
    val circuit = Circuit(NoInfo, macros, macros.last.name)
    val passes = Seq(
      new MacroCompilerPass(mems, libs),
      new SynFlopsPass(synflops, libs getOrElse mems.get),
      RemoveEmpty)
    val result = (passes foldLeft circuit)((c, pass) => pass run c)
    val gold = RemoveEmpty run parse(output)
    (result.serialize) should be (gold.serialize)
  }
}

class RocketChipTest extends MacroCompilerSpec {
  val mem = new File(macroDir, "rocketchip.json")
  val lib = new File(macroDir, "mylib.json")
  val v = new File(testDir, "rocketchip.macro.v")
  val output = // TODO: check correctness...
"""
circuit T_2172_ext :
  module tag_array_ext :
    input RW0_clk : Clock
    input RW0_addr : UInt<6>
    input RW0_wdata : UInt<80>
    output RW0_rdata : UInt<80>
    input RW0_en : UInt<1>
    input RW0_wmode : UInt<1>
    input RW0_wmask : UInt<4>

    inst mem_0_0 of SRAM1RW64x32
    inst mem_0_1 of SRAM1RW64x32
    inst mem_0_2 of SRAM1RW64x32
    inst mem_0_3 of SRAM1RW64x32
    mem_0_0.CE <= RW0_clk
    mem_0_0.A <= RW0_addr
    node RW0_rdata_0_0 = bits(mem_0_0.O, 19, 0)
    mem_0_0.I <= bits(RW0_wdata, 19, 0)
    mem_0_0.OEB <= not(and(not(RW0_wmode), UInt<1>("h1")))
    mem_0_0.WEB <= not(and(and(RW0_wmode, bits(RW0_wmask, 0, 0)), UInt<1>("h1")))
    mem_0_0.CEB <= not(and(RW0_en, UInt<1>("h1")))
    mem_0_1.CE <= RW0_clk
    mem_0_1.A <= RW0_addr
    node RW0_rdata_0_1 = bits(mem_0_1.O, 19, 0)
    mem_0_1.I <= bits(RW0_wdata, 39, 20)
    mem_0_1.OEB <= not(and(not(RW0_wmode), UInt<1>("h1")))
    mem_0_1.WEB <= not(and(and(RW0_wmode, bits(RW0_wmask, 1, 1)), UInt<1>("h1")))
    mem_0_1.CEB <= not(and(RW0_en, UInt<1>("h1")))
    mem_0_2.CE <= RW0_clk
    mem_0_2.A <= RW0_addr
    node RW0_rdata_0_2 = bits(mem_0_2.O, 19, 0)
    mem_0_2.I <= bits(RW0_wdata, 59, 40)
    mem_0_2.OEB <= not(and(not(RW0_wmode), UInt<1>("h1")))
    mem_0_2.WEB <= not(and(and(RW0_wmode, bits(RW0_wmask, 2, 2)), UInt<1>("h1")))
    mem_0_2.CEB <= not(and(RW0_en, UInt<1>("h1")))
    mem_0_3.CE <= RW0_clk
    mem_0_3.A <= RW0_addr
    node RW0_rdata_0_3 = bits(mem_0_3.O, 19, 0)
    mem_0_3.I <= bits(RW0_wdata, 79, 60)
    mem_0_3.OEB <= not(and(not(RW0_wmode), UInt<1>("h1")))
    mem_0_3.WEB <= not(and(and(RW0_wmode, bits(RW0_wmask, 3, 3)), UInt<1>("h1")))
    mem_0_3.CEB <= not(and(RW0_en, UInt<1>("h1")))
    node RW0_rdata_0 = cat(RW0_rdata_0_3, cat(RW0_rdata_0_2, cat(RW0_rdata_0_1, RW0_rdata_0_0)))
    RW0_rdata <= mux(UInt<1>("h1"), RW0_rdata_0, UInt<1>("h0"))

  extmodule SRAM1RW64x32 :
    input CE : Clock
    input A : UInt<6>
    input I : UInt<32>
    output O : UInt<32>
    input CEB : UInt<1>
    input OEB : UInt<1>
    input WEB : UInt<1>

    defname = SRAM1RW64x32


  module T_1090_ext :
    input RW0_clk : Clock
    input RW0_addr : UInt<9>
    input RW0_wdata : UInt<64>
    output RW0_rdata : UInt<64>
    input RW0_en : UInt<1>
    input RW0_wmode : UInt<1>

    inst mem_0_0 of SRAM1RW512x32
    inst mem_0_1 of SRAM1RW512x32
    mem_0_0.CE <= RW0_clk
    mem_0_0.A <= RW0_addr
    node RW0_rdata_0_0 = bits(mem_0_0.O, 31, 0)
    mem_0_0.I <= bits(RW0_wdata, 31, 0)
    mem_0_0.OEB <= not(and(not(RW0_wmode), UInt<1>("h1")))
    mem_0_0.WEB <= not(and(and(RW0_wmode, UInt<1>("h1")), UInt<1>("h1")))
    mem_0_0.CEB <= not(and(RW0_en, UInt<1>("h1")))
    mem_0_1.CE <= RW0_clk
    mem_0_1.A <= RW0_addr
    node RW0_rdata_0_1 = bits(mem_0_1.O, 31, 0)
    mem_0_1.I <= bits(RW0_wdata, 63, 32)
    mem_0_1.OEB <= not(and(not(RW0_wmode), UInt<1>("h1")))
    mem_0_1.WEB <= not(and(and(RW0_wmode, UInt<1>("h1")), UInt<1>("h1")))
    mem_0_1.CEB <= not(and(RW0_en, UInt<1>("h1")))
    node RW0_rdata_0 = cat(RW0_rdata_0_1, RW0_rdata_0_0)
    RW0_rdata <= mux(UInt<1>("h1"), RW0_rdata_0, UInt<1>("h0"))

  module T_406_ext :
    input RW0_clk : Clock
    input RW0_addr : UInt<9>
    input RW0_wdata : UInt<64>
    output RW0_rdata : UInt<64>
    input RW0_en : UInt<1>
    input RW0_wmode : UInt<1>
    input RW0_wmask : UInt<8>

    inst mem_0_0 of SRAM1RW512x32
    inst mem_0_1 of SRAM1RW512x32
    inst mem_0_2 of SRAM1RW512x32
    inst mem_0_3 of SRAM1RW512x32
    inst mem_0_4 of SRAM1RW512x32
    inst mem_0_5 of SRAM1RW512x32
    inst mem_0_6 of SRAM1RW512x32
    inst mem_0_7 of SRAM1RW512x32
    mem_0_0.CE <= RW0_clk
    mem_0_0.A <= RW0_addr
    node RW0_rdata_0_0 = bits(mem_0_0.O, 7, 0)
    mem_0_0.I <= bits(RW0_wdata, 7, 0)
    mem_0_0.OEB <= not(and(not(RW0_wmode), UInt<1>("h1")))
    mem_0_0.WEB <= not(and(and(RW0_wmode, bits(RW0_wmask, 0, 0)), UInt<1>("h1")))
    mem_0_0.CEB <= not(and(RW0_en, UInt<1>("h1")))
    mem_0_1.CE <= RW0_clk
    mem_0_1.A <= RW0_addr
    node RW0_rdata_0_1 = bits(mem_0_1.O, 7, 0)
    mem_0_1.I <= bits(RW0_wdata, 15, 8)
    mem_0_1.OEB <= not(and(not(RW0_wmode), UInt<1>("h1")))
    mem_0_1.WEB <= not(and(and(RW0_wmode, bits(RW0_wmask, 1, 1)), UInt<1>("h1")))
    mem_0_1.CEB <= not(and(RW0_en, UInt<1>("h1")))
    mem_0_2.CE <= RW0_clk
    mem_0_2.A <= RW0_addr
    node RW0_rdata_0_2 = bits(mem_0_2.O, 7, 0)
    mem_0_2.I <= bits(RW0_wdata, 23, 16)
    mem_0_2.OEB <= not(and(not(RW0_wmode), UInt<1>("h1")))
    mem_0_2.WEB <= not(and(and(RW0_wmode, bits(RW0_wmask, 2, 2)), UInt<1>("h1")))
    mem_0_2.CEB <= not(and(RW0_en, UInt<1>("h1")))
    mem_0_3.CE <= RW0_clk
    mem_0_3.A <= RW0_addr
    node RW0_rdata_0_3 = bits(mem_0_3.O, 7, 0)
    mem_0_3.I <= bits(RW0_wdata, 31, 24)
    mem_0_3.OEB <= not(and(not(RW0_wmode), UInt<1>("h1")))
    mem_0_3.WEB <= not(and(and(RW0_wmode, bits(RW0_wmask, 3, 3)), UInt<1>("h1")))
    mem_0_3.CEB <= not(and(RW0_en, UInt<1>("h1")))
    mem_0_4.CE <= RW0_clk
    mem_0_4.A <= RW0_addr
    node RW0_rdata_0_4 = bits(mem_0_4.O, 7, 0)
    mem_0_4.I <= bits(RW0_wdata, 39, 32)
    mem_0_4.OEB <= not(and(not(RW0_wmode), UInt<1>("h1")))
    mem_0_4.WEB <= not(and(and(RW0_wmode, bits(RW0_wmask, 4, 4)), UInt<1>("h1")))
    mem_0_4.CEB <= not(and(RW0_en, UInt<1>("h1")))
    mem_0_5.CE <= RW0_clk
    mem_0_5.A <= RW0_addr
    node RW0_rdata_0_5 = bits(mem_0_5.O, 7, 0)
    mem_0_5.I <= bits(RW0_wdata, 47, 40)
    mem_0_5.OEB <= not(and(not(RW0_wmode), UInt<1>("h1")))
    mem_0_5.WEB <= not(and(and(RW0_wmode, bits(RW0_wmask, 5, 5)), UInt<1>("h1")))
    mem_0_5.CEB <= not(and(RW0_en, UInt<1>("h1")))
    mem_0_6.CE <= RW0_clk
    mem_0_6.A <= RW0_addr
    node RW0_rdata_0_6 = bits(mem_0_6.O, 7, 0)
    mem_0_6.I <= bits(RW0_wdata, 55, 48)
    mem_0_6.OEB <= not(and(not(RW0_wmode), UInt<1>("h1")))
    mem_0_6.WEB <= not(and(and(RW0_wmode, bits(RW0_wmask, 6, 6)), UInt<1>("h1")))
    mem_0_6.CEB <= not(and(RW0_en, UInt<1>("h1")))
    mem_0_7.CE <= RW0_clk
    mem_0_7.A <= RW0_addr
    node RW0_rdata_0_7 = bits(mem_0_7.O, 7, 0)
    mem_0_7.I <= bits(RW0_wdata, 63, 56)
    mem_0_7.OEB <= not(and(not(RW0_wmode), UInt<1>("h1")))
    mem_0_7.WEB <= not(and(and(RW0_wmode, bits(RW0_wmask, 7, 7)), UInt<1>("h1")))
    mem_0_7.CEB <= not(and(RW0_en, UInt<1>("h1")))
    node RW0_rdata_0 = cat(RW0_rdata_0_7, cat(RW0_rdata_0_6, cat(RW0_rdata_0_5, cat(RW0_rdata_0_4, cat(RW0_rdata_0_3, cat(RW0_rdata_0_2, cat(RW0_rdata_0_1, RW0_rdata_0_0)))))))
    RW0_rdata <= mux(UInt<1>("h1"), RW0_rdata_0, UInt<1>("h0"))

  extmodule SRAM1RW512x32 :
    input CE : Clock
    input A : UInt<9>
    input I : UInt<32>
    output O : UInt<32>
    input CEB : UInt<1>
    input OEB : UInt<1>
    input WEB : UInt<1>

    defname = SRAM1RW512x32


  module T_2172_ext :
    input W0_clk : Clock
    input W0_addr : UInt<6>
    input W0_data : UInt<88>
    input W0_en : UInt<1>
    input W0_mask : UInt<4>
    input R0_clk : Clock
    input R0_addr : UInt<6>
    output R0_data : UInt<88>
    input R0_en : UInt<1>

    inst mem_0_0 of SRAM2RW64x32
    inst mem_0_1 of SRAM2RW64x32
    inst mem_0_2 of SRAM2RW64x32
    inst mem_0_3 of SRAM2RW64x32
    mem_0_0.CE1 <= W0_clk
    mem_0_0.A1 <= W0_addr
    mem_0_0.I1 <= bits(W0_data, 21, 0)
    mem_0_0.OEB1 <= not(and(not(UInt<1>("h1")), UInt<1>("h1")))
    mem_0_0.WEB1 <= not(and(and(UInt<1>("h1"), bits(W0_mask, 0, 0)), UInt<1>("h1")))
    mem_0_0.CEB1 <= not(and(W0_en, UInt<1>("h1")))
    mem_0_1.CE1 <= W0_clk
    mem_0_1.A1 <= W0_addr
    mem_0_1.I1 <= bits(W0_data, 43, 22)
    mem_0_1.OEB1 <= not(and(not(UInt<1>("h1")), UInt<1>("h1")))
    mem_0_1.WEB1 <= not(and(and(UInt<1>("h1"), bits(W0_mask, 1, 1)), UInt<1>("h1")))
    mem_0_1.CEB1 <= not(and(W0_en, UInt<1>("h1")))
    mem_0_2.CE1 <= W0_clk
    mem_0_2.A1 <= W0_addr
    mem_0_2.I1 <= bits(W0_data, 65, 44)
    mem_0_2.OEB1 <= not(and(not(UInt<1>("h1")), UInt<1>("h1")))
    mem_0_2.WEB1 <= not(and(and(UInt<1>("h1"), bits(W0_mask, 2, 2)), UInt<1>("h1")))
    mem_0_2.CEB1 <= not(and(W0_en, UInt<1>("h1")))
    mem_0_3.CE1 <= W0_clk
    mem_0_3.A1 <= W0_addr
    mem_0_3.I1 <= bits(W0_data, 87, 66)
    mem_0_3.OEB1 <= not(and(not(UInt<1>("h1")), UInt<1>("h1")))
    mem_0_3.WEB1 <= not(and(and(UInt<1>("h1"), bits(W0_mask, 3, 3)), UInt<1>("h1")))
    mem_0_3.CEB1 <= not(and(W0_en, UInt<1>("h1")))
    mem_0_0.CE2 <= R0_clk
    mem_0_0.A2 <= R0_addr
    node R0_data_0_0 = bits(mem_0_0.O2, 21, 0)
    mem_0_0.OEB2 <= not(and(not(UInt<1>("h0")), UInt<1>("h1")))
    mem_0_0.WEB2 <= not(and(and(UInt<1>("h0"), UInt<1>("h1")), UInt<1>("h1")))
    mem_0_0.CEB2 <= not(and(R0_en, UInt<1>("h1")))
    mem_0_1.CE2 <= R0_clk
    mem_0_1.A2 <= R0_addr
    node R0_data_0_1 = bits(mem_0_1.O2, 21, 0)
    mem_0_1.OEB2 <= not(and(not(UInt<1>("h0")), UInt<1>("h1")))
    mem_0_1.WEB2 <= not(and(and(UInt<1>("h0"), UInt<1>("h1")), UInt<1>("h1")))
    mem_0_1.CEB2 <= not(and(R0_en, UInt<1>("h1")))
    mem_0_2.CE2 <= R0_clk
    mem_0_2.A2 <= R0_addr
    node R0_data_0_2 = bits(mem_0_2.O2, 21, 0)
    mem_0_2.OEB2 <= not(and(not(UInt<1>("h0")), UInt<1>("h1")))
    mem_0_2.WEB2 <= not(and(and(UInt<1>("h0"), UInt<1>("h1")), UInt<1>("h1")))
    mem_0_2.CEB2 <= not(and(R0_en, UInt<1>("h1")))
    mem_0_3.CE2 <= R0_clk
    mem_0_3.A2 <= R0_addr
    node R0_data_0_3 = bits(mem_0_3.O2, 21, 0)
    mem_0_3.OEB2 <= not(and(not(UInt<1>("h0")), UInt<1>("h1")))
    mem_0_3.WEB2 <= not(and(and(UInt<1>("h0"), UInt<1>("h1")), UInt<1>("h1")))
    mem_0_3.CEB2 <= not(and(R0_en, UInt<1>("h1")))
    node R0_data_0 = cat(R0_data_0_3, cat(R0_data_0_2, cat(R0_data_0_1, R0_data_0_0)))
    R0_data <= mux(UInt<1>("h1"), R0_data_0, UInt<1>("h0"))

  extmodule SRAM2RW64x32 :
    input CE1 : Clock
    input A1 : UInt<6>
    input I1 : UInt<32>
    output O1 : UInt<32>
    input CEB1 : UInt<1>
    input OEB1 : UInt<1>
    input WEB1 : UInt<1>
    input CE2 : Clock
    input A2 : UInt<6>
    input I2 : UInt<32>
    output O2 : UInt<32>
    input CEB2 : UInt<1>
    input OEB2 : UInt<1>
    input WEB2 : UInt<1>

    defname = SRAM2RW64x32
"""
  compile(mem, Some(lib), v, false)
}
