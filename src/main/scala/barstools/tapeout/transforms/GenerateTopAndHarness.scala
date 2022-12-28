package barstools.tapeout.transforms

import barstools.tapeout.transforms.stage._
import firrtl._
import firrtl.annotations._
import firrtl.ir._
import firrtl.options.{Dependency, InputAnnotationFileAnnotation, StageMain}
import firrtl.passes.memlib.ReplSeqMemAnnotation
import firrtl.stage.{FirrtlCircuitAnnotation, FirrtlStage, OutputFileAnnotation, RunFirrtlTransformAnnotation}
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
  // Reads global params "outAnno"
  protected def dumpAnnos(
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

  // Top and harness generation
  def execute(): Unit = {
    val generatorAnnotations = annotations
      .filterNot(_.isInstanceOf[OutputFileAnnotation])
      .map {
        case ReplSeqMemAnnotation(i, _) => ReplSeqMemAnnotation(i, harnessConf.get)
        case anno                       => anno
      }

    val annos = new FirrtlStage().execute(Array.empty, generatorAnnotations)
    annos.collectFirst { case FirrtlCircuitAnnotation(circuit) => circuit } match {
      case Some(circuit) =>
        dumpAnnos(annos)
      case _ =>
        throw new Exception(s"executeTop failed while executing FIRRTL!\n")
    }
  }
}

object GenerateTopAndHarness extends StageMain(new TapeoutStage())
