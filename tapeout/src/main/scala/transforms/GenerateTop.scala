// See LICENSE for license details.

package barstools.tapeout.transforms

import firrtl._
import firrtl.ir._
import firrtl.annotations._
import firrtl.passes.Pass

object GenerateTop extends App {
  var input: Option[String] = None
  var output: Option[String] = None
  var synTop: Option[String] = None
  var harnessTop: Option[String] = None
  var seqMemFlags: Option[String] = Some("-o:unused.confg")
  var listClocks: Option[String] = Some("-o:unused.clocks")

  var usedOptions = Set.empty[Integer]
  args.zipWithIndex.foreach{ case (arg, i) =>
    arg match {
      case "-i" => {
        input = Some(args(i+1))
        usedOptions = usedOptions | Set(i+1)
      }
      case "-o" => {
        output = Some(args(i+1))
        usedOptions = usedOptions | Set(i+1)
      }
      case "--syn-top" => {
        synTop = Some(args(i+1))
        usedOptions = usedOptions | Set(i+1)
      }
      case "--harness-top" => {
        harnessTop = Some(args(i+1))
        usedOptions = usedOptions | Set(i+1)
      }
      case "--seq-mem-flags" => {
        seqMemFlags = Some(args(i+1))
        usedOptions = usedOptions | Set(i+1)
      }
      case "--list-clocks" => {
        listClocks = Some(args(i+1))
        usedOptions = usedOptions | Set(i+1)
      }
      case _ => {
        if (! (usedOptions contains i)) {
          error("Unknown option " + arg)
        }
      }
    }
  }

  firrtl.Driver.compile(
    input.get,
    output.get,
    new VerilogCompiler(),
    Parser.UseInfo,
    Seq(
      new ReParentCircuit(synTop.get),
      new RemoveUnusedModules,
      new passes.memlib.InferReadWrite(),
      new passes.memlib.ReplSeqMem(),
      new passes.clocklist.ClockListTransform()
    ),
    AnnotationMap(Seq(
      passes.memlib.InferReadWriteAnnotation(
        s"${synTop.get}"
      ),
      passes.clocklist.ClockListAnnotation(
        s"-c:${synTop.get}:-m:${synTop.get}:${listClocks.get}"
      ),
      passes.memlib.ReplSeqMemAnnotation(
        s"-c:${synTop.get}:${seqMemFlags.get}"
      )
    ))
  )
}
