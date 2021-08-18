// See LICENSE for license details.

package barstools.macros

import firrtl.Parser.parse
import firrtl.ir.{Circuit, NoInfo}
import firrtl.passes.RemoveEmpty
import mdf.macrolib.SRAMMacro
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.io.File

abstract class MacroCompilerSpec extends AnyFlatSpec with Matchers {
  import scala.language.implicitConversions
  implicit def String2SomeString(i: String): Option[String] = Some(i)
  val testDir: String = "test_run_dir/macros"
  new File(testDir).mkdirs // Make sure the testDir exists

  // Override these to change the prefixing of macroDir and testDir
  val memPrefix: String = testDir
  val libPrefix: String = testDir
  val vPrefix:   String = testDir

  // Override this to use a different cost metric.
  // If this is None, the compile() call will not have any -c/-cp arguments, and
  // execute() will use CostMetric.default.
  val costMetric:            Option[CostMetric] = None
  private def getCostMetric: CostMetric = costMetric.getOrElse(CostMetric.default)

  private def costMetricCmdLine = {
    costMetric match {
      case None => Nil
      case Some(m) =>
        val name = m.name()
        val params = m.commandLineParams()
        List("-c", name) ++ params.flatMap { case (key, value) => List("-cp", key, value) }
    }
  }

  private def args(mem: String, lib: Option[String], v: String, synflops: Boolean, useCompiler: Boolean) =
    List("-m", mem, "-v", v) ++
      (lib match {
        case None    => Nil
        case Some(l) => List("-l", l)
      }) ++
      costMetricCmdLine ++
      (if (synflops) List("--mode", "synflops") else Nil) ++
      (if (useCompiler) List("--use-compiler") else Nil)

  // Run the full compiler as if from the command line interface.
  // Generates the Verilog; useful in testing since an error will throw an
  // exception.
  def compile(mem: String, lib: String, v: String, synflops: Boolean): Unit = {
    compile(mem, Some(lib), v, synflops)
  }
  def compile(mem: String, lib: Option[String], v: String, synflops: Boolean, useCompiler: Boolean = false): Unit = {
    val mem_full = concat(memPrefix, mem)
    val lib_full = concat(libPrefix, lib)
    val v_full = concat(vPrefix, v)

    MacroCompiler.run(args(mem_full, lib_full, v_full, synflops, useCompiler))
  }

  // Helper functions to write macro libraries to the given files.
  def writeToLib(lib: String, libs: Seq[mdf.macrolib.Macro]): Boolean = {
    mdf.macrolib.Utils.writeMDFToPath(Some(concat(libPrefix, lib)), libs)
  }

  def writeToMem(mem: String, mems: Seq[mdf.macrolib.Macro]): Boolean = {
    mdf.macrolib.Utils.writeMDFToPath(Some(concat(memPrefix, mem)), mems)
  }

  // Convenience function for running both compile, execute, and test at once.
  def compileExecuteAndTest(
    mem:         String,
    lib:         Option[String],
    v:           String,
    output:      String,
    synflops:    Boolean = false,
    useCompiler: Boolean = false
  ): Unit = {
    compile(mem, lib, v, synflops, useCompiler)
    val result = execute(mem, lib, synflops, useCompiler)
    test(result, output)
  }

  // Compare FIRRTL outputs after reparsing output with ScalaTest ("should be").
  def test(result: Circuit, output: String): Unit = {
    val gold = RemoveEmpty.run(parse(output))
    result.serialize should be(gold.serialize)
  }

  // Execute the macro compiler and returns a Circuit containing the output of
  // the memory compiler.
  def execute(memFile: Option[String], libFile: Option[String], synflops: Boolean): Circuit =
    execute(memFile, libFile, synflops, useCompiler = false)
  def execute(memFile: Option[String], libFile: Option[String], synflops: Boolean, useCompiler: Boolean): Circuit = {
    val mem_full = concat(memPrefix, memFile)
    val lib_full = concat(libPrefix, libFile)

    require(memFile.isDefined)
    val mems: Seq[Macro] = Utils.filterForSRAM(mdf.macrolib.Utils.readMDFFromPath(mem_full)).get.map(new Macro(_))
    val libs: Option[Seq[Macro]] = if (useCompiler) {
      Utils.findSRAMCompiler(mdf.macrolib.Utils.readMDFFromPath(lib_full)).map { x =>
        Utils.buildSRAMMacros(x).map(new Macro(_))
      }
    } else {
      Utils.filterForSRAM(mdf.macrolib.Utils.readMDFFromPath(lib_full)) match {
        case Some(x) => Some(x.map(new Macro(_)))
        case None    => None
      }
    }
    val macros = mems.map(_.blackbox)
    val circuit = Circuit(NoInfo, macros, macros.last.name)
    val passes = Seq(
      new MacroCompilerPass(
        Some(mems),
        libs,
        None,
        None,
        getCostMetric,
        if (synflops) MacroCompilerAnnotation.Synflops else MacroCompilerAnnotation.Default
      ),
      new SynFlopsPass(synflops, libs.getOrElse(mems)),
      RemoveEmpty
    )
    val result: Circuit = passes.foldLeft(circuit)((c, pass) => pass.run(c))
    result
  }

  // Helper method to deal with String + Option[String]
  private def concat(a: String, b: String): String = { a + "/" + b }
  private def concat(a: String, b: Option[String]): Option[String] = {
    b match {
      case Some(b2: String) => Some(a + "/" + b2)
      case _ => None
    }
  }
}

// A collection of standard SRAM generators.
trait HasSRAMGenerator {
  import mdf.macrolib._

  import scala.language.implicitConversions
  implicit def Int2SomeInt(i:       Int):    Option[Int] = Some(i)
  implicit def BigInt2SomeBigInt(i: BigInt): Option[BigInt] = Some(i)

  // Generate a standard (read/write/combo) port for testing.
  // Helper methods for optional width argument
  def generateTestPort(
    prefix:      String,
    width:       Option[Int],
    depth:       Option[BigInt],
    maskGran:    Option[Int] = None,
    read:        Boolean,
    readEnable:  Boolean = false,
    write:       Boolean,
    writeEnable: Boolean = false
  ): MacroPort = {
    val realPrefix = if (prefix == "") "" else prefix + "_"

    MacroPort(
      address = PolarizedPort(name = realPrefix + "addr", polarity = ActiveHigh),
      clock = Some(PolarizedPort(name = realPrefix + "clk", polarity = PositiveEdge)),
      readEnable = if (readEnable) Some(PolarizedPort(name = realPrefix + "read_en", polarity = ActiveHigh)) else None,
      writeEnable =
        if (writeEnable) Some(PolarizedPort(name = realPrefix + "write_en", polarity = ActiveHigh)) else None,
      output = if (read) Some(PolarizedPort(name = realPrefix + "dout", polarity = ActiveHigh)) else None,
      input = if (write) Some(PolarizedPort(name = realPrefix + "din", polarity = ActiveHigh)) else None,
      maskPort = maskGran match {
        case Some(_: Int) => Some(PolarizedPort(name = realPrefix + "mask", polarity = ActiveHigh))
        case _ => None
      },
      maskGran = maskGran,
      width = width,
      depth = depth // These numbers don't matter here.
    )
  }

  // Generate a read port for testing.
  def generateReadPort(
    prefix:     String,
    width:      Option[Int],
    depth:      Option[BigInt],
    readEnable: Boolean = false
  ): MacroPort = {
    generateTestPort(prefix, width, depth, write = false, read = true, readEnable = readEnable)
  }

  // Generate a write port for testing.
  def generateWritePort(
    prefix:      String,
    width:       Option[Int],
    depth:       Option[BigInt],
    maskGran:    Option[Int] = None,
    writeEnable: Boolean = true
  ): MacroPort = {
    generateTestPort(prefix, width, depth, maskGran = maskGran, write = true, read = false, writeEnable = writeEnable)
  }

  // Generate a simple read-write port for testing.
  def generateReadWritePort(
    prefix:   String,
    width:    Option[Int],
    depth:    Option[BigInt],
    maskGran: Option[Int] = None
  ): MacroPort = {
    generateTestPort(prefix, width, depth, maskGran = maskGran, write = true, writeEnable = true, read = true)
  }

  // Generate a "simple" SRAM (active high/positive edge, 1 read-write port).
  def generateSRAM(
    name:       String,
    prefix:     String,
    width:      Int,
    depth:      BigInt,
    maskGran:   Option[Int] = None,
    extraPorts: Seq[MacroExtraPort] = List()
  ): SRAMMacro = {
    SRAMMacro(
      name = name,
      width = width,
      depth = depth,
      family = "1rw",
      ports = Seq(generateReadWritePort(prefix, width, depth, maskGran)),
      extraPorts = extraPorts
    )
  }

  // Generate a "simple" SRAM group (active high/positive edge, 1 read-write port).
  def generateSimpleSRAMGroup(
    prefix:   String,
    mux:      Int,
    depth:    Range,
    width:    Range,
    maskGran: Option[Int] = None
  ): SRAMGroup = {
    SRAMGroup(
      Seq("mygroup_", "width", "x", "depth", "_", "VT"),
      "1rw",
      Seq("svt", "lvt", "ulvt"),
      mux,
      depth,
      width,
      Seq(generateReadWritePort(prefix, None, None, maskGran))
    )
  }

  // 'vt': ('svt','lvt','ulvt'), 'mux': 2,  'depth': range(16,513,8),       'width': range(8,289,2),   'ports': 1
  // 'vt': ('svt','lvt','ulvt'), 'mux': 4,  'depth': range(32,1025,16),     'width': range(4,145),     'ports': 1}
  def generateSRAMCompiler(name: String, prefix: String): mdf.macrolib.SRAMCompiler = {
    SRAMCompiler(
      name,
      Seq(
        generateSimpleSRAMGroup(prefix, 2, Range(16, 512, 8), Range(8, 288, 2)),
        generateSimpleSRAMGroup(prefix, 4, Range(32, 1024, 16), Range(4, 144, 1))
      )
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
  def useCompiler: Boolean = false
  def memWidth: Int
  def libWidth: Int
  def memDepth: BigInt
  def libDepth: BigInt
  def memMaskGran: Option[Int] = None
  def libMaskGran: Option[Int] = None
  def extraPorts:  Seq[mdf.macrolib.MacroExtraPort] = List()
  def extraTag:    String = ""

  // "Effective" libMaskGran by considering write_enable.
  val effectiveLibMaskGran: Int = libMaskGran.getOrElse(libWidth)

  // Override this in the sub-generator if you need a more specific name.
  // Defaults to using reflection to pull the name of the test using this
  // generator.
  def generatorType: String = this.getClass.getSimpleName

  //require (memDepth >= libDepth)

  // Convenience variables to check if a mask exists.
  val memHasMask: Boolean = memMaskGran.isDefined
  val libHasMask: Boolean = libMaskGran.isDefined
  // We need to figure out how many mask bits there are in the mem.
  val memMaskBits: Int = if (memHasMask) memWidth / memMaskGran.get else 0
  val libMaskBits: Int = if (libHasMask) libWidth / libMaskGran.get else 0

  val extraTagPrefixed: String = if (extraTag == "") "" else "-" + extraTag

  val mem = s"mem-$generatorType$extraTagPrefixed.json"
  val lib = s"lib-$generatorType$extraTagPrefixed.json"
  val v = s"$generatorType$extraTagPrefixed.v"

  lazy val mem_name = "target_memory"
  val mem_addr_width: Int = MacroCompilerMath.ceilLog2(memDepth)

  lazy val lib_name = "awesome_lib_mem"
  val lib_addr_width: Int = MacroCompilerMath.ceilLog2(libDepth)

  // Override these to change the port prefixes if needed.
  def libPortPrefix: String = "lib"
  def memPortPrefix: String = "outer"

  // These generate "simple" SRAMs (1 masked read-write port) by default,
  // but can be overridden if need be.
  def generateLibSRAM(): SRAMMacro = generateSRAM(lib_name, libPortPrefix, libWidth, libDepth, libMaskGran, extraPorts)
  def generateMemSRAM(): SRAMMacro = generateSRAM(mem_name, memPortPrefix, memWidth, memDepth, memMaskGran)

  def libSRAM: SRAMMacro = generateLibSRAM()
  def memSRAM: SRAMMacro = generateMemSRAM()

  def libSRAMs: Seq[SRAMMacro] = Seq(libSRAM)
  def memSRAMs: Seq[SRAMMacro] = Seq(memSRAM)

  writeToLib(lib, libSRAMs)
  writeToMem(mem, memSRAMs)

  // For masks, width it's a bit tricky since we have to consider cases like
  // memMaskGran = 4 and libMaskGran = 8.
  // Consider the actually usable libWidth in cases like the above.
  val usableLibWidth: Int =
    if (memMaskGran.getOrElse(Int.MaxValue) < effectiveLibMaskGran) memMaskGran.get else libWidth

  // Number of lib instances needed to hold the mem, in both directions.
  // Round up (e.g. 1.5 instances = effectively 2 instances)
  val depthInstances: Int = math.ceil(memDepth.toFloat / libDepth.toFloat).toInt
  val widthInstances: Int = math.ceil(memWidth.toFloat / usableLibWidth).toInt

  // Number of width bits in the last width-direction memory.
  // e.g. if memWidth = 16 and libWidth = 8, this would be 8 since the last memory 0_1 has 8 bits of input width.
  // e.g. if memWidth = 9 and libWidth = 8, this would be 1 since the last memory 0_1 has 1 bit of input width.
  lazy val lastWidthBits: Int = if (memWidth % usableLibWidth == 0) usableLibWidth else memWidth % usableLibWidth
  lazy val selectBits:    Int = mem_addr_width - lib_addr_width

  /** Convenience function to generate a mask statement.
    * @param widthInst Width instance (mem_0_x)
    * @param depthInst Depth instance (mem_x_0)
    */
  def generateMaskStatement(widthInst: Int, depthInst: Int): String = {
    // Width of this submemory.
    val myMemWidth = if (widthInst == widthInstances - 1) lastWidthBits else usableLibWidth
    // Base bit of this submemory.
    // e.g. if libWidth is 8 and this is submemory 2 (0-indexed), then this
    // would be 16.
    val myBaseBit = usableLibWidth * widthInst

    if (libMaskGran.isDefined) {
      if (memMaskGran.isEmpty) {
        // If there is no memory mask, we should just turn all the lib mask
        // bits high.
        s"""mem_${depthInst}_$widthInst.lib_mask <= UInt<$libMaskBits>("h${((1 << libMaskBits) - 1).toHexString}")"""
      } else {
        // Calculate which bit of outer_mask contains the given bit.
        // e.g. if memMaskGran = 2, libMaskGran = 1 and libWidth = 4, then
        // calculateMaskBit({0, 1}) = 0 and calculateMaskBit({1, 2}) = 1
        def calculateMaskBit(bit: Int): Int = bit / memMaskGran.getOrElse(memWidth)

        val bitsArr = (libMaskBits - 1 to 0 by -1).map(x => {
          if (x * libMaskGran.get > myMemWidth) {
            // If we have extra mask bits leftover after the effective width,
            // disable those bits.
            """UInt<1>("h0")"""
          } else {
            val outerMaskBit = calculateMaskBit(x * libMaskGran.get + myBaseBit)
            s"bits(outer_mask, $outerMaskBit, $outerMaskBit)"
          }
        })
        val maskVal = bitsArr.reduceRight((bit, rest) => s"cat($bit, $rest)")
        s"mem_${depthInst}_$widthInst.lib_mask <= $maskVal"
      }
    } else ""
  }

  /** Helper function to generate a port.
    *
    * @param prefix      Memory port prefix (e.g. "x" for ports like "x_clk")
    * @param addrWidth   Address port width
    * @param width       data width
    * @param write       Has a write port?
    * @param writeEnable Has a write enable port?
    * @param read        Has a read port?
    * @param readEnable  Has a read enable port?
    * @param mask        Mask granularity (# bits) of the port or None.
    * @param extraPorts  Extra ports (name, # bits)
    */
  def generatePort(
    prefix:      String,
    addrWidth:   Int,
    width:       Int,
    write:       Boolean,
    writeEnable: Boolean,
    read:        Boolean,
    readEnable:  Boolean,
    mask:        Option[Int],
    extraPorts:  Seq[(String, Int)] = Seq()
  ): String = {
    val realPrefix = if (prefix == "") "" else prefix + "_"

    val readStr = if (read) s"output ${realPrefix}dout : UInt<$width>" else ""
    val writeStr = if (write) s"input ${realPrefix}din : UInt<$width>" else ""
    val readEnableStr = if (readEnable) s"input ${realPrefix}read_en : UInt<1>" else ""
    val writeEnableStr = if (writeEnable) s"input ${realPrefix}write_en : UInt<1>" else ""
    val maskStr = mask match {
      case Some(maskBits: Int) => s"input ${realPrefix}mask : UInt<$maskBits>"
      case _ => ""
    }
    val extraPortsStr = extraPorts.map { case (name, bits) => s"    input $name : UInt<$bits>" }.mkString("\n")
    s"""
    input ${realPrefix}addr : UInt<$addrWidth>
    input ${realPrefix}clk : Clock
    $writeStr
    $readStr
    $readEnableStr
    $writeEnableStr
    $maskStr
$extraPortsStr
  """
  }

  /** Helper function to generate a RW footer port.
    *
    * @param prefix     Memory port prefix (e.g. "x" for ports like "x_clk")
    * @param readEnable Has a read enable port?
    * @param mask       Mask granularity (# bits) of the port or None.
    * @param extraPorts Extra ports (name, # bits)
    */
  def generateReadWriteFooterPort(
    prefix:     String,
    readEnable: Boolean,
    mask:       Option[Int],
    extraPorts: Seq[(String, Int)] = Seq()
  ): String = {
    generatePort(
      prefix,
      lib_addr_width,
      libWidth,
      write = true,
      writeEnable = true,
      read = true,
      readEnable = readEnable,
      mask = mask,
      extraPorts = extraPorts
    )
  }

  /** Helper function to generate a RW header port.
    * @param prefix Memory port prefix (e.g. "x" for ports like "x_clk")
    * @param readEnable Has a read enable port?
    * @param mask Mask granularity (# bits) of the port or None.
    */
  def generateReadWriteHeaderPort(prefix: String, readEnable: Boolean, mask: Option[Int]): String = {
    generatePort(
      prefix,
      mem_addr_width,
      memWidth,
      write = true,
      writeEnable = true,
      read = true,
      readEnable = readEnable,
      mask
    )
  }

  // Generate the header memory ports.
  def generateHeaderPorts(): String = {
    require(memSRAM.ports.size == 1, "Header generator only supports single RW port mem")
    generateReadWriteHeaderPort(
      memPortPrefix,
      memSRAM.ports.head.readEnable.isDefined,
      if (memHasMask) Some(memMaskBits) else None
    )
  }

  // Generate the header (contains the circuit statement and the target memory
  // module.
  def generateHeader(): String = {
    s"""
circuit $mem_name :
  module $mem_name :
${generateHeaderPorts()}
  """
  }

  // Generate the target memory ports.
  def generateFooterPorts(): String = {
    require(libSRAM.ports.size == 1, "Footer generator only supports single RW port mem")
    generateReadWriteFooterPort(
      libPortPrefix,
      libSRAM.ports.head.readEnable.isDefined,
      if (libHasMask) Some(libMaskBits) else None,
      extraPorts.map(p => (p.name, p.width))
    )
  }

  // Generate the footer (contains the target memory extmodule declaration by default).
  def generateFooter(): String = {
    s"""
  extmodule $lib_name :
${generateFooterPorts()}

    defname = $lib_name
  """
  }

  // Abstract method to generate body; to be overridden by specific generator type.
  def generateBody(): String

  // Generate the entire output from header, body, and footer.
  def generateOutput(): String = {
    s"""
${generateHeader()}
${generateBody()}
${generateFooter()}
      """
  }

  val output: String = generateOutput()
}

// Use this trait for tests that invoke the memory compiler without lib.
trait HasNoLibTestGenerator extends HasSimpleTestGenerator {
  this: MacroCompilerSpec with HasSRAMGenerator =>

  // If there isn't a lib, then the "lib" will become a FIRRTL "mem", which
  // in turn becomes synthesized flops.
  // Therefore, make "lib" width/depth equal to the mem.
  override lazy val libDepth: BigInt = memDepth
  override lazy val libWidth: Int = memWidth
  override lazy val lib_name: String = mem_name
  // Do the same for port names.
  override lazy val libPortPrefix: String = memPortPrefix

  // If there is no lib, don't generate a body.
  override def generateBody() = ""
}
