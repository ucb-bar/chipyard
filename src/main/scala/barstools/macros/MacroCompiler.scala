// See LICENSE for license details.

/** Terminology note:
  * mem - target memory to compile, in design (e.g. Mem() in rocket)
  * lib - technology SRAM(s) to use to compile mem
  */

package barstools.macros

import barstools.macros.Utils._
import firrtl.Utils.{one, zero, BoolType}
import firrtl.annotations._
import firrtl.ir._
import firrtl.options.Dependency
import firrtl.stage.TransformManager.TransformDependency
import firrtl.stage.{FirrtlSourceAnnotation, FirrtlStage, Forms, OutputFileAnnotation, RunFirrtlTransformAnnotation}
import firrtl.transforms.NoDCEAnnotation
import firrtl.{PrimOps, _}
import mdf.macrolib.{PolarizedPort, PortPolarity, SRAMCompiler, SRAMGroup, SRAMMacro}

import java.io.{File, FileWriter}
import scala.annotation.tailrec
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

case class MacroCompilerException(msg: String) extends Exception(msg)

// TODO The parameters could be unpacked here instead of keeping it in a serialized form
case class MacroCompilerAnnotation(content: String) extends NoTargetAnnotation {
  import MacroCompilerAnnotation.Params

  def params: Params = MacroCompilerUtil.objFromString(content).asInstanceOf[Params]
}

/** The MacroCompilerAnnotation to trigger the macro compiler.
  * Note that this annotation does NOT actually target any modules for
  * compilation. It simply holds all the settings for the memory compiler. The
  * actual selection of which memories to compile is set in the Params.
  *
  * To use, simply annotate the entire circuit itself with this annotation and
  * include [[MacroCompilerTransform]].
  */
object MacroCompilerAnnotation {

  /** Macro compiler mode. */
  sealed trait CompilerMode

  /** Strict mode - must compile all memories or error out. */
  case object Strict extends CompilerMode

  /** Synflops mode - compile all memories with synflops (do not map to lib at all). */
  case object Synflops extends CompilerMode

  /** CompileAndSynflops mode - compile all memories and create mock versions of the target libs with synflops. */
  case object CompileAndSynflops extends CompilerMode

  /** FallbackSynflops - compile all memories to SRAM when possible and fall back to synflops if a memory fails. * */
  case object FallbackSynflops extends CompilerMode

  /** CompileAvailable - compile what is possible and do nothing with uncompiled memories. * */
  case object CompileAvailable extends CompilerMode

  /** The default mode for the macro compiler.
    * TODO: Maybe set the default to FallbackSynflops (typical for
    * vlsi_mem_gen-like scripts) once it's implemented?
    */
  val Default: CompilerMode = CompileAvailable

  // Options as list of (CompilerMode, command-line name, description)
  val options: Seq[(CompilerMode, String, String)] = Seq(
    (Default, "default", "Select the default option from below."),
    (Strict, "strict", "Compile all memories to library or return an error."),
    (
      Synflops,
      "synflops",
      "Produces synthesizable flop-based memories for all memories (do not map to lib at all); likely useful for simulation purposes."
    ),
    (
      CompileAndSynflops,
      "compileandsynflops",
      "Compile all memories and create mock versions of the target libs with synflops; likely also useful for simulation purposes."
    ),
    (
      FallbackSynflops,
      "fallbacksynflops",
      "Compile all memories to library when possible and fall back to synthesizable flop-based memories when library synth is not possible."
    ),
    (
      CompileAvailable,
      "compileavailable",
      "Compile all memories to library when possible and do nothing in case of errors. (default)"
    )
  )

  /** Helper function to select a compiler mode. */
  def stringToCompilerMode(str: String): CompilerMode = options.collectFirst {
    case (mode, cmd, _) if cmd == str => mode
  } match {
    case Some(x) => x
    case None    => throw new IllegalArgumentException("No such compiler mode " + str)
  }

  /** Parameters associated to this MacroCompilerAnnotation.
    *
    * @param mem           Path to memory lib
    * @param memFormat     Type of memory lib (Some("conf"), Some("mdf"), or None (defaults to mdf))
    * @param lib           Path to library lib or None if no libraries
    * @param hammerIR      Path to HammerIR output or None (not generated in this case)
    * @param costMetric    Cost metric to use
    * @param mode          Compiler mode (see CompilerMode)
    * @param forceCompile  Set of memories to force compiling to lib regardless of the mode
    * @param forceSynflops Set of memories to force compiling as flops regardless of the mode
    */
  case class Params(
    mem:           String,
    memFormat:     Option[String],
    lib:           Option[String],
    hammerIR:      Option[String],
    costMetric:    CostMetric,
    mode:          CompilerMode,
    useCompiler:   Boolean,
    forceCompile:  Set[String],
    forceSynflops: Set[String])
      extends Serializable

  /** Create a MacroCompilerAnnotation.
    * @param c Top-level circuit name (see class description)
    * @param p Parameters (see above).
    */
  def apply(c: String, p: Params): MacroCompilerAnnotation =
    MacroCompilerAnnotation(MacroCompilerUtil.objToString(p))

}

class MacroCompilerPass(
  mems:       Option[Seq[Macro]],
  libs:       Option[Seq[Macro]],
  compilers:  Option[SRAMCompiler],
  hammerIR:   Option[String],
  costMetric: CostMetric = CostMetric.default,
  mode:       MacroCompilerAnnotation.CompilerMode = MacroCompilerAnnotation.Default)
    extends firrtl.passes.Pass {
  // Helper function to check the legality of bitPairs.
  // e.g. ((0,21), (22,43)) is legal
  // ((0,21), (22,21)) is illegal and will throw an assert
  private def checkBitPairs(bitPairs: Seq[(BigInt, BigInt)]): Unit = {
    bitPairs.foldLeft(BigInt(-1))((lastBit, nextPair) => {
      assert(lastBit + 1 == nextPair._1, s"Pair's first bit ${nextPair._1} does not follow last bit $lastBit")
      assert(nextPair._2 >= nextPair._1, s"Pair $nextPair in bitPairs $bitPairs is illegal")
      nextPair._2
    })
  }

  /** Calculate bit pairs.
    * This is a list of submemories by width.
    * The tuples are (lsb, msb) inclusive.
    * Example: (0, 7) and (8, 15) might be a split for a width=16 memory into two width=8 target memories.
    * Another example: (0, 3), (4, 7), (8, 11) may be a split for a width-12 memory into 3 width-4 target memories.
    *
    * @param mem Memory to compile
    * @param lib Lib to compile with
    * @return Bit pairs or empty list if there was an error.
    */
  private def calculateBitPairs(mem: Macro, lib: Macro): Seq[(BigInt, BigInt)] = {
    val pairedPorts = mem.sortedPorts.zip(lib.sortedPorts)

    val bitPairs = ArrayBuffer[(BigInt, BigInt)]()
    var currentLSB: BigInt = 0

    // Process every bit in the mem width.
    for (memBit <- 0 until mem.src.width) {
      val bitsInCurrentMem = memBit - currentLSB

      // We'll need to find a bitPair that works for *all* the ports of the memory.
      // e.g. unmasked read port and masked write port.
      // For each port, store a tentative candidate for the split.
      // Afterwards, figure out which one to use.
      val bitPairCandidates = ArrayBuffer[(BigInt, BigInt)]()
      for ((memPort, libPort) <- pairedPorts) {

        // Sanity check to make sure we only split once per bit, once per port.
        var alreadySplit: Boolean = false

        // Helper function to check if it's time to split memories.
        // @param effectiveLibWidth Split memory when we have this many bits.
        def splitMemory(effectiveLibWidth: Int): Unit = {
          assert(!alreadySplit)

          if (bitsInCurrentMem == effectiveLibWidth) {
            bitPairCandidates += ((currentLSB, memBit - 1))
            alreadySplit = true
          }
        }

        // Make sure we don't have a maskGran larger than the width of the memory.
        assert(memPort.src.effectiveMaskGran <= memPort.src.width.get)
        assert(libPort.src.effectiveMaskGran <= libPort.src.width.get)

        val libWidth = libPort.src.width.get

        // Don't consider cases of maskGran == width as "masked" since those masks
        // effectively function as write-enable bits.
        val memMask = if (memPort.src.effectiveMaskGran == memPort.src.width.get) None else memPort.src.maskGran
        val libMask = if (libPort.src.effectiveMaskGran == libPort.src.width.get) None else libPort.src.maskGran

        (memMask, libMask) match {
          // Neither lib nor mem is masked.
          // No problems here.
          case (None, None) => splitMemory(libWidth)

          // Only the lib is masked.
          // Not an issue; we can just make all the bits in the lib mask enabled.
          case (None, Some(_)) => splitMemory(libWidth)

          // Only the mem is masked.
          case (Some(p), None) =>
            if (p % libPort.src.width.get == 0) {
              // If the mem mask is a multiple of the lib width, then we're good.
              // Just roll over every lib width as usual.
              // e.g. lib width=4, mem maskGran={4, 8, 12, 16, ...}
              splitMemory(libWidth)
            } else if (libPort.src.width.get % p == 0) {
              // Lib width is a multiple of the mem mask.
              // Consider the case where mem mask = 4 but lib width = 8, unmasked.
              // We can still compile, but will need to waste the extra bits.
              splitMemory(memMask.get)
            } else {
              // No neat multiples.
              // We might still be able to compile extremely inefficiently.
              if (p < libPort.src.width.get) {
                // Compile using mem mask as the effective width. (note that lib is not masked)
                // e.g. mem mask = 3, lib width = 8
                splitMemory(memMask.get)
              } else {
                // e.g. mem mask = 13, lib width = 8
                System.err.println(
                  s"Unmasked target memory: unaligned mem maskGran $p with lib (${lib.src.name}) width ${libPort.src.width.get} not supported"
                )
                return Seq()
              }
            }

          // Both lib and mem are masked.
          case (Some(m), Some(l)) =>
            if (m == l) {
              // Lib maskGran == mem maskGran, no problems
              splitMemory(libWidth)
            } else if (m > l) {
              // Mem maskGran > lib maskGran
              if (m % l == 0) {
                // Mem maskGran is a multiple of lib maskGran, carry on as normal.
                splitMemory(libWidth)
              } else {
                System.err.println(s"Mem maskGran $m is not a multiple of lib maskGran $l: currently not supported")
                return Seq()
              }
            } else { // m < l
              // Lib maskGran > mem maskGran.
              if (l % m == 0) {
                // Lib maskGran is a multiple of mem maskGran.
                // e.g. lib maskGran = 8, mem maskGran = 4.
                // In this case we can only compile very wastefully (by treating
                // lib as a mem maskGran width memory) :(
                splitMemory(memMask.get)

                // TODO: there's an optimization that could allow us to pack more
                // bits in and be more efficient.
                // e.g. say if mem maskGran = 4, lib maskGran = 8, libWidth = 32
                // We could use 16 of bit (bits 0-3, 8-11, 16-19, 24-27) instead
                // of treating it as simply a width 4 (!!!) memory.
                // This would require a major refactor though.
              } else {
                System.err.println(s"Lib maskGran $m is not a multiple of mem maskGran $l: currently not supported")
                return Seq()
              }
            }
        }
      }

      // Choose an actual bit pair to add.
      // We'll have to choose the smallest one (e.g. unmasked read port might be more tolerant of a bigger split than the masked write port).
      if (bitPairCandidates.isEmpty) {
        // No pair needed to split, just continue
      } else {
        val bestPair = bitPairCandidates.reduceLeft((leftPair, rightPair) => {
          if (leftPair._2 - leftPair._1 + 1 > rightPair._2 - rightPair._1 + 1) leftPair else rightPair
        })
        bitPairs += bestPair
        currentLSB = bestPair._2 + BigInt(1) // advance the LSB pointer
      }
    }
    // Add in the last chunk if there are any leftovers
    bitPairs += ((currentLSB, mem.src.width - 1))

    bitPairs
  }.toSeq

  def compile(mem: Macro, lib: Macro): Option[(Module, Macro)] = {
    assert(
      mem.sortedPorts.lengthCompare(lib.sortedPorts.length) == 0,
      "mem and lib should have an equal number of ports"
    )
    val pairedPorts = mem.sortedPorts.zip(lib.sortedPorts)

    // Width mapping. See calculateBitPairs.
    val bitPairs: Seq[(BigInt, BigInt)] = calculateBitPairs(mem, lib)
    if (bitPairs.isEmpty) {
      System.err.println("Error occurred during bitPairs calculations (bitPairs is empty).")
      return None
    }
    // Check bit pairs.
    checkBitPairs(bitPairs)

    // Depth mapping
    val stmts = ArrayBuffer[Statement]()
    val outputs = mutable.HashMap[String, ArrayBuffer[(Expression, Expression)]]()
    val selects = mutable.HashMap[String, Expression]()
    val selectRegs = mutable.HashMap[String, Expression]()
    /* Palmer: If we've got a parallel memory then we've got to take the
     * address bits into account. */
    if (mem.src.depth > lib.src.depth) {
      mem.src.ports.foreach { port =>
        val high = MacroCompilerMath.ceilLog2(mem.src.depth)
        val low = MacroCompilerMath.ceilLog2(lib.src.depth)
        val ref = WRef(port.address.name)
        val nodeName = s"${ref.name}_sel"
        val tpe = UIntType(IntWidth(high - low))
        selects(ref.name) = WRef(nodeName, tpe)
        stmts += DefNode(NoInfo, nodeName, bits(ref, high - 1, low))
        // Donggyu: output selection should be piped
        if (port.output.isDefined) {
          val regName = s"${ref.name}_sel_reg"
          val enable = (port.chipEnable, port.readEnable) match {
            case (Some(ce), Some(re)) =>
              and(WRef(ce.name, BoolType), WRef(re.name, BoolType))
            case (Some(ce), None) => WRef(ce.name, BoolType)
            case (None, Some(re)) => WRef(re.name, BoolType)
            case (None, None)     => one
          }
          selectRegs(ref.name) = WRef(regName, tpe)
          stmts += DefRegister(NoInfo, regName, tpe, WRef(port.clock.get.name), zero, WRef(regName))
          stmts += Connect(NoInfo, WRef(regName), Mux(enable, WRef(nodeName), WRef(regName), tpe))
        }
      }
    }
    for ((_, i) <- BigInt(0).until(mem.src.depth, lib.src.depth).zipWithIndex) {
      for (j <- bitPairs.indices) {
        val name = s"mem_${i}_$j"
        // Create the instance.
        stmts += WDefInstance(NoInfo, name, lib.src.name, lib.tpe)
        // Connect extra ports of the lib.
        stmts ++= lib.extraPorts.map { case (portName, portValue) =>
          Connect(NoInfo, WSubField(WRef(name), portName), portValue)
        }
      }
      for ((memPort, libPort) <- pairedPorts) {
        val addrMatch = selects.get(memPort.src.address.name) match {
          case None => one
          case Some(addr) =>
            val index = UIntLiteral(i, IntWidth(bitWidth(addr.tpe)))
            DoPrim(PrimOps.Eq, Seq(addr, index), Nil, index.tpe)
        }
        val addrMatchReg = selectRegs.get(memPort.src.address.name) match {
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
          val inst = WRef(s"mem_${i}_$j", lib.tpe)

          def connectPorts2(mem: Expression, lib: String, polarity: Option[PortPolarity]): Statement =
            Connect(NoInfo, WSubField(inst, lib), portToExpression(mem, polarity))
          def connectPorts(mem: Expression, lib: String, polarity: PortPolarity): Statement =
            connectPorts2(mem, lib, Some(polarity))

          // Clock port mapping
          /* Palmer: FIXME: I don't handle memories with read/write clocks yet. */
          /* Colin not all libPorts have clocks but all memPorts do*/
          libPort.src.clock.foreach { cPort =>
            stmts += connectPorts(WRef(memPort.src.clock.get.name), cPort.name, cPort.polarity)
          }

          // Adress port mapping
          /* Palmer: The address port to a memory is just the low-order bits of
           * the top address. */
          stmts += connectPorts(WRef(memPort.src.address.name), libPort.src.address.name, libPort.src.address.polarity)

          // Output port mapping
          (memPort.src.output, libPort.src.output) match {
            case (Some(PolarizedPort(mem, _)), Some(PolarizedPort(lib, lib_polarity))) =>
              /* Palmer: In order to produce the output of a memory we need to cat
               * together a bunch of narrower memories, which can only be
               * done after generating all the memories.  This saves up the
               * output statements for later. */
              val name = s"${mem}_${i}_$j" // This name is the output from the instance (mem vs ${mem}).
              val exp = portToExpression(bits(WSubField(inst, lib), high - low, 0), Some(lib_polarity))
              stmts += DefNode(NoInfo, name, exp)
              cats += WRef(name)
            case (None, Some(_)) =>
            /* Palmer: If the inner memory has an output port but the outer
             * one doesn't then it's safe to just leave the outer
             * port floating. */
            case (None, None) =>
            /* Palmer: If there's no output ports at all (ie, read-only
             * port on the memory) then just don't worry about it,
             * there's nothing to do. */
            case (Some(PolarizedPort(mem, _)), None) =>
              System.err.println("WARNING: Unable to match output ports on memory")
              System.err.println(s"  outer output port: $mem")
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
              //Firrtl cares about dangling inputs now tie it off
              stmts += IsInvalid(NoInfo, WSubField(inst, lib.name))
            case (None, None) =>
            /* Palmer: If there's no input ports at all (ie, read-only
             * port on the memory) then just don't worry about it,
             * there's nothing to do. */
            case (Some(PolarizedPort(mem, _)), None) =>
              System.err.println("WARNING: Unable to match input ports on memory")
              System.err.println(s"  outer input port: $mem")
              return None
          }

          // Mask port mapping
          val memMask = memPort.src.maskPort match {
            case Some(PolarizedPort(mem, _)) =>
              /* Palmer: The bits from the outer memory's write mask that will be
               * used as the write mask for this inner memory. */
              if (libPort.src.effectiveMaskGran == libPort.src.width.get) {
                bits(WRef(mem), low / memPort.src.effectiveMaskGran)
              } else {
                require(isPowerOfTwo(libPort.src.effectiveMaskGran), "only powers of two masks supported for now")

                // How much of this lib's width we are effectively using.
                // If we have a mem maskGran less than the lib's maskGran, we'll have to take the smaller maskGran.
                // Example: if we have a lib whose maskGran is 8 but our mem's maskGran is 4.
                // The other case is if we're using a larger lib than mem.
                val usingLessThanLibMaskGran = memPort.src.maskGran.get < libPort.src.effectiveMaskGran
                val effectiveLibWidth =
                  if (usingLessThanLibMaskGran)
                    memPort.src.maskGran.get
                  else
                    libPort.src.width.get

                cat(
                  (0 until libPort.src.width.get by libPort.src.effectiveMaskGran)
                    .map(i => {
                      if (usingLessThanLibMaskGran && i >= effectiveLibWidth) {
                        // If the memMaskGran is smaller than the lib's gran, then
                        // zero out the upper bits.
                        zero
                      } else {
                        if ((low + i) >= memPort.src.width.get) {
                          // If our bit is larger than the whole width of the mem, just zero out the upper bits.
                          zero
                        } else {
                          // Pick the appropriate bit from the mem mask.
                          bits(WRef(mem), (low + i) / memPort.src.effectiveMaskGran)
                        }
                      }
                    })
                    .reverse
                )
              }
            case None =>
              /* If there is a lib mask port but no mem mask port, just turn on
               * all bits of the lib mask port. */
              if (libPort.src.maskPort.isDefined) {
                val width = libPort.src.width.get / libPort.src.effectiveMaskGran
                val value = (BigInt(1) << width) - 1
                UIntLiteral(value, IntWidth(width))
              } else {
                // No mask ports on either side.
                // We treat a "mask" of a single bit to be equivalent to a write
                // enable (as used below).
                one
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
            case None                        => one
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
              stmts += connectPorts(andAddrMatch(and(not(memWriteEnable), memChipEnable)), lib, lib_polarity)
          }

          /* Palmer: This is actually the memory compiler: it figures out how to
           * implement the outer memory's collection of ports using what
           * the inner memory has availiable. */
          ((libPort.src.maskPort, libPort.src.writeEnable, libPort.src.chipEnable): @unchecked) match {
            case (
                  Some(PolarizedPort(mask, mask_polarity)),
                  Some(PolarizedPort(we, we_polarity)),
                  Some(PolarizedPort(en, en_polarity))
                ) =>
              /* Palmer: This is the simple option: every port exists. */
              stmts += connectPorts(memMask, mask, mask_polarity)
              stmts += connectPorts(andAddrMatch(memWriteEnable), we, we_polarity)
              stmts += connectPorts(andAddrMatch(memChipEnable), en, en_polarity)
            case (Some(PolarizedPort(mask, mask_polarity)), Some(PolarizedPort(we, we_polarity)), None) =>
              /* Palmer: If we don't have a chip enable but do have mask ports. */
              stmts += connectPorts(memMask, mask, mask_polarity)
              stmts += connectPorts(andAddrMatch(and(memWriteEnable, memChipEnable)), we, we_polarity)
            case (None, Some(PolarizedPort(we, we_polarity)), chipEnable) =>
              if (bitWidth(memMask.tpe) == 1) {
                /* Palmer: If we're expected to provide mask ports without a
                 * memory that actually has them then we can use the
                 * write enable port instead of the mask port. */
                chipEnable match {
                  case Some(PolarizedPort(en, en_polarity)) =>
                    stmts += connectPorts(andAddrMatch(and(memWriteEnable, memMask)), we, we_polarity)
                    stmts += connectPorts(andAddrMatch(memChipEnable), en, en_polarity)
                  case _ =>
                    stmts += connectPorts(
                      andAddrMatch(and(and(memWriteEnable, memChipEnable), memMask)),
                      we,
                      we_polarity
                    )
                }
              } else {
                System.err.println("cannot emulate multi-bit mask ports with write enable")
                return None
              }
            case (None, None, None) =>
            // No write ports to match up (this may be a read-only port).
            // This isn't necessarily an error condition.
          }
        }
        // Cat macro outputs for selection
        memPort.src.output match {
          case Some(PolarizedPort(mem, _)) if cats.nonEmpty =>
            val name = s"${mem}_$i"
            stmts += DefNode(NoInfo, name, cat(cats.toSeq.reverse))
            outputs.getOrElseUpdate(mem, ArrayBuffer[(Expression, Expression)]()) +=
              (addrMatchReg -> WRef(name))
          case _ =>
        }
      }
    }
    // Connect mem outputs
    val zeroOutputValue: Expression = UIntLiteral(0, IntWidth(mem.src.width))
    mem.src.ports.foreach { port =>
      port.output match {
        case Some(PolarizedPort(mem, _)) =>
          outputs.get(mem) match {
            case Some(select) =>
              val output = select.foldRight(zeroOutputValue) { case ((cond, tval), fval) =>
                Mux(cond, tval, fval, fval.tpe)
              }
              stmts += Connect(NoInfo, WRef(mem), output)
            case None =>
          }
        case None =>
      }
    }

    Some((mem.module(Block(stmts.toSeq)), lib))
  }

  def run(c: Circuit): Circuit = {
    var firstLib = true
    val modules = (mems, libs) match {
      case (Some(mems), Some(libs)) =>
        // Try to compile each of the memories in mems.
        // The 'state' is c.modules, which is a list of all the firrtl modules
        // in the 'circuit'.
        mems.foldLeft(c.modules) { (modules, mem) =>
          val sram = mem.src
          def groupMatchesMask(group: SRAMGroup, mem: SRAMMacro): Boolean = {
            val memMask = mem.ports.map(_.maskGran).find(_.isDefined).flatten
            val libMask = group.ports.map(_.maskGran).find(_.isDefined).flatten
            (memMask, libMask) match {
              case (None, _)          => true
              case (Some(_), None)    => false
              case (Some(m), Some(l)) => l <= m //Ignore memories that don't have nice mask
            }
          }
          // Add compiler memories that might map well to libs
          val compLibs = compilers match {
            case Some(SRAMCompiler(_, groups)) =>
              groups
                .filter(g => g.family == sram.family && groupMatchesMask(g, sram))
                .map(g => {
                  for {
                    w <- g.width
                    d <- g.depth if (sram.width % w == 0) && (sram.depth % d == 0)
                  } yield Seq(new Macro(buildSRAMMacro(g, d, w, g.vt.head)))
                })
            case None => Seq()
          }
          val fullLibs = libs ++ compLibs.flatten.flatten

          // Try to compile mem against each lib in libs, keeping track of the
          // best compiled version, external lib used, and cost.
          val (best, _) = fullLibs.foldLeft(None: Option[(Module, Macro)], Double.MaxValue) {
            case ((best, cost), lib) if mem.src.ports.size != lib.src.ports.size =>
              /* Palmer: FIXME: This just assumes the Chisel and vendor ports are in the same
               * order, but I'm starting with what actually gets generated. */
              System.err.println(s"INFO: unable to compile ${mem.src.name} using ${lib.src.name} port count must match")
              (best, cost)
            case ((best, cost), lib) =>
              // Run the cost function to evaluate this potential compile.
              costMetric.cost(mem, lib) match {
                case Some(newCost) =>
                  //System.err.println(s"Cost of ${lib.src.name} for ${mem.src.name}: ${newCost}")
                  // Try compiling
                  compile(mem, lib) match {
                    // If it was successful and the new cost is lower
                    case Some(p) if newCost < cost => (Some(p), newCost)
                    case _                         => (best, cost)
                  }
                case _ => (best, cost) // Cost function rejected this combination.
              }
          }

          // If we were able to compile anything, then replace the original module
          // in the modules list with a compiled version, as well as the extmodule
          // stub for the lib.
          best match {
            case None =>
              if (mode == MacroCompilerAnnotation.Strict)
                throw MacroCompilerException(
                  s"Target memory ${mem.src.name} could not be compiled and strict mode is activated - aborting."
                )
              else
                modules
            case Some((mod, bb)) =>
              hammerIR match {
                case Some(f) =>
                  val hammerIRWriter = new FileWriter(new File(f), !firstLib)
                  if (firstLib) hammerIRWriter.write("[\n")
                  hammerIRWriter.write(bb.src.toJSON().toString())
                  hammerIRWriter.write("\n,\n")
                  hammerIRWriter.close()
                  firstLib = false
                case None =>
              }
              modules.filterNot(m => m.name == mod.name || m.name == bb.blackbox.name) ++ Seq(mod, bb.blackbox)
          }
        }
      case _ => c.modules
    }
    c.copy(modules = modules)
  }
}

class MacroCompilerTransform extends Transform with DependencyAPIMigration {
  override def prerequisites:          Seq[TransformDependency] = Forms.LowForm
  override def optionalPrerequisites:  Seq[TransformDependency] = Forms.LowFormOptimized
  override def optionalPrerequisiteOf: Seq[Dependency[Emitter]] = Forms.LowEmitters
  override def invalidates(a: Transform) = false

  def execute(state: CircuitState): CircuitState = state.annotations.collect { case a: MacroCompilerAnnotation =>
    a
  } match {
    case Seq(anno: MacroCompilerAnnotation) =>
      val MacroCompilerAnnotation.Params(
        memFile,
        memFileFormat,
        libFile,
        hammerIR,
        costMetric,
        mode,
        useCompiler,
        forceCompile,
        forceSynflops
      ) = anno.params
      if (mode == MacroCompilerAnnotation.FallbackSynflops) {
        throw new UnsupportedOperationException("Not implemented yet")
      }

      // Check that we don't have any modules both forced to compile and synflops.
      assert(forceCompile.intersect(forceSynflops).isEmpty, "Cannot have modules both forced to compile and synflops")

      // Read, eliminate None, get only SRAM, make firrtl macro
      val mems: Option[Seq[Macro]] = (memFileFormat match {
        case Some("conf") => readConfFromPath(Some(memFile))
        case _            => mdf.macrolib.Utils.readMDFFromPath(Some(memFile))
      }) match {
        case Some(x: Seq[mdf.macrolib.Macro]) =>
          Some(filterForSRAM(Some(x)).getOrElse(List()).map { new Macro(_) })
        case _ => None
      }
      val libs: Option[Seq[Macro]] = mdf.macrolib.Utils.readMDFFromPath(libFile) match {
        case Some(x: Seq[mdf.macrolib.Macro]) =>
          Some(filterForSRAM(Some(x)).getOrElse(List()).map { new Macro(_) })
        case _ => None
      }
      val compilers: Option[mdf.macrolib.SRAMCompiler] = mdf.macrolib.Utils.readMDFFromPath(libFile) match {
        case Some(x: Seq[mdf.macrolib.Macro]) =>
          if (useCompiler) {
            findSRAMCompiler(Some(x))
          } else None
        case _ => None
      }

      // Helper function to turn a set of mem names into a Seq[Macro].
      def setToSeqMacro(names: Set[String]): Seq[Macro] = {
        names.toSeq.map(memName => mems.get.collectFirst { case m if m.src.name == memName => m }.get)
      }

      // Build lists of memories for compilation and synflops.
      val memCompile = mems.map { actualMems =>
        val memsAdjustedForMode = if (mode == MacroCompilerAnnotation.Synflops) Seq.empty else actualMems
        memsAdjustedForMode.filterNot(m => forceSynflops.contains(m.src.name)) ++ setToSeqMacro(forceCompile)
      }
      val memSynflops: Seq[Macro] = mems.map { actualMems =>
        //
        val memsAdjustedForMode = if (mode == MacroCompilerAnnotation.Synflops) actualMems else Seq.empty
        memsAdjustedForMode.filterNot(m => forceCompile.contains(m.src.name)) ++ setToSeqMacro(forceSynflops)
      }.getOrElse(Seq.empty)

      val transforms = Seq(
        new MacroCompilerPass(memCompile, libs, compilers, hammerIR, costMetric, mode),
        new SynFlopsPass(
          true,
          memSynflops ++ (if (mode == MacroCompilerAnnotation.CompileAndSynflops) {
                            libs.get
                          } else {
                            Seq.empty
                          })
        )
      )
      transforms.foldLeft(state)((s, xform) => xform.runTransform(s))
    case _ => state
  }
}

class MacroCompilerOptimizations extends SeqTransform with DependencyAPIMigration {
  override def prerequisites:          Seq[TransformDependency] = Forms.LowForm
  override def optionalPrerequisites:  Seq[TransformDependency] = Forms.LowFormOptimized
  override def optionalPrerequisiteOf: Seq[Dependency[Emitter]] = Forms.LowEmitters
  override def invalidates(a: Transform) = false

  def transforms: Seq[Transform] = Seq(
    passes.RemoveValidIf,
    new firrtl.transforms.ConstantPropagation,
    passes.memlib.VerilogMemDelays,
    new firrtl.transforms.ConstantPropagation,
    passes.SplitExpressions,
    passes.CommonSubexpressionElimination
  )
}

object MacroCompiler extends App {
  sealed trait MacroParam
  case object Macros extends MacroParam
  case object MacrosFormat extends MacroParam
  case object Library extends MacroParam
  case object Verilog extends MacroParam
  case object Firrtl extends MacroParam
  case object HammerIR extends MacroParam
  case object CostFunc extends MacroParam
  case object Mode extends MacroParam
  case object UseCompiler extends MacroParam

  type MacroParamMap = Map[MacroParam, String]
  type CostParamMap = Map[String, String]
  type ForcedMemories = (Set[String], Set[String])
  val modeOptions: Seq[String] = MacroCompilerAnnotation.options.map { case (_, cmd, description) =>
    s"    $cmd: $description"
  }
  val usage: String = (Seq(
    "Options:",
    "  -n, --macro-conf: The set of macros to compile in firrtl-generated conf format (exclusive with -m)",
    "  -m, --macro-mdf: The set of macros to compile in MDF JSON format (exclusive with -n)",
    "  -l, --library: The set of macros that have blackbox instances",
    "  -u, --use-compiler: Flag, whether to use the memory compiler defined in library",
    "  -v, --verilog: Verilog output",
    "  -f, --firrtl: FIRRTL output (optional)",
    "  -hir, --hammer-ir: Hammer-IR output currently only needed for IP compilers",
    "  -c, --cost-func: Cost function to use. Optional (default: \"default\")",
    "  -cp, --cost-param: Cost function parameter. (Optional depending on the cost function.). e.g. -c ExternalMetric -cp path /path/to/my/cost/script",
    "  --force-compile [mem]: Force the given memory to be compiled to target libs regardless of the mode",
    "  --force-synflops [mem]: Force the given memory to be compiled via synflops regardless of the mode",
    "  --mode:"
  ) ++ modeOptions).mkString("\n")

  @tailrec
  def parseArgs(
    map:            MacroParamMap,
    costMap:        CostParamMap,
    forcedMemories: ForcedMemories,
    args:           List[String]
  ): (MacroParamMap, CostParamMap, ForcedMemories) =
    args match {
      case Nil => (map, costMap, forcedMemories)
      case ("-n" | "--macro-conf") :: value :: tail =>
        parseArgs(map + (Macros -> value) + (MacrosFormat -> "conf"), costMap, forcedMemories, tail)
      case ("-m" | "--macro-mdf") :: value :: tail =>
        parseArgs(map + (Macros -> value) + (MacrosFormat -> "mdf"), costMap, forcedMemories, tail)
      case ("-l" | "--library") :: value :: tail =>
        parseArgs(map + (Library -> value), costMap, forcedMemories, tail)
      case ("-u" | "--use-compiler") :: tail =>
        parseArgs(map + (UseCompiler -> ""), costMap, forcedMemories, tail)
      case ("-v" | "--verilog") :: value :: tail =>
        parseArgs(map + (Verilog -> value), costMap, forcedMemories, tail)
      case ("-f" | "--firrtl") :: value :: tail =>
        parseArgs(map + (Firrtl -> value), costMap, forcedMemories, tail)
      case ("-hir" | "--hammer-ir") :: value :: tail =>
        parseArgs(map + (HammerIR -> value), costMap, forcedMemories, tail)
      case ("-c" | "--cost-func") :: value :: tail =>
        parseArgs(map + (CostFunc -> value), costMap, forcedMemories, tail)
      case ("-cp" | "--cost-param") :: value1 :: value2 :: tail =>
        parseArgs(map, costMap + (value1 -> value2), forcedMemories, tail)
      case "--force-compile" :: value :: tail =>
        parseArgs(map, costMap, forcedMemories.copy(_1 = forcedMemories._1 + value), tail)
      case "--force-synflops" :: value :: tail =>
        parseArgs(map, costMap, forcedMemories.copy(_2 = forcedMemories._2 + value), tail)
      case "--mode" :: value :: tail =>
        parseArgs(map + (Mode -> value), costMap, forcedMemories, tail)
      case arg :: _ =>
        println(s"Unknown field $arg\n")
        println(usage)
        sys.exit(1)
    }

  def run(args: List[String]): Unit = {
    val (params, costParams, forcedMemories) =
      parseArgs(Map[MacroParam, String](), Map[String, String](), (Set.empty, Set.empty), args)
    try {
      val macros = params.get(MacrosFormat) match {
        case Some("conf") =>
          filterForSRAM(readConfFromPath(params.get(Macros))).get.map(x => new Macro(x).blackbox)
        case _ =>
          filterForSRAM(mdf.macrolib.Utils.readMDFFromPath(params.get(Macros))).get
            .map(x => new Macro(x).blackbox)
      }

      if (macros.nonEmpty) {
        // Note: the last macro in the input list is (seemingly arbitrarily)
        // determined as the firrtl "top-level module".
        val circuit = Circuit(NoInfo, macros, macros.last.name)
        val annotations = AnnotationSeq(
          Seq(
            MacroCompilerAnnotation(
              circuit.main,
              MacroCompilerAnnotation.Params(
                params(Macros),
                params.get(MacrosFormat),
                params.get(Library),
                params.get(HammerIR),
                CostMetric.getCostMetric(params.getOrElse(CostFunc, "default"), costParams),
                MacroCompilerAnnotation.stringToCompilerMode(params.getOrElse(Mode, "default")),
                params.contains(UseCompiler),
                forceCompile = forcedMemories._1,
                forceSynflops = forcedMemories._2
              )
            )
          )
        )

        // The actual MacroCompilerTransform basically just generates an input circuit
        val macroCompilerInput = CircuitState(circuit, annotations)
        val macroCompiled = (new MacroCompilerTransform).execute(macroCompilerInput)

        // Run FIRRTL compiler
        (new FirrtlStage).execute(
          Array.empty,
          Seq(
            OutputFileAnnotation(params.getOrElse(Verilog, "")),
            RunFirrtlTransformAnnotation(new VerilogEmitter),
            EmitCircuitAnnotation(classOf[VerilogEmitter]),
            NoDCEAnnotation,
            FirrtlSourceAnnotation(macroCompiled.circuit.serialize)
          )
        )

        params.get(HammerIR) match {
          case Some(hammerIRFile: String) =>
            val lines = FileUtils.getLines(hammerIRFile).toList
            val hammerIRWriter = new FileWriter(new File(hammerIRFile))
            // JSON means we need to destroy the last comma :(
            lines.dropRight(1).foreach(l => hammerIRWriter.write(l + "\n"))
            hammerIRWriter.write("]\n")
            hammerIRWriter.close()
          case None =>
        }
      } else {
        // Warn user
        System.err.println("WARNING: Empty *.mems.conf file. No memories generated.")

        // Emit empty verilog file if no macros found
        params.get(Verilog) match {
          case Some(verilogFile: String) =>
            // Create an empty verilog file
            val verilogWriter = new FileWriter(new File(verilogFile))
            verilogWriter.close()
          case None =>
        }
      }
    } catch {
      case e: java.util.NoSuchElementException =>
        if (args.isEmpty) {
          println("Command line arguments must be specified")
        } else {
          e.printStackTrace()
        }
        e.printStackTrace()
        sys.exit(1)
      case e: MacroCompilerException =>
        println(usage)
        e.printStackTrace()
        sys.exit(1)
      case e: Throwable =>
        throw e
    }
  }

  run(args.toList)
}
