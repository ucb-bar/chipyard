// See LICENSE for license details.

/**
 * Terminology note:
 * mem - target memory to compile, in design (e.g. Mem() in rocket)
 * lib - technology SRAM(s) to use to compile mem
 */

package barstools.macros

import firrtl._
import firrtl.ir._
import firrtl.PrimOps
import firrtl.Utils._
import firrtl.annotations._
import firrtl.CompilerUtils.getLoweringTransforms
import mdf.macrolib.{PolarizedPort, PortPolarity}
import scala.collection.mutable.{ArrayBuffer, HashMap}
import java.io.{File, FileWriter}
import Utils._

/**
 * Trait which can calculate the cost of compiling a memory against a certain
 * library memory macro using a cost function.
 */
// TODO: eventually explore compiling a single target memory using multiple
// different kinds of target memory.
trait CostMetric extends Serializable {
  /**
   * Cost function that returns the cost of compiling a memory using a certain
   * macro.
   *
   * @param mem Memory macro to compile (target memory)
   * @param lib Library memory macro to use (library memory)
   * @return The cost of this compile, defined by this cost metric, or None if
   *         it cannot be compiled.
   */
  def cost(mem: Macro, lib: Macro): Option[BigInt]

  /**
   * Helper function to return the map of argments (or an empty map if there are none).
   */
  def commandLineParams(): Map[String, String]

  // We also want this to show up for the class itself.
  def name(): String
}

// Is there a better way to do this? (static method associated to CostMetric)
trait CostMetricCompanion {
  def name(): String

  /** Construct this cost metric from a command line mapping. */
  def construct(m: Map[String, String]): CostMetric
}

// Some default cost functions.

/** Palmer's old metric. */
object PalmerMetric extends CostMetric with CostMetricCompanion {
  override def cost(mem: Macro, lib: Macro): Option[BigInt] = {
    /* Palmer: A quick cost function (that must be kept in sync with
     * memory_cost()) that attempts to avoid compiling unncessary
     * memories.  This is a lower bound on the cost of compiling a
     * memory: it assumes 100% bit-cell utilization when mapping. */
    // val cost = 100 * (mem.depth * mem.width) / (lib.depth * lib.width) +
    //                  (mem.depth * mem.width)
    ???
  }

  override def commandLineParams = Map()
  override def name = "PalmerMetric"
  override def construct(m: Map[String, String]) = PalmerMetric
}

/**
 * An external cost function.
 * Calls the specified path with paths to the JSON MDF representation of the mem
 * and lib macros. The external executable should return a BigInt.
 * None will be returned if the external executable does not return a valid
 * BigInt.
 */
class ExternalMetric(path: String) extends CostMetric {
  import mdf.macrolib.Utils.writeMacroToPath
  import java.io._
  import scala.language.postfixOps // for !! postfix op
  import sys.process._

  override def cost(mem: Macro, lib: Macro): Option[BigInt] = {
    // Create temporary files.
    val memFile = File.createTempFile("_macrocompiler_mem_", ".json")
    val libFile = File.createTempFile("_macrocompiler_lib_", ".json")

    writeMacroToPath(Some(memFile.getAbsolutePath), mem.src)
    writeMacroToPath(Some(libFile.getAbsolutePath), lib.src)

    // !! executes the given command
    val result: String = (s"${path} ${memFile.getAbsolutePath} ${libFile.getAbsolutePath}" !!).trim

    // Remove temporary files.
    memFile.delete()
    libFile.delete()

    try {
      Some(BigInt(result))
    } catch {
      case e: NumberFormatException => None
    }
  }

  override def commandLineParams = Map("path" -> path)
  override def name = ExternalMetric.name
}

object ExternalMetric extends CostMetricCompanion {
  override def name = "ExternalMetric"

  /** Construct this cost metric from a command line mapping. */
  override def construct(m: Map[String, String]) = {
    val pathOption = m.get("path")
    pathOption match {
      case Some(path:String) => new ExternalMetric(path)
      case _ => throw new IllegalArgumentException("ExternalMetric missing option 'path'")
    }
  }
}

/** The current default metric in barstools, re-defined by Donggyu. */
// TODO: write tests for this function to make sure it selects the right things
object NewDefaultMetric extends CostMetric with CostMetricCompanion {
  override def cost(mem: Macro, lib: Macro): Option[BigInt] = {
    val memMask = mem.src.ports map (_.maskGran) find (_.isDefined) map (_.get)
    val libMask = lib.src.ports map (_.maskGran) find (_.isDefined) map (_.get)
    val memWidth = (memMask, libMask) match {
      case (Some(1), Some(1)) | (None, _) => mem.src.width
      case (Some(p), _) => p // assume that the memory consists of smaller chunks
    }
    return Some(
      (((mem.src.depth - 1) / lib.src.depth) + 1) *
      (((memWidth - 1) / lib.src.width) + 1) *
      (lib.src.depth * lib.src.width + 1) // weights on # cells
    )
  }

  override def commandLineParams = Map()
  override def name = "NewDefaultMetric"
  override def construct(m: Map[String, String]) = NewDefaultMetric
}

object MacroCompilerUtil {
  import java.io._
  import java.util.Base64

  // Adapted from https://stackoverflow.com/a/134918

  /** Serialize an arbitrary object to String.
   *  Used to pass structured values through as an annotation. */
  def objToString(o: Serializable): String = {
    val baos: ByteArrayOutputStream = new ByteArrayOutputStream
    val oos: ObjectOutputStream = new ObjectOutputStream(baos)
    oos.writeObject(o)
    oos.close()
    return Base64.getEncoder.encodeToString(baos.toByteArray)
  }

  /** Deserialize an arbitrary object from String. */
  def objFromString(s: String): AnyRef = {
    val data = Base64.getDecoder.decode(s)
    val ois: ObjectInputStream = new ObjectInputStream(new ByteArrayInputStream(data))
    val o = ois.readObject
    ois.close()
    return o
  }
}

object CostMetric {
  /** Define some default metric. */
  val default: CostMetric = NewDefaultMetric

  val costMetricCreators: scala.collection.mutable.Map[String, CostMetricCompanion] = scala.collection.mutable.Map()

  // Register some default metrics
  registerCostMetric(PalmerMetric)
  registerCostMetric(ExternalMetric)
  registerCostMetric(NewDefaultMetric)

  /**
   * Register a cost metric.
   * @param createFuncHelper Companion object to fetch the name and construct
   *                         the metric.
   */
  def registerCostMetric(createFuncHelper: CostMetricCompanion): Unit = {
    costMetricCreators.update(createFuncHelper.name, createFuncHelper)
  }

  /** Select a cost metric from string. */
  def getCostMetric(m: String, params: Map[String, String]): CostMetric = {
    if (m == "default") {
      CostMetric.default
    } else if (!costMetricCreators.contains(m)) {
      throw new IllegalArgumentException("Invalid cost metric " + m)
    } else {
      costMetricCreators.get(m).get.construct(params)
    }
  }
}

object MacroCompilerAnnotation {
  /**
   * Parameters associated to this MacroCompilerAnnotation.
   * @param mem Path to memory lib
   * @param lib Path to library lib or None if no libraries
   * @param costMetric Cost metric to use
   * @param synflops True to syn flops
   */
  case class Params(mem: String, lib: Option[String], costMetric: CostMetric, synflops: Boolean)

  /**
   * Create a MacroCompilerAnnotation.
   * @param c Name of the module(?) for this annotation.
   * @param p Parameters (see above).
   */
  def apply(c: String, p: Params): Annotation =
    Annotation(CircuitName(c), classOf[MacroCompilerTransform], MacroCompilerUtil.objToString(p))

  def unapply(a: Annotation) = a match {
    case Annotation(CircuitName(c), t, serialized) if t == classOf[MacroCompilerTransform] => {
      val p: Params = MacroCompilerUtil.objFromString(serialized).asInstanceOf[Params]
      Some(c, p)
    }
    case _ => None
  }
}

class MacroCompilerPass(mems: Option[Seq[Macro]],
                        libs: Option[Seq[Macro]],
                        costMetric: CostMetric = CostMetric.default) extends firrtl.passes.Pass {
  def compile(mem: Macro, lib: Macro): Option[(Module, ExtModule)] = {
    val pairedPorts = mem.sortedPorts zip lib.sortedPorts

    // Parallel mapping
    val pairs = ArrayBuffer[(BigInt, BigInt)]()
    var last = 0
    for (i <- 0 until mem.src.width) {
      if (i <= last + 1) {
        /* Palmer: Every memory is going to have to fit at least a single bit. */
        // continue
      } else if ((i - last) % lib.src.width.toInt == 0) {
        /* Palmer: It's possible that we rolled over a memory's width here,
                   if so generate one. */
        pairs += ((last, i-1))
        last = i
      } else {
        /* Palmer: FIXME: This is a mess, I must just be super confused. */
        for ((memPort, libPort) <- pairedPorts) {
          (memPort.src.maskGran, libPort.src.maskGran) match {
            case (_, Some(p)) if p == 1 => // continue
            case (Some(p), _) if i % p == 0 =>
              pairs += ((last, i-1))
              last = i
            case (_, None) => // continue
            case (_, Some(p)) if p == lib.src.width => // continue
            case _ =>
              System.err println "Bit-mask (or unmasked) target memories are supported only"
              return None
          }
        }
      }
    }
    pairs += ((last, mem.src.width.toInt - 1))

    // Serial mapping
    val stmts = ArrayBuffer[Statement]()
    val outputs = HashMap[String, ArrayBuffer[(Expression, Expression)]]()
    val selects = HashMap[String, Expression]()
    val selectRegs = HashMap[String, Expression]()
    /* Palmer: If we've got a parallel memory then we've got to take the
     * address bits into account. */
    if (mem.src.depth > lib.src.depth) {
      mem.src.ports foreach { port =>
        val high = ceilLog2(mem.src.depth)
        val low = ceilLog2(lib.src.depth)
        val ref = WRef(port.address.name)
        val nodeName = s"${ref.name}_sel"
        val tpe = UIntType(IntWidth(high-low))
        selects(ref.name) = WRef(nodeName, tpe)
        stmts += DefNode(NoInfo, nodeName, bits(ref, high-1, low))
        // Donggyu: output selection should be piped
        if (port.output.isDefined) {
          val regName = s"${ref.name}_sel_reg"
          val enable = (port.chipEnable, port.readEnable) match {
            case (Some(ce), Some(re)) =>
              and(WRef(ce.name, BoolType), WRef(re.name, BoolType))
            case (Some(ce), None) => WRef(ce.name, BoolType)
            case (None, Some(re)) => WRef(re.name, BoolType)
            case (None, None) => one
          }
          selectRegs(ref.name) = WRef(regName, tpe)
          stmts += DefRegister(NoInfo, regName, tpe, WRef(port.clock.name), zero, WRef(regName))
          stmts += Connect(NoInfo, WRef(regName), Mux(enable, WRef(nodeName), WRef(regName), tpe))
        }
      }
    }
    for ((off, i) <- (0 until mem.src.depth by lib.src.depth).zipWithIndex) {
      for (j <- pairs.indices) {
        val name = s"mem_${i}_${j}"
        stmts += WDefInstance(NoInfo, name, lib.src.name, lib.tpe)
        // connect extra ports
        stmts ++= lib.extraPorts map { case (portName, portValue) =>
          Connect(NoInfo, WSubField(WRef(name), portName), portValue)
        }
      }
      for ((memPort, libPort) <- pairedPorts) {
        val addrMatch = selects get memPort.src.address.name match {
          case None => one
          case Some(addr) =>
            val index = UIntLiteral(i, IntWidth(bitWidth(addr.tpe)))
            DoPrim(PrimOps.Eq, Seq(addr, index), Nil, index.tpe)
        }
        val addrMatchReg = selectRegs get memPort.src.address.name match {
          case None => one
          case Some(reg) =>
            val index = UIntLiteral(i, IntWidth(bitWidth(reg.tpe)))
            DoPrim(PrimOps.Eq, Seq(reg, index), Nil, index.tpe)
        }
        def andAddrMatch(e: Expression) = {
          and(e, addrMatch)
        }
        val cats = ArrayBuffer[Expression]()
        for (((low, high), j) <- pairs.zipWithIndex) {
          val inst = WRef(s"mem_${i}_${j}", lib.tpe)

          def connectPorts2(mem: Expression,
                           lib: String,
                           polarity: Option[PortPolarity]): Statement =
            Connect(NoInfo, WSubField(inst, lib), portToExpression(mem, polarity))
          def connectPorts(mem: Expression,
                           lib: String,
                           polarity: PortPolarity): Statement =
            connectPorts2(mem, lib, Some(polarity))

          // Clock port mapping
          /* Palmer: FIXME: I don't handle memories with read/write clocks yet. */
          stmts += connectPorts(WRef(memPort.src.clock.name),
                                libPort.src.clock.name,
                                libPort.src.clock.polarity)

          // Adress port mapping
          /* Palmer: The address port to a memory is just the low-order bits of
           * the top address. */
          stmts += connectPorts(WRef(memPort.src.address.name),
                                libPort.src.address.name,
                                libPort.src.address.polarity)

          // Output port mapping
          (memPort.src.output, libPort.src.output) match {
            case (Some(PolarizedPort(mem, _)), Some(PolarizedPort(lib, lib_polarity))) =>
              /* Palmer: In order to produce the output of a memory we need to cat
               * together a bunch of narrower memories, which can only be
               * done after generating all the memories.  This saves up the
               * output statements for later. */
              val name = s"${mem}_${i}_${j}"
              val exp = portToExpression(bits(WSubField(inst, lib), high-low, 0), Some(lib_polarity))
              stmts += DefNode(NoInfo, name, exp)
              cats += WRef(name)
            case (None, Some(lib)) =>
              /* Palmer: If the inner memory has an output port but the outer
               * one doesn't then it's safe to just leave the outer
               * port floating. */
            case (None, None) =>
              /* Palmer: If there's no output ports at all (ie, read-only
               * port on the memory) then just don't worry about it,
               * there's nothing to do. */
            case (Some(PolarizedPort(mem, _)), None) =>
              System.err println "WARNING: Unable to match output ports on memory"
              System.err println s"  outer output port: ${mem}"
              return None
          }

          // Input port mapping
          (memPort.src.input, libPort.src.input) match {
            case (Some(PolarizedPort(mem, _)), Some(PolarizedPort(lib, lib_polarity))) =>
              /* Palmer: The input port to a memory just needs to happen in parallel,
               * this does a part select to narrow the memory down. */
              stmts += connectPorts(bits(WRef(mem), high, low), lib, lib_polarity)
            case (None, Some(lib)) =>
              /* Palmer: If the inner memory has an input port but the other
               * one doesn't then it's safe to just leave the inner
               * port floating.  This should be handled by the
               * default value of the write enable, so nothing should
               * every make it into the memory. */
            case (None, None) =>
              /* Palmer: If there's no input ports at all (ie, read-only
               * port on the memory) then just don't worry about it,
               * there's nothing to do. */
            case (Some(PolarizedPort(mem, _)), None) =>
              System.err println "WARNING: Unable to match input ports on memory"
              System.err println s"  outer input port: ${mem}"
              return None
          }

          // Mask port mapping
          val memMask = memPort.src.maskPort match {
            case Some(PolarizedPort(mem, _)) =>
              /* Palmer: The bits from the outer memory's write mask that will be
               * used as the write mask for this inner memory. */
              if (libPort.src.effectiveMaskGran == libPort.src.width) {
                bits(WRef(mem), low / memPort.src.effectiveMaskGran)
              } else {
                require(libPort.src.effectiveMaskGran == 1, "only single-bit mask supported for now")

                require(isPowerOfTwo(memPort.src.effectiveMaskGran), "only powers of two masks supported for now")
                require(isPowerOfTwo(libPort.src.effectiveMaskGran), "only powers of two masks supported for now")

                cat(((low to high) map (i => bits(WRef(mem), i / memPort.src.effectiveMaskGran))).reverse)
              }
            case None =>
              /* Palmer: If there is no input port on the source memory port
               * then we don't ever want to turn on this write
               * enable.  Otherwise, we just _always_ turn on the
               * write enable port on the inner memory. */
              if (libPort.src.maskPort.isEmpty) one
              else {
                val width = libPort.src.width / libPort.src.effectiveMaskGran
                val value = (BigInt(1) << width.toInt) - 1
                UIntLiteral(value, IntWidth(width))
              }
          }

          // Write enable port mapping
          val memWriteEnable = memPort.src.writeEnable match {
            case Some(PolarizedPort(mem, _)) =>
              /* Palmer: The outer memory's write enable port, or a constant 1 if
               * there isn't a write enable port. */
              WRef(mem)
            case None =>
              /* Palmer: If there is no input port on the source memory port
               * then we don't ever want to turn on this write
               * enable.  Otherwise, we just _always_ turn on the
               * write enable port on the inner memory. */
              if (memPort.src.input.isEmpty) zero else one
          }

          // Chip enable port mapping
          val memChipEnable = memPort.src.chipEnable match {
            case Some(PolarizedPort(mem, _)) => WRef(mem)
            case None      => one
          }

          // Read enable port mapping
          /* Palmer: It's safe to ignore read enables, but we pass them through
           * to the vendor memory if there's a port on there that
           * implements the read enables. */
          (memPort.src.readEnable, libPort.src.readEnable) match {
            case (_, None) =>
            case (Some(PolarizedPort(mem, _)), Some(PolarizedPort(lib, lib_polarity))) =>
              stmts += connectPorts(andAddrMatch(WRef(mem)), lib, lib_polarity)
            case (None, Some(PolarizedPort(lib, lib_polarity))) =>
              stmts += connectPorts(andAddrMatch(not(memWriteEnable)), lib, lib_polarity)
          }

          /* Palmer: This is actually the memory compiler: it figures out how to
           * implement the outer memory's collection of ports using what
           * the inner memory has availiable. */
          ((libPort.src.maskPort, libPort.src.writeEnable, libPort.src.chipEnable): @unchecked) match {
            case (Some(PolarizedPort(mask, mask_polarity)), Some(PolarizedPort(we, we_polarity)), Some(PolarizedPort(en, en_polarity))) =>
              /* Palmer: This is the simple option: every port exists. */
              stmts += connectPorts(memMask, mask, mask_polarity)
              stmts += connectPorts(andAddrMatch(memWriteEnable), we, we_polarity)
              stmts += connectPorts(andAddrMatch(memChipEnable), en, en_polarity)
            case (Some(PolarizedPort(mask, mask_polarity)), Some(PolarizedPort(we, we_polarity)), None) =>
              /* Palmer: If we don't have a chip enable but do have mask ports. */
              stmts += connectPorts(memMask, mask, mask_polarity)
              stmts += connectPorts(andAddrMatch(and(memWriteEnable, memChipEnable)),
                                    we, mask_polarity)
            case (None, Some(PolarizedPort(we, we_polarity)), chipEnable) if bitWidth(memMask.tpe) == 1 =>
              /* Palmer: If we're expected to provide mask ports without a
               * memory that actually has them then we can use the
               * write enable port instead of the mask port. */
              stmts += connectPorts(andAddrMatch(and(memWriteEnable, memMask)),
                                    we, we_polarity)
              chipEnable match {
                case Some(PolarizedPort(en, en_polarity)) => {
                  stmts += connectPorts(andAddrMatch(memChipEnable), en, en_polarity)
                }
                case _ => // TODO: do we care about the case where mem has chipEnable but lib doesn't?
              }
            case (None, Some(PolarizedPort(we, we_polarity)), Some(PolarizedPort(en, en_polarity))) =>
              // TODO
              System.err.println("cannot emulate multi-bit mask ports with write enable")
              return None
            case (None, None, None) =>
              /* Palmer: There's nothing to do here since there aren't any
               * ports to match up. */
          }
        }
        // Cat macro outputs for selection
        memPort.src.output match {
          case Some(PolarizedPort(mem, _)) if cats.nonEmpty =>
            val name = s"${mem}_${i}"
            stmts += DefNode(NoInfo, name, cat(cats.toSeq.reverse))
            (outputs getOrElseUpdate (mem, ArrayBuffer[(Expression, Expression)]())) +=
              (addrMatchReg -> WRef(name))
          case _ =>
        }
      }
    }
    // Connect mem outputs
    mem.src.ports foreach { port =>
      port.output match {
        case Some(PolarizedPort(mem, _)) => outputs get mem match {
          case Some(select) =>
            val output = (select foldRight (zero: Expression)) {
              case ((cond, tval), fval) => Mux(cond, tval, fval, fval.tpe) }
            stmts += Connect(NoInfo, WRef(mem), output)
          case None =>
        }
        case None =>
      }
    }

    Some((mem.module(Block(stmts.toSeq)), lib.blackbox))
  }

  def run(c: Circuit): Circuit = {
    val modules = (mems, libs) match {
      case (Some(mems), Some(libs)) =>
        // Try to compile each of the memories in mems.
        // The 'state' is c.modules, which is a list of all the firrtl modules
        // in the 'circuit'.
        (mems foldLeft c.modules){ (modules, mem) =>

        // Try to compile mem against each lib in libs, keeping track of the
        // best compiled version, external lib used, and cost.
        val (best, cost) = (libs foldLeft (None: Option[(Module, ExtModule)], BigInt(Long.MaxValue))){
          case ((best, cost), lib) if mem.src.ports.size != lib.src.ports.size =>
            /* Palmer: FIXME: This just assumes the Chisel and vendor ports are in the same
             * order, but I'm starting with what actually gets generated. */
            System.err println s"INFO: unable to compile ${mem.src.name} using ${lib.src.name} port count must match"
            (best, cost)
          case ((best, cost), lib) =>
            // Run the cost function to evaluate this potential compile.
            costMetric.cost(mem, lib) match {
              case Some(newCost) => {
                System.err.println(s"Cost of ${lib.src.name} for ${mem.src.name}: ${newCost}")
                if (newCost > cost) (best, cost)
                else compile(mem, lib) match {
                  case None => (best, cost)
                  case Some(p) => (Some(p), newCost)
                }
              }
              case _ => (best, cost) // Cost function rejected this combination.
            }
        }

        // If we were able to compile anything, then replace the original module
        // in the modules list with a compiled version, as well as the extmodule
        // stub for the lib.
        best match {
          case None => modules
          case Some((mod, bb)) =>
            (modules filterNot (m => m.name == mod.name || m.name == bb.name)) ++ Seq(mod, bb)
        }
      }
      case _ => c.modules
    }
    c.copy(modules = modules)
  }
}

class MacroCompilerTransform extends Transform {
  def inputForm = MidForm
  def outputForm = MidForm
  def execute(state: CircuitState) = getMyAnnotations(state) match {
    case Seq(MacroCompilerAnnotation(state.circuit.main, MacroCompilerAnnotation.Params(memFile, libFile, costMetric, synflops))) =>
      // Read, eliminate None, get only SRAM, make firrtl macro
      val mems: Option[Seq[Macro]] = mdf.macrolib.Utils.readMDFFromPath(Some(memFile)) match {
        case Some(x:Seq[mdf.macrolib.Macro]) =>
          Some(Utils.filterForSRAM(Some(x)) getOrElse(List()) map {new Macro(_)})
        case _ => None
      }
      val libs: Option[Seq[Macro]] = mdf.macrolib.Utils.readMDFFromPath(libFile) match {
        case Some(x:Seq[mdf.macrolib.Macro]) =>
          Some(Utils.filterForSRAM(Some(x)) getOrElse(List()) map {new Macro(_)})
        case _ => None
      }
      val transforms = Seq(
        new MacroCompilerPass(mems, libs, costMetric),
        new SynFlopsPass(synflops, libs getOrElse mems.get))
      (transforms foldLeft state)((s, xform) => xform runTransform s).copy(form=outputForm)
    case _ => state
  }
}

// FIXME: Use firrtl.LowerFirrtlOptimizations
class MacroCompilerOptimizations extends SeqTransform {
  def inputForm = LowForm
  def outputForm = LowForm
  def transforms = Seq(
    passes.RemoveValidIf,
    new firrtl.transforms.ConstantPropagation,
    passes.memlib.VerilogMemDelays,
    new firrtl.transforms.ConstantPropagation,
    passes.Legalize,
    passes.SplitExpressions,
    passes.CommonSubexpressionElimination)
}

class MacroCompiler extends Compiler {
  def emitter = new VerilogEmitter
  def transforms =
    Seq(new MacroCompilerTransform) ++
    getLoweringTransforms(firrtl.HighForm, firrtl.LowForm) ++
    Seq(new MacroCompilerOptimizations)
}

object MacroCompiler extends App {
  sealed trait MacroParam
  case object Macros extends MacroParam
  case object Library extends MacroParam
  case object Verilog extends MacroParam
  case object CostFunc extends MacroParam
  type MacroParamMap = Map[MacroParam, String]
  type CostParamMap = Map[String, String]
  val usage = Seq(
    "Options:",
    "  -m, --macro-list: The set of macros to compile",
    "  -l, --library: The set of macros that have blackbox instances",
    "  -v, --verilog: Verilog output",
    "  -c, --cost-func: Cost function to use. Optional (default: \"default\")",
    "  -cp, --cost-param: Cost function parameter. (Optional depending on the cost function.). e.g. -c ExternalMetric -cp path /path/to/my/cost/script",
    "  --syn-flops: Produces synthesizable flop-based memories (for all memories and library memory macros); likely useful for simulation purposes") mkString "\n"

  def parseArgs(map: MacroParamMap, costMap: CostParamMap, synflops: Boolean, args: List[String]): (MacroParamMap, CostParamMap, Boolean) =
    args match {
      case Nil => (map, costMap, synflops)
      case ("-m" | "--macro-list") :: value :: tail =>
        parseArgs(map + (Macros  -> value), costMap, synflops, tail)
      case ("-l" | "--library") :: value :: tail =>
        parseArgs(map + (Library -> value), costMap, synflops, tail)
      case ("-v" | "--verilog") :: value :: tail =>
        parseArgs(map + (Verilog -> value), costMap, synflops, tail)
      case ("-c" | "--cost-func") :: value :: tail =>
        parseArgs(map + (CostFunc -> value), costMap, synflops, tail)
      case ("-cp" | "--cost-param") :: value1 :: value2 :: tail =>
        parseArgs(map, costMap + (value1 -> value2), synflops, tail)
      case "--syn-flops" :: tail =>
        parseArgs(map, costMap, true, tail)
      case arg :: tail =>
        println(s"Unknown field $arg\n")
        println(usage)
        sys.exit(1)
    }

  def run(args: List[String]) {
    val (params, costParams, synflops) = parseArgs(Map[MacroParam, String](), Map[String, String](), false, args)
    try {
      val macros = Utils.filterForSRAM(mdf.macrolib.Utils.readMDFFromPath(params.get(Macros))).get map (x => (new Macro(x)).blackbox)

      // Open the writer for the output Verilog file.
      val verilogWriter = new FileWriter(new File(params.get(Verilog).get))

      if (macros.nonEmpty) {
        // Note: the last macro in the input list is (seemingly arbitrarily)
        // determined as the firrtl "top-level module".
        val circuit = Circuit(NoInfo, macros, macros.last.name)
        val annotations = AnnotationMap(
          Seq(MacroCompilerAnnotation(
            circuit.main,
            MacroCompilerAnnotation.Params(
              params.get(Macros).get, params.get(Library),
              CostMetric.getCostMetric(params.getOrElse(CostFunc, "default"), costParams),
              synflops
            )
          ))
        )
        val state = CircuitState(circuit, HighForm, Some(annotations))

        // Run the compiler.
        val result = new MacroCompiler().compileAndEmit(state)

        // Extract Verilog circuit and write it.
        verilogWriter.write(result.getEmittedCircuit.value)
      }

      // Close the writer.
      verilogWriter.close()

    } catch {
      case e: java.util.NoSuchElementException =>
        println(usage)
        sys.exit(1)
      case e: Throwable =>
        throw e
    }
  }

  run(args.toList)
}
