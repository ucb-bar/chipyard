package barstools.tapeout.transforms

import firrtl._
import firrtl.ir._
import firrtl.annotations._
import firrtl.stage.FirrtlCircuitAnnotation
import firrtl.passes.Pass

import java.io.File
import firrtl.annotations.AnnotationYamlProtocol._
import net.jcazevedo.moultingyaml._
import com.typesafe.scalalogging.LazyLogging

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

}

case class TapeoutOptions(
  harnessOutput: Option[String] = None,
  synTop: Option[String] = None,
  topFir: Option[String] = None,
  topAnnoOut: Option[String] = None,
  harnessTop: Option[String] = None,
  harnessFir: Option[String] = None,
  harnessAnnoOut: Option[String] = None
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

  private def topTransforms: Seq[Transform] = {
    Seq(
      new ReParentCircuit(synTop.get),
      new RemoveUnusedModules
    )
  }


  private def harnessTransforms: Seq[Transform] = {
    // XXX this is a hack, we really should be checking the masters to see if they are ExtModules
    val externals = Set(harnessTop.get, synTop.get, "SimSerial", "SimDTM")
    Seq(
      new ConvertToExtMod((m) => m.name == synTop.get),
      new RemoveUnusedModules,
      new RenameModulesAndInstances((old) => if (externals contains old) old else (old + "_in" + harnessTop.get))
    )
  }

  // Top Generation
  protected def executeTop: Unit = {

    optionsManager.firrtlOptions = optionsManager.firrtlOptions.copy(
      customTransforms = firrtlOptions.customTransforms ++ topTransforms
    )

    val result = firrtl.Driver.execute(optionsManager)

    result match {
      case x: FirrtlExecutionSuccess =>
        tapeoutOptions.topFir.foreach { firFile =>
          val outputFile = new java.io.PrintWriter(firFile)
          outputFile.write(x.circuitState.circuit.serialize)
          outputFile.close()
        }
        tapeoutOptions.topAnnoOut.foreach { annoFile =>
          val outputFile = new java.io.PrintWriter(annoFile)
          outputFile.write(JsonProtocol.serialize(x.circuitState.annotations.filter(_ match {
            case ea: EmittedAnnotation[_] => false
            case fca: FirrtlCircuitAnnotation => false
            case _ => true
          })))
          outputFile.close()
        }
      case _ =>
    }

  }

  // Harness Generation
  protected def executeHarness: Unit = {

    optionsManager.firrtlOptions = optionsManager.firrtlOptions.copy(
      customTransforms = firrtlOptions.customTransforms ++ harnessTransforms
    )

    val result = firrtl.Driver.execute(optionsManager)

    result match {
      case x: FirrtlExecutionSuccess =>
        tapeoutOptions.harnessFir.foreach { firFile =>
          val outputFile = new java.io.PrintWriter(firFile)
          outputFile.write(x.circuitState.circuit.serialize)
          outputFile.close()
        }
        tapeoutOptions.harnessAnnoOut.foreach { annoFile =>
          val outputFile = new java.io.PrintWriter(annoFile)
          outputFile.write(JsonProtocol.serialize(x.circuitState.annotations.filter(_ match {
            case ea: EmittedAnnotation[_] => false
            case fca: FirrtlCircuitAnnotation => false
            case _ => true
          })))
          outputFile.close()
        }
      case _ =>
    }
  }
}

object GenerateTop extends App with GenerateTopAndHarnessApp {
  // Only need a single phase to generate the top module
  executeTop
}

object GenerateHarness extends App with GenerateTopAndHarnessApp {
  // Do minimal work for the first phase to generate test harness
  executeHarness
}
