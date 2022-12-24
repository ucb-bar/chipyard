package barstools.tapeout.transforms

import barstools.tapeout.transforms.stage._
import firrtl._
import firrtl.annotations._
import firrtl.ir._
import firrtl.options.{Dependency, InputAnnotationFileAnnotation, StageMain}
import firrtl.passes.memlib.ReplSeqMemAnnotation
import firrtl.stage.{FirrtlCircuitAnnotation, FirrtlStage, OutputFileAnnotation, RunFirrtlTransformAnnotation}
import firrtl.passes.{ConvertFixedToSInt}
import firrtl.transforms.BlackBoxResourceFileNameAnno
import logger.LazyLogging

// Requires two phases, one to collect modules below synTop in the hierarchy
// and a second to remove those modules to generate the test harness
private class GenerateTopAndHarness(annotations: AnnotationSeq) extends LazyLogging {
  val outAnno:     Option[String] = annotations.collectFirst { case OutAnnoAnnotation(s) => s }
  val harnessConf: Option[String] = annotations.collectFirst { case HarnessConfAnnotation(h) => h }

  val annoFiles: List[String] = annotations.flatMap {
    case InputAnnotationFileAnnotation(f) => Some(f)
    case _                                => None
  }.toList

  // Dump firrtl and annotation files
  protected def dump(
    circuit:     Circuit,
    annotations: AnnotationSeq
  ): Unit = {
    outAnno.foreach { annoPath =>
      val outputFile = new java.io.PrintWriter(annoPath)
      outputFile.write(JsonProtocol.serialize(annotations.filter(_ match {
        case _: DeletedAnnotation       => false
        case _: EmittedComponent        => false
        case _: EmittedAnnotation[_]    => false
        case _: FirrtlCircuitAnnotation => false
        case _: OutAnnoAnnotation       => false
        case _ => true
      })))
      outputFile.close()
    }
  }

  // TODO: Filter out blackbox dumping from this FIRRTL step, let CIRCT do it

  // Top Generation
  def executeTop(): Unit = {
    val annos = new FirrtlStage().execute(Array.empty, annotations)

    annos.collectFirst { case FirrtlCircuitAnnotation(circuit) => circuit } match {
      case Some(circuit) =>
        dump(circuit, annos)
      case _ =>
        throw new Exception(s"executeTop failed while executing FIRRTL!\n")
    }
  }

  // Top and harness generation
  def executeTopAndHarness(): Unit = {
    executeTop()

    // For harness run, change some firrtlOptions (below) for harness phase
    // customTransforms: setup harness transforms, add AvoidExtModuleCollisions
    // outputFileNameOverride: change to harnessOutput
    // conf file must change to harnessConf by mapping annotations
    val generatorAnnotations = annotations
      .filterNot(_.isInstanceOf[OutputFileAnnotation])
      .map {
        case ReplSeqMemAnnotation(i, _) => ReplSeqMemAnnotation(i, harnessConf.get)
        case anno                       => anno
      }

    val annos = new FirrtlStage().execute(Array.empty, generatorAnnotations)
    annos.collectFirst { case FirrtlCircuitAnnotation(circuit) => circuit } match {
      case Some(circuit) =>
        dump(circuit, annos)
      case _ =>
        throw new Exception(s"executeTop failed while executing FIRRTL!\n")
    }
  }
}

object GenerateTop extends StageMain(new TapeoutStage(doHarness = false))
object GenerateTopAndHarness extends StageMain(new TapeoutStage(doHarness = true))
