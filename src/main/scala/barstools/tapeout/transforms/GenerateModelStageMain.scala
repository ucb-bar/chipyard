package barstools.tapeout.transforms

import barstools.tapeout.transforms.stage._
import firrtl._
import firrtl.annotations._
import firrtl.ir._
import firrtl.options.{Dependency, InputAnnotationFileAnnotation, StageMain}
import firrtl.stage.{FirrtlCircuitAnnotation, FirrtlStage, RunFirrtlTransformAnnotation}
import logger.LazyLogging

private class GenerateModelStageMain(annotations: AnnotationSeq) extends LazyLogging {
  val outAnno: Option[String] = annotations.collectFirst { case OutAnnoAnnotation(s) => s }

  val annoFiles: List[String] = annotations.flatMap {
    case InputAnnotationFileAnnotation(f) => Some(f)
    case _                                => None
  }.toList

  // Dump firrtl and annotation files
  // Use global param outAnno
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

  def executeStageMain(): Unit = {
    val annos = new FirrtlStage().execute(Array.empty, annotations)

    annos.collectFirst { case FirrtlCircuitAnnotation(circuit) => circuit } match {
      case Some(circuit) =>
        dumpAnnos(annos)
      case _ =>
        throw new Exception(s"executeStageMain failed while executing FIRRTL!\n")
    }
  }
}

// main run class
object GenerateModelStageMain extends StageMain(new TapeoutStage())
