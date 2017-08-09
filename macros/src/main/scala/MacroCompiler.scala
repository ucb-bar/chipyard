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

case class MacroCompilerException(msg: String) extends Exception(msg)

object MacroCompilerAnnotation {
  /** Macro compiler mode. */
  sealed trait CompilerMode
  /** Strict mode - must compile all memories or error out. */
  case object Strict extends CompilerMode
  /** Synflops mode - compile all memories with synflops (do not map to lib at all). */
  case object Synflops extends CompilerMode
  /** FallbackSynflops - compile all memories to SRAM when possible and fall back to synflops if a memory fails. **/
  case object FallbackSynflops extends CompilerMode
  /** Default mode - compile what is possible and do nothing with uncompiled memories. **/
  case object Default extends CompilerMode
  def stringToCompilerMode(str: String): CompilerMode = (str: @unchecked) match {
    case "strict" => Strict
    case "synflops" => Synflops
    case "fallbacksynflops" => FallbackSynflops
    case "default" => Default
    case _ => throw new IllegalArgumentException("No such compiler mode " + str)
  }

  /**
   * Parameters associated to this MacroCompilerAnnotation.
   * @param mem Path to memory lib
   * @param lib Path to library lib or None if no libraries
   * @param costMetric Cost metric to use
   * @param mode Compiler mode (see CompilerMode)
   */
  case class Params(mem: String, lib: Option[String], costMetric: CostMetric, mode: CompilerMode)

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
                        costMetric: CostMetric = CostMetric.default,
                        mode: MacroCompilerAnnotation.CompilerMode = MacroCompilerAnnotation.Default) extends firrtl.passes.Pass {
  def compile(mem: Macro, lib: Macro): Option[(Module, ExtModule)] = {
    val pairedPorts = mem.sortedPorts zip lib.sortedPorts

    // Parallel mapping

    /**
     * This is a list of submemories by width.
     * The tuples are (lsb, msb) inclusive.
     * e.g. (0, 7) and (8, 15) might be a split for a width=16 memory into two
     * width=8 memories.
     */
    val bitPairs = ArrayBuffer[(BigInt, BigInt)]()
    var currentLSB = 0

    // Process every bit in the mem width.
    for (memBit <- 0 until mem.src.width) {
      val bitsInCurrentMem = memBit - currentLSB

      // Helper function to check if it's time to split memories.
      // @param effectiveLibWidth Split memory when we have this many bits.
      def splitMemory(effectiveLibWidth: Int): Unit = {
        if (bitsInCurrentMem == effectiveLibWidth) {
          bitPairs += ((currentLSB, memBit - 1))
          currentLSB = memBit
        }
      }

      for ((memPort, libPort) <- pairedPorts) {

        // Make sure we don't have a maskGran larger than the width of the memory.
        assert (memPort.src.effectiveMaskGran <= memPort.src.width)
        assert (libPort.src.effectiveMaskGran <= libPort.src.width)

        val libWidth = libPort.src.width

        // Don't consider cases of maskGran == width as "masked" since those masks
        // effectively function as write-enable bits.
        val memMask = if (memPort.src.effectiveMaskGran == memPort.src.width) None else memPort.src.maskGran
        val libMask = if (libPort.src.effectiveMaskGran == libPort.src.width) None else libPort.src.maskGran

        (memMask, libMask) match {
          // Neither lib nor mem is masked.
          // No problems here.
          case (None, None) => splitMemory(libWidth)

          // Only the lib is masked.
          // Not an issue; we can just make all the bits in the lib mask enabled.
          case (None, Some(p)) => splitMemory(libWidth)

          // Only the mem is masked.
          case (Some(p), None) => {
            if (p % libPort.src.width == 0) {
              // If the mem mask is a multiple of the lib width, then we're good.
              // Just roll over every lib width as usual.
              // e.g. lib width=4, mem maskGran={4, 8, 12, 16, ...}
              splitMemory(libWidth)
            } else if (libPort.src.width % p == 0) {
              // Lib width is a multiple of the mem mask.
              // Consider the case where mem mask = 4 but lib width = 8, unmasked.
              // We can still compile, but will need to waste the extra bits.
              splitMemory(memMask.get)
            } else {
              // No neat multiples.
              // We might still be able to compile extremely inefficiently.
              if (p < libPort.src.width) {
                // Compile using mem mask as the effective width. (note that lib is not masked)
                // e.g. mem mask = 3, lib width = 8
                splitMemory(memMask.get)
              } else {
                // e.g. mem mask = 13, lib width = 8
                System.err.println(s"Unmasked target memory: unaligned mem maskGran ${p} with lib (${lib.src.name}) width ${libPort.src.width} not supported")
                return None
              }
            }
          }

          // Both lib and mem are masked.
          case (Some(m), Some(l)) => {
            if (m == l) {
              // Lib maskGran == mem maskGran, no problems
              splitMemory(libWidth)
            } else if (m > l) {
              // Mem maskGran > lib maskGran
              if (m % l == 0) {
                // Mem maskGran is a multiple of lib maskGran, carry on as normal.
                splitMemory(libWidth)
              } else {
                System.err.println(s"Mem maskGran ${m} is not a multiple of lib maskGran ${l}: currently not supported")
                return None
              }
            } else { // m < l
              // Lib maskGran > mem maskGran.
              if (l % m == 0) {
                // Lib maskGran is a multiple of mem maskGran.
                // e.g. lib maskGran = 8, mem maskGran = 4.
                // In this case we can only compile very wastefully (by treating
                // lib as a mem maskGran width memory) :(
                splitMemory(memMask.get)
              } else {
                System.err.println(s"Lib maskGran ${m} is not a multiple of mem maskGran ${l}: currently not supported")
                return None
              }
            }
          }
        }
      }
    }
    // Add in the last chunk if there are any leftovers
    bitPairs += ((currentLSB, mem.src.width.toInt - 1))

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
      for (j <- bitPairs.indices) {
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
        for (((low, high), j) <- bitPairs.zipWithIndex) {
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
                require(isPowerOfTwo(libPort.src.effectiveMaskGran), "only powers of two masks supported for now")

                val effectiveLibWidth = if (memPort.src.maskGran.get < libPort.src.effectiveMaskGran) memPort.src.maskGran.get else libPort.src.width
                cat(((0 until libPort.src.width by libPort.src.effectiveMaskGran) map (i => {
                  if (memPort.src.maskGran.get < libPort.src.effectiveMaskGran && i >= effectiveLibWidth) {
                    // If the memMaskGran is smaller than the lib's gran, then
                    // zero out the upper bits.
                    zero
                  } else {
                    bits(WRef(mem), (low + i) / memPort.src.effectiveMaskGran)
                  }
                })).reverse)
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
                // Try compiling
                compile(mem, lib) match {
                  // If it was successful and the new cost is lower
                  case Some(p) if (newCost < cost) => (Some(p), newCost)
                  case _ => (best, cost)
                }
              }
              case _ => (best, cost) // Cost function rejected this combination.
            }
        }

        // If we were able to compile anything, then replace the original module
        // in the modules list with a compiled version, as well as the extmodule
        // stub for the lib.
        best match {
          case None => {
            if (mode == MacroCompilerAnnotation.Strict)
              throw new MacroCompilerException(s"Target memory ${mem.src.name} could not be compiled and strict mode is activated - aborting.")
            else
              modules
          }
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
    case Seq(MacroCompilerAnnotation(state.circuit.main, MacroCompilerAnnotation.Params(memFile, libFile, costMetric, mode))) =>
      if (mode == MacroCompilerAnnotation.FallbackSynflops) {
        throw new UnsupportedOperationException("Not implemented yet")
      }
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
        new MacroCompilerPass(mems, libs, costMetric, mode),
        new SynFlopsPass(mode == MacroCompilerAnnotation.Synflops, libs getOrElse mems.get))
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
  case object Mode extends MacroParam
  type MacroParamMap = Map[MacroParam, String]
  type CostParamMap = Map[String, String]
  val usage = Seq(
    "Options:",
    "  -m, --macro-list: The set of macros to compile",
    "  -l, --library: The set of macros that have blackbox instances",
    "  -v, --verilog: Verilog output",
    "  -c, --cost-func: Cost function to use. Optional (default: \"default\")",
    "  -cp, --cost-param: Cost function parameter. (Optional depending on the cost function.). e.g. -c ExternalMetric -cp path /path/to/my/cost/script",
  """  --mode:
    |    synflops: Produces synthesizable flop-based memories (for all memories and library memory macros); likely useful for simulation purposes")
    |    fallbacksynflops: Compile all memories to library when possible and fall back to synthesizable flop-based memories when library synth is not possible
    |    strict: Compile all memories to library or return an error
    |    default: Compile all memories to library when possible and do nothing in case of errors.
  """.stripMargin) mkString "\n"

  def parseArgs(map: MacroParamMap, costMap: CostParamMap, args: List[String]): (MacroParamMap, CostParamMap) =
    args match {
      case Nil => (map, costMap)
      case ("-m" | "--macro-list") :: value :: tail =>
        parseArgs(map + (Macros  -> value), costMap, tail)
      case ("-l" | "--library") :: value :: tail =>
        parseArgs(map + (Library -> value), costMap, tail)
      case ("-v" | "--verilog") :: value :: tail =>
        parseArgs(map + (Verilog -> value), costMap, tail)
      case ("-c" | "--cost-func") :: value :: tail =>
        parseArgs(map + (CostFunc -> value), costMap, tail)
      case ("-cp" | "--cost-param") :: value1 :: value2 :: tail =>
        parseArgs(map, costMap + (value1 -> value2), tail)
      case "--mode" :: value :: tail =>
        parseArgs(map + (Mode -> value), costMap, tail)
      case arg :: tail =>
        println(s"Unknown field $arg\n")
        println(usage)
        sys.exit(1)
    }

  def run(args: List[String]) {
    val (params, costParams) = parseArgs(Map[MacroParam, String](), Map[String, String](), args)
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
              MacroCompilerAnnotation.stringToCompilerMode(params.getOrElse(Mode, "default"))
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
      case e: MacroCompilerException =>
        System.err.println(e.getMessage)
        sys.exit(1)
      case e: Throwable =>
        throw e
    }
  }

  run(args.toList)
}
