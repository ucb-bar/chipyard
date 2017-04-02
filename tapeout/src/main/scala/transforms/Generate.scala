package barstools.tapeout.transforms

import firrtl._
import firrtl.ir._
import firrtl.annotations._
import firrtl.passes.Pass

import java.io.File
import firrtl.annotations.AnnotationYamlProtocol._
import net.jcazevedo.moultingyaml._
import com.typesafe.scalalogging.LazyLogging

object AllModules {
  private var modules = Set[String]()
  def add(module: String) = {
    modules = modules | Set(module)
  }
  def rename(module: String) = {
    var new_name = module
    while (modules.contains(new_name))
      new_name = new_name + "_inTestHarness"
    new_name
  }
}

case class ParsedInput(args: Seq[String]) extends LazyLogging {
  var input: Option[String] = None
  var output: Option[String] = None
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
      case "-o" => {
        output = Some(args(i+1))
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
          logger.error("Unknown option " + arg)
        }
      }
    }
  }

}

// Requires two phases, one to collect modules below synTop in the hierarchy
// and a second to remove those modules to generate the test harness
sealed trait GenerateTopAndHarnessApp extends App with LazyLogging {
  lazy val options: ParsedInput = ParsedInput(args)
  lazy val input = options.input
  lazy val output = options.output
  lazy val topOutput = options.topOutput
  lazy val harnessOutput = options.harnessOutput
  lazy val annoFile = options.annoFile
  lazy val synTop = options.synTop
  lazy val harnessTop = options.harnessTop
  lazy val seqMemFlags = options.seqMemFlags
  lazy val listClocks = options.listClocks

  private def getFirstPhasePasses(top: Boolean, harness: Boolean): Seq[Transform] = {
    val pre = Seq(
      new ReParentCircuit(synTop.get),
      new RemoveUnusedModules
    )

    val enumerate = if (harness) { Seq(
      new EnumerateModules( { m => if (m.name != options.synTop.get) { AllModules.add(m.name) } } )
    ) } else Seq()

    val post = if (top) { Seq(
      new passes.memlib.InferReadWrite(),
      new passes.memlib.ReplSeqMem(),
      new AnalogRenamer(),
      new passes.clocklist.ClockListTransform()
    ) } else Seq()

    pre ++ enumerate ++ post
  }

  private def getFirstPhaseAnnotations(top: Boolean): AnnotationMap = {
    if (top) { 
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
      // add new annotations
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
    } else { AnnotationMap(Seq.empty) }
  }

  private def getSecondPhasePasses: Seq[Transform] = {
    // always the same for now
    Seq(
      new ConvertToExtMod((m) => m.name == synTop.get),
      new RemoveUnusedModules,
      new RenameModulesAndInstances((m) => AllModules.rename(m)),
      new AnalogRenamer()
    )
  }

  // always the same for now
  private def getSecondPhaseAnnotations: AnnotationMap = {
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
    AnnotationMap(annotationArray)
  }

  // Top Generation
  protected def firstPhase(top: Boolean, harness: Boolean): Unit = {
    require(top || harness, "Must specify either top or harness")
    firrtl.Driver.compile(
      input.get,
      topOutput.getOrElse(output.get),
      new VerilogCompiler(),
      Parser.UseInfo,
      getFirstPhasePasses(top, harness),
      getFirstPhaseAnnotations(top)
    )
  }

  // Harness Generation
  protected def secondPhase: Unit = {
    firrtl.Driver.compile(
      input.get,
      harnessOutput.getOrElse(output.get),
      new VerilogCompiler(),
      Parser.UseInfo,
      getSecondPhasePasses,
      getSecondPhaseAnnotations
    )
  }
}

object GenerateTop extends GenerateTopAndHarnessApp {
  // warn about unused options
  harnessOutput.foreach(n => logger.warn(s"Not using harness output filename $n since you asked for just a top-level output."))
  topOutput.foreach(_.foreach{
    n => logger.warn(s"Not using generic output filename $n since you asked for just a top-level output and also specified a generic output.")})
  // Only need a single phase to generate the top module
  firstPhase(top = true, harness = false)
}

object GenerateHarness extends GenerateTopAndHarnessApp {
  // warn about unused options
  topOutput.foreach(n => logger.warn(s"Not using top-level output filename $n since you asked for just a test harness."))
  annoFile.foreach(n => logger.warn(s"Not using annotations file $n since you asked for just a test harness."))
  seqMemFlags.filter(_ != "-o:unused.confg").foreach {
    n => logger.warn(s"Not using SeqMem flags $n since you asked for just a test harness.") }
  listClocks.filter(_ != "-o:unused.clocks").foreach {
    n => logger.warn(s"Not using clocks list $n since you asked for just a test harness.") }
  harnessOutput.foreach(_.foreach{
    n => logger.warn(s"Not using generic output filename $n since you asked for just a test harness and also specified a generic output.")})
  // Do minimal work for the first phase to generate test harness
  firstPhase(top = false, harness = true)
  secondPhase
}

object GenerateTopAndHarness extends GenerateTopAndHarnessApp {
  // warn about unused options
  output.foreach(n => logger.warn(s"Not using generic output filename $n since you asked for both a top-level output and a test harness."))
  // Do everything, top and harness generation
  firstPhase(top = true, harness = true)
  secondPhase
}
