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
  def rename(module: String, suffix: String = "_inTestHarness") = {
    var new_name = module
    while (modules.contains(new_name))
      new_name = new_name + suffix
    new_name
  }
}

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

  parser.note("")
}

case class TapeoutOptions(
  harnessOutput: Option[String] = None,
  synTop: Option[String] = None,
  harnessTop: Option[String] = None
) extends LazyLogging

// Requires two phases, one to collect modules below synTop in the hierarchy
// and a second to remove those modules to generate the test harness
sealed trait GenerateTopAndHarnessApp extends LazyLogging { this: App =>
  def getOptionsManager = {
    val optionsManager = new ExecutionOptionsManager("tapeout") with HasFirrtlOptions with HasTapeoutOptions
    if (!optionsManager.parse(args)) {
      throw new Exception("Error parsing options!")
    }
    optionsManager
  }
  lazy val optionsManager = getOptionsManager
  lazy val tapeoutOptions = optionsManager.tapeoutOptions
  // Tapeout options
  lazy val harnessOutput = tapeoutOptions.harnessOutput
  lazy val synTop = tapeoutOptions.synTop
  lazy val harnessTop = tapeoutOptions.harnessTop

  lazy val firrtlOptions = optionsManager.firrtlOptions
  // FIRRTL options
  lazy val annoFiles = firrtlOptions.annotationFileNames

  private def getFirstPhasePasses: Seq[Transform] = {
    Seq(
      new ReParentCircuit(synTop.get),
      new RemoveUnusedModules
    )
  }

  private def getSecondPhasePasses: Seq[Transform] = {
    Seq(
      new ConvertToExtMod((m) => m.name == synTop.get),
      new EnumerateModules( { m => if (m.name != tapeoutOptions.harnessTop.get && m.name != tapeoutOptions.synTop.get) { AllModules.add(m.name) } } ),
      new RenameModulesAndInstances((m) => AllModules.rename(m, "_in" + harnessTop.get)),
      new RemoveUnusedModules
    )
  }

  // Top Generation
  protected def firstPhase: Unit = {

    val firstPhaseOptions = getOptionsManager
    firstPhaseOptions.firrtlOptions = firstPhaseOptions.firrtlOptions.copy(
      customTransforms = firrtlOptions.customTransforms ++ getFirstPhasePasses
    )

    firrtl.Driver.execute(firstPhaseOptions)
  }

  // Harness Generation
  protected def secondPhase: Unit = {
    val secondPhaseOptions = getOptionsManager
    secondPhaseOptions.firrtlOptions = secondPhaseOptions.firrtlOptions.copy(
      outputFileNameOverride = harnessOutput.get,
      customTransforms = getSecondPhasePasses
    )

    firrtl.Driver.execute(secondPhaseOptions)
  }
}

object GenerateTop extends App with GenerateTopAndHarnessApp {
  // Only need a single phase to generate the top module
  firstPhase
}

object GenerateHarness extends App with GenerateTopAndHarnessApp {
  // Do minimal work for the first phase to generate test harness
  secondPhase
}

object GenerateTopAndHarness extends App with GenerateTopAndHarnessApp {
  // Do everything, top and harness generation
  firstPhase
  secondPhase
}
