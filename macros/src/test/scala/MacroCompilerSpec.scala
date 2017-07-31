package barstools.macros

import firrtl.ir.{Circuit, NoInfo}
import firrtl.passes.RemoveEmpty
import firrtl.Parser.parse
import firrtl.Utils.ceilLog2
import java.io.{File, StringWriter}

abstract class MacroCompilerSpec extends org.scalatest.FlatSpec with org.scalatest.Matchers {
  /**
   * Terminology note:
   * mem - target memory to compile, in design (e.g. Mem() in rocket)
   * lib - technology SRAM(s) to use to compile mem
   */

  val macroDir: String = "tapeout/src/test/resources/macros"
  val testDir: String = "test_run_dir/macros"
  new File(testDir).mkdirs // Make sure the testDir exists

  // Override these to change the prefixing of macroDir and testDir
  val memPrefix: String = macroDir
  val libPrefix: String = macroDir
  val vPrefix: String = testDir

  private def args(mem: String, lib: Option[String], v: String, synflops: Boolean) =
    List("-m", mem.toString, "-v", v) ++
    (lib match { case None => Nil case Some(l) => List("-l", l.toString) }) ++
    (if (synflops) List("--syn-flops") else Nil)

  // Run the full compiler as if from the command line interface.
  // Generates the Verilog; useful in testing since an error will throw an
  // exception.
  def compile(mem: String, lib: String, v: String, synflops: Boolean) {
    compile(mem, Some(lib), v, synflops)
  }
  def compile(mem: String, lib: Option[String], v: String, synflops: Boolean) {
    var mem_full = concat(memPrefix, mem)
    var lib_full = concat(libPrefix, lib)
    var v_full = concat(vPrefix, v)

    MacroCompiler.run(args(mem_full, lib_full, v_full, synflops))
  }

  // Helper functions to write macro libraries to the given files.
  def writeToLib(lib: String, libs: Seq[mdf.macrolib.Macro]) = {
    mdf.macrolib.Utils.writeMDFToPath(Some(concat(libPrefix, lib)), libs)
  }

  def writeToMem(mem: String, mems: Seq[mdf.macrolib.Macro]) = {
    mdf.macrolib.Utils.writeMDFToPath(Some(concat(memPrefix, mem)), mems)
  }

  // Convenience function for running both compile, execute, and test at once.
  def compileExecuteAndTest(mem: String, lib: Option[String], v: String, output: String, synflops: Boolean): Unit = {
    compile(mem, lib, v, synflops)
    val result = execute(mem, lib, synflops)
    test(result, output)
  }
  def compileExecuteAndTest(mem: String, lib: String, v: String, output: String, synflops: Boolean = false): Unit = {
    compileExecuteAndTest(mem, Some(lib), v, output, synflops)
  }

  // Compare FIRRTL outputs after reparsing output with ScalaTest ("should be").
  def test(result: Circuit, output: String): Unit = {
    val gold = RemoveEmpty run parse(output)
    (result.serialize) should be (gold.serialize)
  }

  // Execute the macro compiler and returns a Circuit containing the output of
  // the memory compiler.
  def execute(memFile: String, libFile: Option[String], synflops: Boolean): Circuit = {
    execute(Some(memFile), libFile, synflops)
  }
  def execute(memFile: String, libFile: String, synflops: Boolean): Circuit = {
    execute(Some(memFile), Some(libFile), synflops)
  }
  def execute(memFile: Option[String], libFile: Option[String], synflops: Boolean): Circuit = {
    var mem_full = concat(memPrefix, memFile)
    var lib_full = concat(libPrefix, libFile)

    require(memFile.isDefined)
    val mems: Seq[Macro] = Utils.filterForSRAM(mdf.macrolib.Utils.readMDFFromPath(mem_full)).get map (new Macro(_))
    val libs: Option[Seq[Macro]] = Utils.filterForSRAM(mdf.macrolib.Utils.readMDFFromPath(lib_full)) match {
      case Some(x) => Some(x map (new Macro(_)))
      case None => None
    }
    val macros = mems map (_.blackbox)
    val circuit = Circuit(NoInfo, macros, macros.last.name)
    val passes = Seq(
      new MacroCompilerPass(Some(mems), libs),
      new SynFlopsPass(synflops, libs getOrElse mems),
      RemoveEmpty)
    val result: Circuit = (passes foldLeft circuit)((c, pass) => pass run c)
    result
  }

  // Helper method to deal with String + Option[String]
  private def concat(a: String, b: String): String = {a + "/" + b}
  private def concat(a: String, b: Option[String]): Option[String] = {
    b match {
      case Some(b2:String) => Some(a + "/" + b2)
      case _ => None
    }
  }
}

// A collection of standard SRAM generators.
trait HasSRAMGenerator {
  import mdf.macrolib._

  // Generate a standard (read/write/combo) port for testing.
  def generateTestPort(
    prefix: String,
    width: Int,
    depth: Int,
    maskGran: Option[Int] = None,
    read: Boolean,
    readEnable: Boolean = false,
    write: Boolean,
    writeEnable: Boolean = false
  ): MacroPort = {
    val realPrefix = prefix + "_"

    MacroPort(
      address=PolarizedPort(name=realPrefix + "addr", polarity=ActiveHigh),
      clock=PolarizedPort(name=realPrefix + "clk", polarity=PositiveEdge),

      readEnable=if (readEnable) Some(PolarizedPort(name=realPrefix + "read_en", polarity=ActiveHigh)) else None,
      writeEnable=if (writeEnable) Some(PolarizedPort(name=realPrefix + "write_en", polarity=ActiveHigh)) else None,

      output=if (read) Some(PolarizedPort(name=realPrefix + "dout", polarity=ActiveHigh)) else None,
      input=if (write) Some(PolarizedPort(name=realPrefix + "din", polarity=ActiveHigh)) else None,

      maskPort=maskGran match {
        case Some(x:Int) => Some(PolarizedPort(name=realPrefix + "mask", polarity=ActiveHigh))
        case _ => None
      },
      maskGran=maskGran,

      width=width, depth=depth // These numbers don't matter here.
    )
  }

  // Generate a read port for testing.
  def generateReadPort(prefix: String, width: Int, depth: Int, readEnable: Boolean = false): MacroPort = {
    generateTestPort(prefix, width, depth, write=false, read=true, readEnable=readEnable)
  }

  // Generate a write port for testing.
  def generateWritePort(prefix: String, width: Int, depth: Int, maskGran: Option[Int] = None, writeEnable: Boolean = true): MacroPort = {
    generateTestPort(prefix, width, depth, maskGran=maskGran, write=true, read=false, writeEnable=writeEnable)
  }

  // Generate a simple read-write port for testing.
  def generateReadWritePort(prefix: String, width: Int, depth: Int, maskGran: Option[Int] = None): MacroPort = {
    generateTestPort(
      prefix, width, depth, maskGran=maskGran,
      write=true, writeEnable=true,
      read=true, readEnable=false
    )
  }

  // Generate a "simple" SRAM (active high/positive edge, 1 read-write port).
  def generateSRAM(name: String, prefix: String, width: Int, depth: Int, maskGran: Option[Int] = None, extraPorts: Seq[MacroExtraPort] = List()): SRAMMacro = {
    SRAMMacro(
      macroType=SRAM,
      name=name,
      width=width,
      depth=depth,
      family="1rw",
      ports=Seq(generateReadWritePort(prefix, width, depth, maskGran)),
      extraPorts=extraPorts
    )
  }
}

// Generic "simple" test generator.
// Set up scaffolding for generating memories, files, etc.
// Override this generator to specify the expected FIRRTL output.
trait HasSimpleTestGenerator {
  this: MacroCompilerSpec with HasSRAMGenerator =>
    // Override these with "override lazy val".
    // Why lazy? These are used in the constructor here so overriding non-lazily
    // would be too late.
    def memWidth: Int
    def libWidth: Int
    def memDepth: Int
    def libDepth: Int
    def memMaskGran: Option[Int] = None
    def libMaskGran: Option[Int] = None
    def extraPorts: Seq[mdf.macrolib.MacroExtraPort] = List()
    def extraTag: String = ""

    // Override this in the sub-generator if you need a more specific name.
    // Defaults to using reflection to pull the name of the test using this
    // generator.
    def generatorType: String = this.getClass.getSimpleName

    require (memDepth >= libDepth)

    override val memPrefix = testDir
    override val libPrefix = testDir

    // Convenience variables to check if a mask exists.
    val memHasMask = memMaskGran != None
    val libHasMask = libMaskGran != None
    // We need to figure out how many mask bits there are in the mem.
    val memMaskBits = if (memHasMask) memWidth / memMaskGran.get else 0
    val libMaskBits = if (libHasMask) libWidth / libMaskGran.get else 0

    val extraTagPrefixed = if (extraTag == "") "" else ("-" + extraTag)

    val mem = s"mem-${generatorType}${extraTagPrefixed}.json"
    val lib = s"lib-${generatorType}${extraTagPrefixed}.json"
    val v = s"${generatorType}${extraTagPrefixed}.v"

    val mem_name = "target_memory"
    val mem_addr_width = ceilLog2(memDepth)

    val lib_name = "awesome_lib_mem"
    val lib_addr_width = ceilLog2(libDepth)

    // Override these to change the port prefixes if needed.
    def libPortPrefix: String = "lib"
    def memPortPrefix: String = "outer"

    // These generate "simple" SRAMs (1 masked read-write port) by default,
    // but can be overridden if need be.
    def generateLibSRAM() = generateSRAM(lib_name, libPortPrefix, libWidth, libDepth, libMaskGran, extraPorts)
    def generateMemSRAM() = generateSRAM(mem_name, memPortPrefix, memWidth, memDepth, memMaskGran)

    val libSRAM = generateLibSRAM
    val memSRAM = generateMemSRAM

    writeToLib(lib, Seq(libSRAM))
    writeToMem(mem, Seq(memSRAM))

    // Number of lib instances needed to hold the mem, in both directions.
    // Round up (e.g. 1.5 instances = effectively 2 instances)
    val depthInstances = math.ceil(memDepth.toFloat / libDepth).toInt
    val widthInstances = math.ceil(memWidth.toFloat / libWidth).toInt
    // Number of width bits in the last width-direction memory.
    // e.g. if memWidth = 16 and libWidth = 8, this would be 8 since the last memory 0_1 has 8 bits of input width.
    // e.g. if memWidth = 9 and libWidth = 8, this would be 1 since the last memory 0_1 has 1 bit of input width.
    val lastWidthBits = if (memWidth % libWidth == 0) libWidth else (memWidth % libWidth)
    val selectBits = mem_addr_width - lib_addr_width

    // Generate the header (contains the circuit statement and the target memory
    // module.
    def generateHeader(): String = {
      require (memSRAM.ports.size == 1, "Header generator only supports single port mem")

      val readEnable = if (memSRAM.ports(0).readEnable.isDefined) s"input ${memPortPrefix}_read_en : UInt<1>" else ""
      val headerMask = if (memHasMask) s"input ${memPortPrefix}_mask : UInt<${memMaskBits}>" else ""
      s"""
circuit $mem_name :
  module $mem_name :
    input ${memPortPrefix}_clk : Clock
    input ${memPortPrefix}_addr : UInt<$mem_addr_width>
    input ${memPortPrefix}_din : UInt<$memWidth>
    output ${memPortPrefix}_dout : UInt<$memWidth>
    ${readEnable}
    input ${memPortPrefix}_write_en : UInt<1>
    ${headerMask}
  """
    }

    // Generate the target memory ports.
    def generateFooterPorts(): String = {
      require (libSRAM.ports.size == 1, "Footer generator only supports single port lib")

      val readEnable = if (libSRAM.ports(0).readEnable.isDefined) s"input ${libPortPrefix}_read_en : UInt<1>" else ""
      val footerMask = if (libHasMask) s"input ${libPortPrefix}_mask : UInt<${libMaskBits}>" else ""
      s"""
    input ${libPortPrefix}_clk : Clock
    input ${libPortPrefix}_addr : UInt<$lib_addr_width>
    input ${libPortPrefix}_din : UInt<$libWidth>
    output ${libPortPrefix}_dout : UInt<$libWidth>
    ${readEnable}
    input ${libPortPrefix}_write_en : UInt<1>
    ${footerMask}
  """
    }

    // Generate the footer (contains the target memory extmodule declaration by default).
    def generateFooter(): String = {
      require (libSRAM.ports.size == 1, "Footer generator only supports single port lib")

      val readEnable = if (libSRAM.ports(0).readEnable.isDefined) s"input ${libPortPrefix}_read_en : UInt<1>" else ""
      val footerMask = if (libHasMask) s"input ${libPortPrefix}_mask : UInt<${libMaskBits}>" else ""
      s"""
  extmodule $lib_name :
${generateFooterPorts}

    defname = $lib_name
  """
    }

    // Abstract method to generate body; to be overridden by specific generator type.
    def generateBody(): String

    // Generate the entire output from header, body, and footer.
    def generateOutput(): String = {
      s"""
${generateHeader}
${generateBody}
${generateFooter}
      """
    }

    val output = generateOutput()
}

// Use this trait for tests that invoke the memory compiler without lib.
trait HasNoLibTestGenerator extends HasSimpleTestGenerator {
  this: MacroCompilerSpec with HasSRAMGenerator =>

    // If there isn't a lib, then the "lib" will become a FIRRTL "mem", which
    // in turn becomes synthesized flops.
    // Therefore, make "lib" width/depth equal to the mem.
    override lazy val libDepth = memDepth
    override lazy val libWidth = memWidth
    // Do the same for port names.
    override lazy val libPortPrefix = memPortPrefix

    // If there is no lib, don't generate a body.
    override def generateBody = ""
}

//~ class RocketChipTest extends MacroCompilerSpec {
  //~ val mem = new File(macroDir, "rocketchip.json")
  //~ val lib = new File(macroDir, "mylib.json")
  //~ val v = new File(testDir, "rocketchip.macro.v")
  //~ val output = // TODO: check correctness...
//~ """
//~ circuit T_2172_ext :
  //~ module tag_array_ext :
    //~ input RW0_clk : Clock
    //~ input RW0_addr : UInt<6>
    //~ input RW0_wdata : UInt<80>
    //~ output RW0_rdata : UInt<80>
    //~ input RW0_en : UInt<1>
    //~ input RW0_wmode : UInt<1>
    //~ input RW0_wmask : UInt<4>

    //~ inst mem_0_0 of SRAM1RW64x32
    //~ inst mem_0_1 of SRAM1RW64x32
    //~ inst mem_0_2 of SRAM1RW64x32
    //~ inst mem_0_3 of SRAM1RW64x32
    //~ mem_0_0.CE <= RW0_clk
    //~ mem_0_0.A <= RW0_addr
    //~ node RW0_rdata_0_0 = bits(mem_0_0.O, 19, 0)
    //~ mem_0_0.I <= bits(RW0_wdata, 19, 0)
    //~ mem_0_0.OEB <= not(and(not(RW0_wmode), UInt<1>("h1")))
    //~ mem_0_0.WEB <= not(and(and(RW0_wmode, bits(RW0_wmask, 0, 0)), UInt<1>("h1")))
    //~ mem_0_0.CEB <= not(and(RW0_en, UInt<1>("h1")))
    //~ mem_0_1.CE <= RW0_clk
    //~ mem_0_1.A <= RW0_addr
    //~ node RW0_rdata_0_1 = bits(mem_0_1.O, 19, 0)
    //~ mem_0_1.I <= bits(RW0_wdata, 39, 20)
    //~ mem_0_1.OEB <= not(and(not(RW0_wmode), UInt<1>("h1")))
    //~ mem_0_1.WEB <= not(and(and(RW0_wmode, bits(RW0_wmask, 1, 1)), UInt<1>("h1")))
    //~ mem_0_1.CEB <= not(and(RW0_en, UInt<1>("h1")))
    //~ mem_0_2.CE <= RW0_clk
    //~ mem_0_2.A <= RW0_addr
    //~ node RW0_rdata_0_2 = bits(mem_0_2.O, 19, 0)
    //~ mem_0_2.I <= bits(RW0_wdata, 59, 40)
    //~ mem_0_2.OEB <= not(and(not(RW0_wmode), UInt<1>("h1")))
    //~ mem_0_2.WEB <= not(and(and(RW0_wmode, bits(RW0_wmask, 2, 2)), UInt<1>("h1")))
    //~ mem_0_2.CEB <= not(and(RW0_en, UInt<1>("h1")))
    //~ mem_0_3.CE <= RW0_clk
    //~ mem_0_3.A <= RW0_addr
    //~ node RW0_rdata_0_3 = bits(mem_0_3.O, 19, 0)
    //~ mem_0_3.I <= bits(RW0_wdata, 79, 60)
    //~ mem_0_3.OEB <= not(and(not(RW0_wmode), UInt<1>("h1")))
    //~ mem_0_3.WEB <= not(and(and(RW0_wmode, bits(RW0_wmask, 3, 3)), UInt<1>("h1")))
    //~ mem_0_3.CEB <= not(and(RW0_en, UInt<1>("h1")))
    //~ node RW0_rdata_0 = cat(RW0_rdata_0_3, cat(RW0_rdata_0_2, cat(RW0_rdata_0_1, RW0_rdata_0_0)))
    //~ RW0_rdata <= mux(UInt<1>("h1"), RW0_rdata_0, UInt<1>("h0"))

  //~ extmodule SRAM1RW64x32 :
    //~ input CE : Clock
    //~ input A : UInt<6>
    //~ input I : UInt<32>
    //~ output O : UInt<32>
    //~ input CEB : UInt<1>
    //~ input OEB : UInt<1>
    //~ input WEB : UInt<1>

    //~ defname = SRAM1RW64x32


  //~ module T_1090_ext :
    //~ input RW0_clk : Clock
    //~ input RW0_addr : UInt<9>
    //~ input RW0_wdata : UInt<64>
    //~ output RW0_rdata : UInt<64>
    //~ input RW0_en : UInt<1>
    //~ input RW0_wmode : UInt<1>

    //~ inst mem_0_0 of SRAM1RW512x32
    //~ inst mem_0_1 of SRAM1RW512x32
    //~ mem_0_0.CE <= RW0_clk
    //~ mem_0_0.A <= RW0_addr
    //~ node RW0_rdata_0_0 = bits(mem_0_0.O, 31, 0)
    //~ mem_0_0.I <= bits(RW0_wdata, 31, 0)
    //~ mem_0_0.OEB <= not(and(not(RW0_wmode), UInt<1>("h1")))
    //~ mem_0_0.WEB <= not(and(and(RW0_wmode, UInt<1>("h1")), UInt<1>("h1")))
    //~ mem_0_0.CEB <= not(and(RW0_en, UInt<1>("h1")))
    //~ mem_0_1.CE <= RW0_clk
    //~ mem_0_1.A <= RW0_addr
    //~ node RW0_rdata_0_1 = bits(mem_0_1.O, 31, 0)
    //~ mem_0_1.I <= bits(RW0_wdata, 63, 32)
    //~ mem_0_1.OEB <= not(and(not(RW0_wmode), UInt<1>("h1")))
    //~ mem_0_1.WEB <= not(and(and(RW0_wmode, UInt<1>("h1")), UInt<1>("h1")))
    //~ mem_0_1.CEB <= not(and(RW0_en, UInt<1>("h1")))
    //~ node RW0_rdata_0 = cat(RW0_rdata_0_1, RW0_rdata_0_0)
    //~ RW0_rdata <= mux(UInt<1>("h1"), RW0_rdata_0, UInt<1>("h0"))

  //~ module T_406_ext :
    //~ input RW0_clk : Clock
    //~ input RW0_addr : UInt<9>
    //~ input RW0_wdata : UInt<64>
    //~ output RW0_rdata : UInt<64>
    //~ input RW0_en : UInt<1>
    //~ input RW0_wmode : UInt<1>
    //~ input RW0_wmask : UInt<8>

    //~ inst mem_0_0 of SRAM1RW512x32
    //~ inst mem_0_1 of SRAM1RW512x32
    //~ inst mem_0_2 of SRAM1RW512x32
    //~ inst mem_0_3 of SRAM1RW512x32
    //~ inst mem_0_4 of SRAM1RW512x32
    //~ inst mem_0_5 of SRAM1RW512x32
    //~ inst mem_0_6 of SRAM1RW512x32
    //~ inst mem_0_7 of SRAM1RW512x32
    //~ mem_0_0.CE <= RW0_clk
    //~ mem_0_0.A <= RW0_addr
    //~ node RW0_rdata_0_0 = bits(mem_0_0.O, 7, 0)
    //~ mem_0_0.I <= bits(RW0_wdata, 7, 0)
    //~ mem_0_0.OEB <= not(and(not(RW0_wmode), UInt<1>("h1")))
    //~ mem_0_0.WEB <= not(and(and(RW0_wmode, bits(RW0_wmask, 0, 0)), UInt<1>("h1")))
    //~ mem_0_0.CEB <= not(and(RW0_en, UInt<1>("h1")))
    //~ mem_0_1.CE <= RW0_clk
    //~ mem_0_1.A <= RW0_addr
    //~ node RW0_rdata_0_1 = bits(mem_0_1.O, 7, 0)
    //~ mem_0_1.I <= bits(RW0_wdata, 15, 8)
    //~ mem_0_1.OEB <= not(and(not(RW0_wmode), UInt<1>("h1")))
    //~ mem_0_1.WEB <= not(and(and(RW0_wmode, bits(RW0_wmask, 1, 1)), UInt<1>("h1")))
    //~ mem_0_1.CEB <= not(and(RW0_en, UInt<1>("h1")))
    //~ mem_0_2.CE <= RW0_clk
    //~ mem_0_2.A <= RW0_addr
    //~ node RW0_rdata_0_2 = bits(mem_0_2.O, 7, 0)
    //~ mem_0_2.I <= bits(RW0_wdata, 23, 16)
    //~ mem_0_2.OEB <= not(and(not(RW0_wmode), UInt<1>("h1")))
    //~ mem_0_2.WEB <= not(and(and(RW0_wmode, bits(RW0_wmask, 2, 2)), UInt<1>("h1")))
    //~ mem_0_2.CEB <= not(and(RW0_en, UInt<1>("h1")))
    //~ mem_0_3.CE <= RW0_clk
    //~ mem_0_3.A <= RW0_addr
    //~ node RW0_rdata_0_3 = bits(mem_0_3.O, 7, 0)
    //~ mem_0_3.I <= bits(RW0_wdata, 31, 24)
    //~ mem_0_3.OEB <= not(and(not(RW0_wmode), UInt<1>("h1")))
    //~ mem_0_3.WEB <= not(and(and(RW0_wmode, bits(RW0_wmask, 3, 3)), UInt<1>("h1")))
    //~ mem_0_3.CEB <= not(and(RW0_en, UInt<1>("h1")))
    //~ mem_0_4.CE <= RW0_clk
    //~ mem_0_4.A <= RW0_addr
    //~ node RW0_rdata_0_4 = bits(mem_0_4.O, 7, 0)
    //~ mem_0_4.I <= bits(RW0_wdata, 39, 32)
    //~ mem_0_4.OEB <= not(and(not(RW0_wmode), UInt<1>("h1")))
    //~ mem_0_4.WEB <= not(and(and(RW0_wmode, bits(RW0_wmask, 4, 4)), UInt<1>("h1")))
    //~ mem_0_4.CEB <= not(and(RW0_en, UInt<1>("h1")))
    //~ mem_0_5.CE <= RW0_clk
    //~ mem_0_5.A <= RW0_addr
    //~ node RW0_rdata_0_5 = bits(mem_0_5.O, 7, 0)
    //~ mem_0_5.I <= bits(RW0_wdata, 47, 40)
    //~ mem_0_5.OEB <= not(and(not(RW0_wmode), UInt<1>("h1")))
    //~ mem_0_5.WEB <= not(and(and(RW0_wmode, bits(RW0_wmask, 5, 5)), UInt<1>("h1")))
    //~ mem_0_5.CEB <= not(and(RW0_en, UInt<1>("h1")))
    //~ mem_0_6.CE <= RW0_clk
    //~ mem_0_6.A <= RW0_addr
    //~ node RW0_rdata_0_6 = bits(mem_0_6.O, 7, 0)
    //~ mem_0_6.I <= bits(RW0_wdata, 55, 48)
    //~ mem_0_6.OEB <= not(and(not(RW0_wmode), UInt<1>("h1")))
    //~ mem_0_6.WEB <= not(and(and(RW0_wmode, bits(RW0_wmask, 6, 6)), UInt<1>("h1")))
    //~ mem_0_6.CEB <= not(and(RW0_en, UInt<1>("h1")))
    //~ mem_0_7.CE <= RW0_clk
    //~ mem_0_7.A <= RW0_addr
    //~ node RW0_rdata_0_7 = bits(mem_0_7.O, 7, 0)
    //~ mem_0_7.I <= bits(RW0_wdata, 63, 56)
    //~ mem_0_7.OEB <= not(and(not(RW0_wmode), UInt<1>("h1")))
    //~ mem_0_7.WEB <= not(and(and(RW0_wmode, bits(RW0_wmask, 7, 7)), UInt<1>("h1")))
    //~ mem_0_7.CEB <= not(and(RW0_en, UInt<1>("h1")))
    //~ node RW0_rdata_0 = cat(RW0_rdata_0_7, cat(RW0_rdata_0_6, cat(RW0_rdata_0_5, cat(RW0_rdata_0_4, cat(RW0_rdata_0_3, cat(RW0_rdata_0_2, cat(RW0_rdata_0_1, RW0_rdata_0_0)))))))
    //~ RW0_rdata <= mux(UInt<1>("h1"), RW0_rdata_0, UInt<1>("h0"))

  //~ extmodule SRAM1RW512x32 :
    //~ input CE : Clock
    //~ input A : UInt<9>
    //~ input I : UInt<32>
    //~ output O : UInt<32>
    //~ input CEB : UInt<1>
    //~ input OEB : UInt<1>
    //~ input WEB : UInt<1>

    //~ defname = SRAM1RW512x32


  //~ module T_2172_ext :
    //~ input W0_clk : Clock
    //~ input W0_addr : UInt<6>
    //~ input W0_data : UInt<88>
    //~ input W0_en : UInt<1>
    //~ input W0_mask : UInt<4>
    //~ input R0_clk : Clock
    //~ input R0_addr : UInt<6>
    //~ output R0_data : UInt<88>
    //~ input R0_en : UInt<1>

    //~ inst mem_0_0 of SRAM2RW64x32
    //~ inst mem_0_1 of SRAM2RW64x32
    //~ inst mem_0_2 of SRAM2RW64x32
    //~ inst mem_0_3 of SRAM2RW64x32
    //~ mem_0_0.CE1 <= W0_clk
    //~ mem_0_0.A1 <= W0_addr
    //~ mem_0_0.I1 <= bits(W0_data, 21, 0)
    //~ mem_0_0.OEB1 <= not(and(not(UInt<1>("h1")), UInt<1>("h1")))
    //~ mem_0_0.WEB1 <= not(and(and(UInt<1>("h1"), bits(W0_mask, 0, 0)), UInt<1>("h1")))
    //~ mem_0_0.CEB1 <= not(and(W0_en, UInt<1>("h1")))
    //~ mem_0_1.CE1 <= W0_clk
    //~ mem_0_1.A1 <= W0_addr
    //~ mem_0_1.I1 <= bits(W0_data, 43, 22)
    //~ mem_0_1.OEB1 <= not(and(not(UInt<1>("h1")), UInt<1>("h1")))
    //~ mem_0_1.WEB1 <= not(and(and(UInt<1>("h1"), bits(W0_mask, 1, 1)), UInt<1>("h1")))
    //~ mem_0_1.CEB1 <= not(and(W0_en, UInt<1>("h1")))
    //~ mem_0_2.CE1 <= W0_clk
    //~ mem_0_2.A1 <= W0_addr
    //~ mem_0_2.I1 <= bits(W0_data, 65, 44)
    //~ mem_0_2.OEB1 <= not(and(not(UInt<1>("h1")), UInt<1>("h1")))
    //~ mem_0_2.WEB1 <= not(and(and(UInt<1>("h1"), bits(W0_mask, 2, 2)), UInt<1>("h1")))
    //~ mem_0_2.CEB1 <= not(and(W0_en, UInt<1>("h1")))
    //~ mem_0_3.CE1 <= W0_clk
    //~ mem_0_3.A1 <= W0_addr
    //~ mem_0_3.I1 <= bits(W0_data, 87, 66)
    //~ mem_0_3.OEB1 <= not(and(not(UInt<1>("h1")), UInt<1>("h1")))
    //~ mem_0_3.WEB1 <= not(and(and(UInt<1>("h1"), bits(W0_mask, 3, 3)), UInt<1>("h1")))
    //~ mem_0_3.CEB1 <= not(and(W0_en, UInt<1>("h1")))
    //~ mem_0_0.CE2 <= R0_clk
    //~ mem_0_0.A2 <= R0_addr
    //~ node R0_data_0_0 = bits(mem_0_0.O2, 21, 0)
    //~ mem_0_0.OEB2 <= not(and(not(UInt<1>("h0")), UInt<1>("h1")))
    //~ mem_0_0.WEB2 <= not(and(and(UInt<1>("h0"), UInt<1>("h1")), UInt<1>("h1")))
    //~ mem_0_0.CEB2 <= not(and(R0_en, UInt<1>("h1")))
    //~ mem_0_1.CE2 <= R0_clk
    //~ mem_0_1.A2 <= R0_addr
    //~ node R0_data_0_1 = bits(mem_0_1.O2, 21, 0)
    //~ mem_0_1.OEB2 <= not(and(not(UInt<1>("h0")), UInt<1>("h1")))
    //~ mem_0_1.WEB2 <= not(and(and(UInt<1>("h0"), UInt<1>("h1")), UInt<1>("h1")))
    //~ mem_0_1.CEB2 <= not(and(R0_en, UInt<1>("h1")))
    //~ mem_0_2.CE2 <= R0_clk
    //~ mem_0_2.A2 <= R0_addr
    //~ node R0_data_0_2 = bits(mem_0_2.O2, 21, 0)
    //~ mem_0_2.OEB2 <= not(and(not(UInt<1>("h0")), UInt<1>("h1")))
    //~ mem_0_2.WEB2 <= not(and(and(UInt<1>("h0"), UInt<1>("h1")), UInt<1>("h1")))
    //~ mem_0_2.CEB2 <= not(and(R0_en, UInt<1>("h1")))
    //~ mem_0_3.CE2 <= R0_clk
    //~ mem_0_3.A2 <= R0_addr
    //~ node R0_data_0_3 = bits(mem_0_3.O2, 21, 0)
    //~ mem_0_3.OEB2 <= not(and(not(UInt<1>("h0")), UInt<1>("h1")))
    //~ mem_0_3.WEB2 <= not(and(and(UInt<1>("h0"), UInt<1>("h1")), UInt<1>("h1")))
    //~ mem_0_3.CEB2 <= not(and(R0_en, UInt<1>("h1")))
    //~ node R0_data_0 = cat(R0_data_0_3, cat(R0_data_0_2, cat(R0_data_0_1, R0_data_0_0)))
    //~ R0_data <= mux(UInt<1>("h1"), R0_data_0, UInt<1>("h0"))

  //~ extmodule SRAM2RW64x32 :
    //~ input CE1 : Clock
    //~ input A1 : UInt<6>
    //~ input I1 : UInt<32>
    //~ output O1 : UInt<32>
    //~ input CEB1 : UInt<1>
    //~ input OEB1 : UInt<1>
    //~ input WEB1 : UInt<1>
    //~ input CE2 : Clock
    //~ input A2 : UInt<6>
    //~ input I2 : UInt<32>
    //~ output O2 : UInt<32>
    //~ input CEB2 : UInt<1>
    //~ input OEB2 : UInt<1>
    //~ input WEB2 : UInt<1>

    //~ defname = SRAM2RW64x32
//~ """
  //~ compile(mem, Some(lib), v, false)
//~ }
