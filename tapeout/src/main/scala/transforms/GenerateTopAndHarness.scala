// See LICENSE for license details.

package barstools.tapeout.transforms

import firrtl._
import firrtl.ir._
import firrtl.annotations._
import firrtl.passes.Pass

import java.io.File
import firrtl.annotations.AnnotationYamlProtocol._
import net.jcazevedo.moultingyaml._

object GenerateTopAndHarness extends App {
  var input: Option[String] = None
  var topOutput: Option[String] = None
  var harnessOutput: Option[String] = None
  var annoFile: Option[String] = None
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
      case "--top-o" => {
        topOutput = Some(args(i+1))
        usedOptions = usedOptions | Set(i+1)
      }
      case "--harness-o" => {
        harnessOutput = Some(args(i+1))
        usedOptions = usedOptions | Set(i+1)
      }
      case "--anno-file" => {
        annoFile = Some(args(i+1))
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
  //Load annotations from file
  val annotationArray = annoFile match {
    case None => Array[Annotation]()
    case Some(fileName) => {
      val annotations = new File(fileName)
      if(annotations.exists) {
        val annotationsYaml = io.Source.fromFile(annotations).getLines().mkString("\n").parseYaml
        annotationsYaml.convertTo[Array[Annotation]]
      } else {
        Array[Annotation]()
      }
    }
  }

  //Top Generation
  firrtl.Driver.compile(
    input.get,
    topOutput.get,
    new VerilogCompiler(),
    Parser.UseInfo,
    Seq(
      new ReParentCircuit(synTop.get),
      new RemoveUnusedModules,
      new EnumerateModules( { m => if (m.name != synTop.get) { AllModules.add(m.name) } } ),
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
    ) ++ annotationArray)
  )

  //Harness Generation
  firrtl.Driver.compile(
    input.get,
    harnessOutput.get,
    new VerilogCompiler(),
    Parser.UseInfo,
    Seq(
      new ConvertToExtMod((m) => m.name == synTop.get),
      new RemoveUnusedModules,
      new RenameModulesAndInstances((m) => AllModules.rename(m))
    )
  )
}

