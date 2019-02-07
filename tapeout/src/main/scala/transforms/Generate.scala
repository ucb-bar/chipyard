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

trait HasTapeoutOptions { self: ExecutionOptionsManager with HasFirrtlOptions =>
  var tapeoutOptions = TapeoutOptions()

  parser.note("tapeout options")

  parser.opt[String]("top-o")
    .abbr("tto")
    .valueName("<top-output>")
    .foreach { x =>
      tapeoutOptions = tapeoutOptions.copy(
        topOutput = Some(x)
      )
    }.text {
      "use this to generate top at <top-output>"
    }

  parser.opt[String]("harness-o")
    .abbr("tho")
    .valueName("<harness-output>")
    .foreach { x =>
      tapeoutOptions = tapeoutOptions.copy(
        harnessOutput = Some(x)
      )
    }.text {
      "use this to generate a harness at <harness-output>"
    }

  parser.opt[String]("syn-top")
    .abbr("tst")
    .valueName("<syn-top>")
    .foreach { x =>
      tapeoutOptions = tapeoutOptions.copy(
        synTop = Some(x)
      )
    }.text {
      "use this to set synTop"
    }

  parser.opt[String]("harness-top")
    .abbr("tht")
    .valueName("<harness-top>")
    .foreach { x =>
      tapeoutOptions = tapeoutOptions.copy(
        harnessTop = Some(x)
      )
    }.text {
      "use this to set harnessTop"
    }

  parser.opt[String]("list-clocks")
    .abbr("tlc")
    .valueName("<clocks>")
    .foreach { x =>
      tapeoutOptions = tapeoutOptions.copy(
        listClocks = Some(x)
      )
    }.text {
      "use this to list <clocks>"
    }

  parser.note("")
}

case class TapeoutOptions(
  input: Option[String] = None,
  output: Option[String] = None,
  topOutput: Option[String] = None,
  harnessOutput: Option[String] = None,
  annoFile: Option[String] = None,
  synTop: Option[String] = None,
  harnessTop: Option[String] = None,
  seqMemFlags: Option[String] = Some("-o:unused.confg"),
  listClocks: Option[String] = Some("-o:unused.clocks")
) extends LazyLogging

// Requires two phases, one to collect modules below synTop in the hierarchy
// and a second to remove those modules to generate the test harness
sealed trait GenerateTopAndHarnessApp extends LazyLogging { this: App =>
  lazy val optionsManager = {
    val optionsManager = new ExecutionOptionsManager("tapeout") with HasFirrtlOptions with HasTapeoutOptions
    if (!optionsManager.parse(args)) {
      throw new Exception("Error parsing options!")
    }
    optionsManager
  }
  lazy val options = optionsManager.tapeoutOptions
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
    )

    val enumerate = if (harness) { Seq(
      new EnumerateModules( { m => if (m.name != options.harnessTop.get && m.name != options.synTop.get) { AllModules.add(m.name) } } ),
    ) } else Seq()

    val post = if (top) { Seq(
      new RemoveUnusedModules,
      new passes.memlib.InferReadWrite(),
      new passes.clocklist.ClockListTransform()
    ) } else Seq()

    pre ++ enumerate ++ post
  }

  private def getFirstPhaseAnnotations(top: Boolean): AnnotationSeq = {
    if (top) {
      //Load annotations from file
      val annotationArray: Seq[Annotation] = annoFile match {
        case None => Array[Annotation]()
        case Some(fileName) => {
          val annotations = new File(fileName)
          if(annotations.exists) {
            val annotationsYaml = io.Source.fromFile(annotations).getLines().mkString("\n")
            Seq(AnnotationUtils.fromYaml(annotationsYaml)) // TODO
          } else {
            Seq[Annotation]()
          }
        }
      }
      // add new annotations
      AnnotationSeq(Seq(
        passes.memlib.InferReadWriteAnnotation,
        passes.clocklist.ClockListAnnotation.parse(
          s"-c:${synTop.get}:-m:${synTop.get}:${listClocks.get}"
        ),
        passes.memlib.ReplSeqMemAnnotation.parse(
          s"-c:${synTop.get}:${seqMemFlags.get}"
        )
      ) ++ annotationArray)
    } else { AnnotationSeq(Seq.empty) }
  }

  private def getSecondPhasePasses: Seq[Transform] = {
    // always the same for now
    Seq(
      new ConvertToExtMod((m) => m.name == synTop.get),
      new RenameModulesAndInstances((m) => AllModules.rename(m)),
      // new RemoveUnusedModules,
    )
  }

  // always the same for now
  private def getSecondPhaseAnnotations: AnnotationSeq = AnnotationSeq(Seq.empty)

  // Top Generation
  protected def firstPhase(top: Boolean, harness: Boolean): Unit = {
    require(top || harness, "Must specify either top or harness")

    val firrtlOptions = optionsManager.firrtlOptions
    optionsManager.firrtlOptions = firrtlOptions.copy(
      annotations = firrtlOptions.annotations ++ getFirstPhaseAnnotations(top)
    )

    optionsManager.firrtlOptions = firrtlOptions.copy(
      customTransforms = firrtlOptions.customTransforms ++ getFirstPhasePasses(top, harness)
    )
  }

  // Harness Generation
  protected def secondPhase: Unit = {
    val firrtlOptions = optionsManager.firrtlOptions
    optionsManager.firrtlOptions = firrtlOptions.copy(
      annotations = firrtlOptions.annotations ++ getSecondPhaseAnnotations
    )

    optionsManager.firrtlOptions = firrtlOptions.copy(
      customTransforms = firrtlOptions.customTransforms ++ getSecondPhasePasses
    )
  }

  protected def execute: Unit = {
    firrtl.Driver.execute(optionsManager)
  }
}

object GenerateTop extends App with GenerateTopAndHarnessApp {
  // warn about unused options
  harnessOutput.foreach(n => logger.warn(s"Not using harness output filename $n since you asked for just a top-level output."))
  topOutput.foreach(
    n => logger.warn(s"Not using generic output filename $n since you asked for just a top-level output and also specified a generic output."))
  // Only need a single phase to generate the top module
  firstPhase(top = true, harness = false)
  execute
}

object GenerateHarness extends App with GenerateTopAndHarnessApp {
  // warn about unused options
  topOutput.foreach(n => logger.warn(s"Not using top-level output filename $n since you asked for just a test harness."))
  annoFile.foreach(n => logger.warn(s"Not using annotations file $n since you asked for just a test harness."))
  seqMemFlags.filter(_ != "-o:unused.confg").foreach {
    n => logger.warn(s"Not using SeqMem flags $n since you asked for just a test harness.") }
  listClocks.filter(_ != "-o:unused.clocks").foreach {
    n => logger.warn(s"Not using clocks list $n since you asked for just a test harness.") }
  harnessOutput.foreach(
    n => logger.warn(s"Not using generic output filename $n since you asked for just a test harness and also specified a generic output."))
  // Do minimal work for the first phase to generate test harness
  firstPhase(top = false, harness = true)
  secondPhase
  execute
}

object GenerateTopAndHarness extends App with GenerateTopAndHarnessApp {
  // warn about unused options
  output.foreach(n => logger.warn(s"Not using generic output filename $n since you asked for both a top-level output and a test harness."))
  // Do everything, top and harness generation
  firstPhase(top = true, harness = true)
  secondPhase
  execute
}
