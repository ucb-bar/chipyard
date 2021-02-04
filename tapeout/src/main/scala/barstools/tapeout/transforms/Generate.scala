package barstools.tapeout.transforms

import firrtl._
import firrtl.annotations._
import firrtl.ir._
import firrtl.passes.memlib.ReplSeqMemAnnotation
import firrtl.stage.FirrtlCircuitAnnotation
import firrtl.transforms.BlackBoxResourceFileNameAnno
import logger.LazyLogging

trait HasTapeoutOptions { self: ExecutionOptionsManager with HasFirrtlOptions =>
  var tapeoutOptions = TapeoutOptions()

  parser.note("tapeout options")

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

  parser.opt[String]("top-fir")
    .abbr("tsf")
    .valueName("<top-fir>")
    .foreach { x =>
      tapeoutOptions = tapeoutOptions.copy(
        topFir = Some(x)
      )
    }.text {
      "use this to set topFir"
    }

  parser.opt[String]("top-anno-out")
    .abbr("tsaof")
    .valueName("<top-anno-out>")
    .foreach { x =>
      tapeoutOptions = tapeoutOptions.copy(
        topAnnoOut = Some(x)
      )
    }.text {
      "use this to set topAnnoOut"
    }

  parser.opt[String]("top-dotf-out")
    .abbr("tdf")
    .valueName("<top-dotf-out>")
    .foreach { x =>
      tapeoutOptions = tapeoutOptions.copy(
        topDotfOut = Some(x)
      )
    }.text {
      "use this to set the filename for the top resource .f file"
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

  parser.opt[String]("harness-fir")
    .abbr("thf")
    .valueName("<harness-fir>")
    .foreach { x =>
      tapeoutOptions = tapeoutOptions.copy(
        harnessFir = Some(x)
      )
    }.text {
      "use this to set harnessFir"
    }

  parser.opt[String]("harness-anno-out")
    .abbr("thaof")
    .valueName("<harness-anno-out>")
    .foreach { x =>
      tapeoutOptions = tapeoutOptions.copy(
        harnessAnnoOut = Some(x)
      )
    }.text {
      "use this to set harnessAnnoOut"
    }

  parser.opt[String]("harness-dotf-out")
    .abbr("hdf")
    .valueName("<harness-dotf-out>")
    .foreach { x =>
      tapeoutOptions = tapeoutOptions.copy(
        harnessDotfOut = Some(x)
      )
    }.text {
      "use this to set the filename for the harness resource .f file"
    }

  parser.opt[String]("harness-conf")
    .abbr("thconf")
    .valueName ("<harness-conf-file>")
    .foreach { x =>
      tapeoutOptions = tapeoutOptions.copy(
        harnessConf = Some(x)
      )
    }.text {
      "use this to set the harness conf file location"
    }

}

case class TapeoutOptions(
  harnessOutput: Option[String] = None,
  synTop: Option[String] = None,
  topFir: Option[String] = None,
  topAnnoOut: Option[String] = None,
  topDotfOut: Option[String] = None,
  harnessTop: Option[String] = None,
  harnessFir: Option[String] = None,
  harnessAnnoOut: Option[String] = None,
  harnessDotfOut: Option[String] = None,
  harnessConf: Option[String] = None
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
  lazy val tapeoutOptions = optionsManager.tapeoutOptions
  // Tapeout options
  lazy val synTop = tapeoutOptions.synTop
  lazy val harnessTop = tapeoutOptions.harnessTop
  lazy val firrtlOptions = optionsManager.firrtlOptions
  // FIRRTL options
  lazy val annoFiles = firrtlOptions.annotationFileNames

  // order is determined by DependencyAPIMigration
  val topTransforms = Seq(
    new ReParentCircuit,
    new RemoveUnusedModules
  )

  lazy val rootCircuitTarget = CircuitTarget(harnessTop.get)

  lazy val topAnnos = synTop.map(st => ReParentCircuitAnnotation(rootCircuitTarget.module(st))) ++
    tapeoutOptions.topDotfOut.map(BlackBoxResourceFileNameAnno(_))

  lazy val topOptions = firrtlOptions.copy(
    customTransforms = firrtlOptions.customTransforms ++ topTransforms,
    annotations = firrtlOptions.annotations ++ topAnnos
  )

  // order is determined by DependencyAPIMigration
  val harnessTransforms = Seq(
    new ConvertToExtMod,
    new RemoveUnusedModules,
    new AvoidExtModuleCollisions,
    new AddSuffixToModuleNames
  )

  // Dump firrtl and annotation files
  protected def dump(res: FirrtlExecutionSuccess, firFile: Option[String], annoFile: Option[String]): Unit = {
    firFile.foreach { firPath =>
      val outputFile = new java.io.PrintWriter(firPath)
      outputFile.write(res.circuitState.circuit.serialize)
      outputFile.close()
    }
    annoFile.foreach { annoPath =>
      val outputFile = new java.io.PrintWriter(annoPath)
      outputFile.write(JsonProtocol.serialize(res.circuitState.annotations.filter(_ match {
        case da: DeletedAnnotation => false
        case ec: EmittedComponent => false
        case ea: EmittedAnnotation[_] => false
        case fca: FirrtlCircuitAnnotation => false
        case _ => true
      })))
      outputFile.close()
    }
  }

  // Top Generation
  protected def executeTop(): Seq[ExtModule] = {
    optionsManager.firrtlOptions = topOptions
    val result = firrtl.Driver.execute(optionsManager)
    result match {
      case x: FirrtlExecutionSuccess =>
        dump(x, tapeoutOptions.topFir, tapeoutOptions.topAnnoOut)
        x.circuitState.circuit.modules.collect{ case e: ExtModule => e }
      case x =>
        throw new Exception(s"executeTop failed while executing FIRRTL!\n${x}")
    }
  }

  // Top and harness generation
  protected def executeTopAndHarness(): Unit = {
    // Execute top and get list of ExtModules to avoid collisions
    val topExtModules = executeTop()

    val harnessAnnos =
      tapeoutOptions.harnessDotfOut.map(BlackBoxResourceFileNameAnno(_)).toSeq ++
      harnessTop.map(ht => ModuleNameSuffixAnnotation(rootCircuitTarget, s"_in${ht}")) ++
      synTop.map(st => ConvertToExtModAnnotation(rootCircuitTarget.module(st))) :+
      LinkExtModulesAnnotation(topExtModules)

    // For harness run, change some firrtlOptions (below) for harness phase
    // customTransforms: setup harness transforms, add AvoidExtModuleCollisions
    // outputFileNameOverride: change to harnessOutput
    // conf file must change to harnessConf by mapping annotations
    optionsManager.firrtlOptions = firrtlOptions.copy(
      customTransforms = firrtlOptions.customTransforms ++ harnessTransforms,
      outputFileNameOverride = tapeoutOptions.harnessOutput.get,
      annotations = firrtlOptions.annotations.map({
        case ReplSeqMemAnnotation(i, o) => ReplSeqMemAnnotation(i, tapeoutOptions.harnessConf.get)
        case a => a
      }) ++ harnessAnnos
    )
    val harnessResult = firrtl.Driver.execute(optionsManager)
    harnessResult match {
      case x: FirrtlExecutionSuccess => dump(x, tapeoutOptions.harnessFir, tapeoutOptions.harnessAnnoOut)
      case x => throw new Exception(s"executeHarness failed while executing FIRRTL!\n${x}")
    }
  }
}

object GenerateTop extends App with GenerateTopAndHarnessApp {
  // Only need a single phase to generate the top module
  executeTop()
}

object GenerateTopAndHarness extends App with GenerateTopAndHarnessApp {
  executeTopAndHarness()
}
