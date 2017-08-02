package barstools.macros

import firrtl.ir.{Circuit, NoInfo}
import firrtl.passes.RemoveEmpty
import firrtl.Parser.parse
import firrtl.Utils.ceilLog2
import java.io.{File, StringWriter}

abstract class MacroCompilerSpec extends org.scalatest.FlatSpec with org.scalatest.Matchers {
  val testDir: String = "test_run_dir/macros"
  new File(testDir).mkdirs // Make sure the testDir exists

  // Override these to change the prefixing of macroDir and testDir
  val memPrefix: String = testDir
  val libPrefix: String = testDir
  val vPrefix: String = testDir

  // Override this to use a different cost metric.
  // If this is None, the compile() call will not have any -c/-cp arguments, and
  // execute() will use CostMetric.default.
  val costMetric: Option[CostMetric] = None
  private def getCostMetric: CostMetric = costMetric.getOrElse(CostMetric.default)

  private def costMetricCmdLine = {
    costMetric match {
      case None => Nil
      case Some(m) => {
        val name = m.name
        val params = m.commandLineParams
        List("-c", name) ++ params.flatMap{ case (key, value) => List("-cp", key, value) }
      }
    }
  }

  private def args(mem: String, lib: Option[String], v: String, synflops: Boolean) =
    List("-m", mem.toString, "-v", v) ++
    (lib match { case None => Nil case Some(l) => List("-l", l.toString) }) ++
    costMetricCmdLine ++
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
      new MacroCompilerPass(Some(mems), libs, getCostMetric),
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
    val realPrefix = if (prefix == "") "" else prefix + "_"

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
