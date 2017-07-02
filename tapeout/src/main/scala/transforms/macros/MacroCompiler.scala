// See LICENSE for license details.

package barstools.tapeout.transforms.macros

import firrtl._
import firrtl.ir._
import firrtl.PrimOps
import firrtl.Utils._
import firrtl.annotations._
import firrtl.CompilerUtils.getLoweringTransforms
import scala.collection.mutable.{ArrayBuffer, HashMap}
import java.io.{File, FileWriter}
import Utils._

object MacroCompilerAnnotation {
  def apply(c: String, mem: File, lib: Option[File], synflops: Boolean) = {
    Annotation(CircuitName(c), classOf[MacroCompilerTransform],
      s"${mem} %s ${synflops}".format(lib map (_.toString) getOrElse ""))
  }
  private val matcher = "([^ ]+) ([^ ]*) (true|false)".r
  def unapply(a: Annotation) = a match {
    case Annotation(CircuitName(c), t, matcher(mem, lib, synflops)) if t == classOf[MacroCompilerTransform] =>
      Some((c, Some(new File(mem)), if (lib.isEmpty) None else Some(new File(lib)), synflops.toBoolean))
    case _ => None
  }
}

class MacroCompilerPass(memFile: Option[File],
                        libFile: Option[File]) extends firrtl.passes.Pass {
  require(memFile.isDefined)
  private val mems: Option[Seq[Macro]] = readJSON(memFile) map (_ map (x => new Macro(x)))
  private val libs: Option[Seq[Macro]] = readJSON(libFile) map (_ map (x => new Macro(x)))

  def compile(mem: Macro, lib: Macro): Option[(Module, ExtModule)] = {
    val pairedPorts = (
      (mem.ports filter (p => p.inputName.isDefined && !p.outputName.isDefined)) ++ // write
      (mem.ports filter (p => !p.inputName.isDefined && p.outputName.isDefined)) ++ // read
      (mem.ports filter (p => p.inputName.isDefined && p.outputName.isDefined)) // read writers
    ) zip (
      (lib.ports filter (p => p.inputName.isDefined && !p.outputName.isDefined)) ++ // write
      (lib.ports filter (p => !p.inputName.isDefined && p.outputName.isDefined)) ++ // read
      (lib.ports filter (p => p.inputName.isDefined && p.outputName.isDefined)) // read writers
    )

    // Parallel mapping
    val pairs = ArrayBuffer[(BigInt, BigInt)]()
    var last = 0
    for (i <- 0 until mem.width.toInt) {
      if (i <= last + 1) {
        /* Palmer: Every memory is going to have to fit at least a single bit. */
        // coninue
      } else if ((i - last) % lib.width.toInt == 0) {
        /* Palmer: It's possible that we rolled over a memory's width here,
                   if so generate one. */
        pairs += ((last, i-1))
        last = i
      } else {
        /* Palmer: FIXME: This is a mess, I must just be super confused. */
        for ((memPort, libPort) <- pairedPorts) {
          (memPort.maskGran, libPort.maskGran) match {
            case (_, Some(p)) if p == 1 => // continue
            case (Some(p), _) if i % p == 0 =>
              pairs += ((last, i-1))
              last = i
            case (_, None) => // continue
            case (_, Some(p)) if p == lib.width => // continue
            case _ =>
              System.err println "Bit-mask (or unmasked) target memories are suppored only"
              return None
          }
        }
      }
    }
    pairs += ((last, mem.width.toInt - 1))

    // Serial mapping
    val instType = BundleType(lib.ports flatMap (_.tpe.fields))
    val stmts = ArrayBuffer[Statement]()
    val selects = HashMap[String, Expression]()
    val outputs = HashMap[String, ArrayBuffer[(Expression, Expression)]]()
    /* Palmer: If we've got a parallel memory then we've got to take the
      * address bits into account. */
    if (mem.depth > lib.depth) {
      mem.ports foreach { port =>
        val high = ceilLog2(mem.depth)
        val low = ceilLog2(lib.depth)
        val ref = WRef(port.addressName)
        val name = s"${ref.name}_sel"
        selects(ref.name) =  WRef(name, UIntType(IntWidth(high-low)))
        stmts += DefNode(NoInfo, name, bits(ref, high-1, low))
      }
    }
    for ((off, i) <- (0 until mem.depth.toInt by lib.depth.toInt).zipWithIndex) {
      for (j <- pairs.indices) {
        stmts += WDefInstance(NoInfo, s"mem_${i}_${j}", lib.name, instType)
      }
      for ((memPort, libPort) <- pairedPorts) {
        val addrMatch = selects get memPort.addressName match {
          case None => one
          case Some(addr) =>
            val index = UIntLiteral(i, IntWidth(bitWidth(addr.tpe)))
            DoPrim(PrimOps.Eq, Seq(addr, index), Nil, index.tpe)
        }
        def andAddrMatch(e: Expression) = and(e, addrMatch)
        val cats = ArrayBuffer[Expression]()
        for (((low, high), j) <- pairs.zipWithIndex) {
          val inst = WRef(s"mem_${i}_${j}", instType)
          def invert(exp: Expression, polarity: Option[PortPolarity]) =
            polarity match {
              case Some(ActiveLow) | Some(NegativeEdge) => not(exp)
              case _ => exp
            }

          def connectPorts(mem: Expression,
                           lib: String,
                           polarity: Option[PortPolarity]): Statement =
            Connect(NoInfo, WSubField(inst, lib), invert(mem, polarity))

          // Clock port mapping
          /* Palmer: FIXME: I don't handle memories with read/write clocks yet. */
          stmts += connectPorts(WRef(memPort.clockName),
                                libPort.clockName,
                                libPort.clockPolarity)

          // Adress port mapping
          /* Palmer: The address port to a memory is just the low-order bits of
           * the top address. */
          stmts += connectPorts(WRef(memPort.addressName),
                                libPort.addressName,
                                libPort.addressPolarity)

          // Output port mapping
          (memPort.outputName, libPort.outputName) match {
            case  (Some(mem), Some(lib)) =>
              /* Palmer: In order to produce the output of a memory we need to cat
               * together a bunch of narrower memories, which can only be
               * done after generating all the memories.  This saves up the
               * output statements for later. */
              val name = s"${mem}_${i}_${j}"
              val exp = invert(bits(WSubField(inst, lib), high-low, 0), libPort.outputPolarity)
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
            case (Some(mem), None) =>
              System.err println "WARNING: Unable to match output ports on memory"
              System.err println s"  outer output port: ${mem}"
              return None
          }

          // Input port mapping
          (memPort.inputName, libPort.inputName) match {
            case (Some(mem), Some(lib)) =>
              /* Palmer: The input port to a memory just needs to happen in parallel,
               * this does a part select to narrow the memory down. */
              stmts += connectPorts(bits(WRef(mem), high, low), lib, libPort.inputPolarity)
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
            case (Some(mem), None) =>
              System.err println "WARNING: Unable to match input ports on memory"
              System.err println s"  outer input port: ${mem}"
              return None
          }

          // Mask port mapping
          val memMask = memPort.maskName match {
            case Some(mem) =>
              /* Palmer: The bits from the outer memory's write mask that will be
               * used as the write mask for this inner memory. */
              if (libPort.effectiveMaskGran == libPort.width) {
                bits(WRef(mem), low / memPort.effectiveMaskGran)
              } else {
                if (libPort.effectiveMaskGran != 1) {
                  // TODO
                  System.err println "only single-bit mask supported"
                  return None
                }
                cat(((low to high) map (i => bits(WRef(mem), i / memPort.effectiveMaskGran))).reverse)
              }
            case None =>
              /* Palmer: If there is no input port on the source memory port
               * then we don't ever want to turn on this write
               * enable.  Otherwise, we just _always_ turn on the
               * write enable port on the inner memory. */
              if (!libPort.maskName.isDefined) one
              else {
                val width = libPort.width / libPort.effectiveMaskGran
                val value = (BigInt(1) << width.toInt) - 1
                UIntLiteral(value, IntWidth(width))
              }
          }

          // Write enable port mapping
          val memWriteEnable = memPort.writeEnableName match {
            case Some(mem) =>
              /* Palmer: The outer memory's write enable port, or a constant 1 if
               * there isn't a write enable port. */
              WRef(mem)
            case None =>
              /* Palemr: If there is no input port on the source memory port
               * then we don't ever want to turn on this write
               * enable.  Otherwise, we just _always_ turn on the
               * write enable port on the inner memory. */
              if (!memPort.inputName.isDefined) zero else one
          }

          // Chip enable port mapping
          val memChipEnable = memPort.chipEnableName match {
            case Some(mem) => WRef(mem)
            case None      => one
          }

          // Read enable port mapping 
          /* Palmer: It's safe to ignore read enables, but we pass them through
           * to the vendor memory if there's a port on there that
           * implements the read enables. */
          (memPort.readEnableName, libPort.readEnableName) match {
            case (_, None) =>
            case (Some(mem), Some(lib)) =>
              stmts += connectPorts(andAddrMatch(WRef(mem)), lib, libPort.readEnablePolarity)
            case (None, Some(lib)) =>
              stmts += connectPorts(andAddrMatch(not(memWriteEnable)), lib, libPort.readEnablePolarity)
          }

          /* Palmer: This is actually the memory compiler: it figures out how to
           * implement the outer memory's collection of ports using what
           * the inner memory has availiable. */ 
          ((libPort.maskName, libPort.writeEnableName, libPort.chipEnableName): @unchecked) match {
            case (Some(mask), Some(we), Some(en)) =>
              /* Palmer: This is the simple option: every port exists. */
              stmts += connectPorts(memMask, mask, libPort.maskPolarity)
              stmts += connectPorts(andAddrMatch(memWriteEnable), we, libPort.writeEnablePolarity) 
              stmts += connectPorts(andAddrMatch(memChipEnable), en, libPort.chipEnablePolarity)
            case (Some(mask), Some(we), None) =>
              /* Palmer: If we don't have a chip enable but do have */
              stmts += connectPorts(memMask, mask, libPort.maskPolarity)
              stmts += connectPorts(andAddrMatch(and(memWriteEnable, memChipEnable)),
                                    we, libPort.writeEnablePolarity)
            case (None, Some(we), Some(en)) if bitWidth(memMask.tpe) == 1 =>
              /* Palmer: If we're expected to provide mask ports without a
               * memory that actually has them then we can use the
               * write enable port instead of the mask port. */
              stmts += connectPorts(andAddrMatch(and(memWriteEnable, memMask)),
                                    we, libPort.writeEnablePolarity)
              stmts += connectPorts(andAddrMatch(memChipEnable), en, libPort.chipEnablePolarity)
            case (None, Some(we), Some(en)) =>
              // TODO
              System.err println "cannot emulate multi-bit mask ports with write enable"
              return None
            case (None, None, None) =>
              /* Palmer: There's nothing to do here since there aren't any
               * ports to match up. */ 
          }
        }
        // Cat macro outputs for selection
        memPort.outputName match {
          case Some(mem) if cats.nonEmpty =>
            val name = s"${mem}_${i}"
            stmts += DefNode(NoInfo, name, cat(cats.toSeq.reverse))
            (outputs getOrElseUpdate (mem, ArrayBuffer[(Expression, Expression)]())) +=
              (addrMatch -> WRef(name))
          case _ =>
        }
      }
    }
    // Connect mem outputs
    mem.ports foreach { port =>
      port.outputName match {
        case Some(mem) => outputs get mem match {
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
      case (Some(mems), Some(libs)) => (mems foldLeft c.modules){ (modules, mem) =>
        val (best, cost) = (libs foldLeft (None: Option[(Module, ExtModule)], BigInt(Long.MaxValue))){
          case ((best, area), lib) if mem.ports.size != lib.ports.size =>
            /* Palmer: FIXME: This just assumes the Chisel and vendor ports are in the same
             * order, but I'm starting with what actually gets generated. */
            System.err println s"INFO: unable to compile ${mem.name} using ${lib.name} port count must match"
            (best, area)
          case ((best, area), lib) =>
            /* Palmer: A quick cost function (that must be kept in sync with
             * memory_cost()) that attempts to avoid compiling unncessary
             * memories.  This is a lower bound on the cost of compiling a
             * memory: it assumes 100% bit-cell utilization when mapping. */
            // val cost = 100 * (mem.depth * mem.width) / (lib.depth * lib.width) +
            //                  (mem.depth * mem.width)
            // Donggyu: I re-define cost
            val cost = max(1, mem.depth / lib.depth) *
                       max(1, mem.width / lib.width) *
                       (lib.depth * lib.width + 1) // weights on # cells
            System.err println s"Cost of ${lib.name} for ${mem.name}: ${cost}"
            if (cost > area) (best, area)
            else compile(mem, lib) match {
              case None => (best, area)
              case Some(p) => (Some(p), cost)
            }
        }
        best match {
          case None => modules
          case Some((mod, bb)) =>
            (modules filterNot (m => m.name == mod.name || m.name == bb.name)) ++ Seq(mod, bb)
        }
      }
      case _ => c.modules
    }
    val circuit = c.copy(modules = modules)
    // print(circuit.serialize)
    circuit
  }
}

class MacroCompilerTransform extends Transform {
  def inputForm = HighForm
  def outputForm = HighForm
  def execute(state: CircuitState) = getMyAnnotations(state) match {
    case Seq(MacroCompilerAnnotation(state.circuit.main, mem, lib, synflops)) =>
      val transforms = Seq(
        new MacroCompilerPass(mem, lib),
        // TODO: Syn flops
        firrtl.passes.SplitExpressions
      )
      ((transforms foldLeft state)((s, xform) => xform runTransform s))
  }
}

class MacroCompiler extends Compiler {
  def emitter = new VerilogEmitter
  def transforms =
    Seq(new MacroCompilerTransform) ++
    getLoweringTransforms(firrtl.HighForm, firrtl.LowForm) // ++
    // Seq(new LowFirrtlOptimization) // Todo: This is dangerous...
}

object MacroCompiler extends App {
  sealed trait MacroParam
  case object Macros extends MacroParam
  case object Library extends MacroParam
  case object Verilog extends MacroParam 
  type MacroParamMap = Map[MacroParam, File]
  val usage = Seq(
    "Options:",
    "  -m, --macro-list: The set of macros to compile",
    "  -l, --library: The set of macros that have blackbox instances",
    "  -v, --verilog: Verilog output",
    "  --syn-flop: Produces synthesizable flop-based memories") mkString "\n"

  def parseArgs(map: MacroParamMap, synflops: Boolean, args: List[String]): (MacroParamMap, Boolean) =
    args match {
      case Nil => (map, synflops)
      case ("-m" | "--macro-list") :: value :: tail =>
        parseArgs(map + (Macros  -> new File(value)), synflops, tail)
      case ("-l" | "--library") :: value :: tail =>
        parseArgs(map + (Library -> new File(value)), synflops, tail)
      case ("-v" | "--verilog") :: value :: tail =>
        parseArgs(map + (Verilog -> new File(value)), synflops, tail)
      case "--syn-flops" :: tail =>
        parseArgs(map, true, tail)
      case arg :: tail =>
        println(s"Unknown field $arg\n")
        throw new Exception(usage)
    }

  def run(args: List[String]) = {
    val (params, synflops) = parseArgs(Map[MacroParam, File](), false, args)
    try {
      val macros = readJSON(params get Macros).get map (x => (new Macro(x)).blackbox)
      val circuit = Circuit(NoInfo, macros, macros.last.name)
      val annotations = AnnotationMap(Seq(MacroCompilerAnnotation(
        circuit.main, params(Macros), params get Library, synflops)))
      val state = CircuitState(circuit, HighForm, Some(annotations))
      val verilog = new FileWriter(params(Verilog))
      val result = new MacroCompiler compile (state, verilog)
      verilog.close
      result
    } catch {
      case e: java.util.NoSuchElementException =>
        throw new Exception(usage)
      case e: Throwable =>
        throw e
    }
  }

  run(args.toList)
}
