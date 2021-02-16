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
  val synTop: Option[String] = annotations.collectFirst { case SynTopAnnotation(s) => s }
  val topFir: Option[String] = annotations.collectFirst { case TopFirAnnotation(s) => s }
  val topAnnoOut: Option[String] = annotations.collectFirst { case TopAnnoOutAnnotation(s) => s }
  val harnessTop: Option[String] = annotations.collectFirst { case HarnessTopAnnotation(h) => h }
  val harnessConf: Option[String] = annotations.collectFirst { case HarnessConfAnnotation(h) => h }
  val harnessOutput: Option[String] = annotations.collectFirst { case HarnessOutputAnnotation(h) => h }
  val topDotfOut: Option[String] = annotations.collectFirst { case TopDotfOutAnnotation(h) => h }
  val harnessDotfOut: Option[String] = annotations.collectFirst { case HarnessDotfOutAnnotation(h) => h }

  val annoFiles: List[String] = annotations.flatMap {
    case InputAnnotationFileAnnotation(f) => Some(f)
    case _ => None
  }.toList

  // order is determined by DependencyAPIMigration
  val topTransforms = Seq(
    new ReParentCircuit,
    new RemoveUnusedModules
  )

  lazy val rootCircuitTarget = CircuitTarget(harnessTop.get)

  val topAnnos = synTop.map(st => ReParentCircuitAnnotation(rootCircuitTarget.module(st))) ++
    topDotfOut.map(BlackBoxResourceFileNameAnno)

  // Dump firrtl and annotation files
  protected def dump(
                      circuit: Circuit,
                      annotations: AnnotationSeq,
                      firFile: Option[String],
                      annoFile: Option[String]
                    ): Unit = {
    firFile.foreach { firPath =>
      val outputFile = new java.io.PrintWriter(firPath)
      outputFile.write(circuit.serialize)
      outputFile.close()
    }
    annoFile.foreach { annoPath =>
      val outputFile = new java.io.PrintWriter(annoPath)
      outputFile.write(JsonProtocol.serialize(annotations.filter(_ match {
        case _: DeletedAnnotation => false
        case _: EmittedComponent => false
        case _: EmittedAnnotation[_] => false
        case _: FirrtlCircuitAnnotation => false
        case _ => true
      })))
      outputFile.close()
    }
  }

  // Top Generation
  def executeTop(): Seq[ExtModule] = {
    val annos = new FirrtlStage().execute(Array.empty, annotations ++ Seq(
      RunFirrtlTransformAnnotation(Dependency[ReParentCircuit]),
      RunFirrtlTransformAnnotation(Dependency[RemoveUnusedModules])
    ))
    annos.collectFirst { case FirrtlCircuitAnnotation(circuit) => circuit } match {
      case Some(circuit) =>
        dump(circuit, annos, topFir, topAnnoOut)
        circuit.modules.collect { case e: ExtModule => e }
      case _ =>
        throw new Exception(s"executeTop failed while executing FIRRTL!\n")
    }
  }

  // Top and harness generation
  def executeTopAndHarness(): Unit = {
    // Execute top and get list of ExtModules to avoid collisions
    val topExtModules = executeTop()

    // order is determined by DependencyAPIMigration
    val harnessAnnos =
      harnessDotfOut.map(BlackBoxResourceFileNameAnno).toSeq ++
        harnessTop.map(ht => ModuleNameSuffixAnnotation(rootCircuitTarget, s"_in${ht}")) ++
        synTop.map(st => ConvertToExtModAnnotation(rootCircuitTarget.module(st))) ++
        Seq(
          LinkExtModulesAnnotation(topExtModules),
          RunFirrtlTransformAnnotation(Dependency[ConvertToExtMod]),
          RunFirrtlTransformAnnotation(Dependency[RemoveUnusedModules]),
          RunFirrtlTransformAnnotation(Dependency[AvoidExtModuleCollisions]),
          RunFirrtlTransformAnnotation(Dependency[AddSuffixToModuleNames])
        )

    // For harness run, change some firrtlOptions (below) for harness phase
    // customTransforms: setup harness transforms, add AvoidExtModuleCollisions
    // outputFileNameOverride: change to harnessOutput
    // conf file must change to harnessConf by mapping annotations

    val generatorAnnotations = annotations
      .filterNot(_.isInstanceOf[OutputFileAnnotation])
      .map {
      case ReplSeqMemAnnotation(i, _) => ReplSeqMemAnnotation(i, harnessConf.get)
      case HarnessOutputAnnotation(s) => OutputFileAnnotation(s)
      case anno => anno
    } ++ harnessAnnos

    val annos = new FirrtlStage().execute(Array.empty, generatorAnnotations)
    annos.collectFirst { case FirrtlCircuitAnnotation(circuit) => circuit } match {
      case Some(circuit) =>
        dump(circuit, annos, topFir, topAnnoOut)
      case _ =>
        throw new Exception(s"executeTop failed while executing FIRRTL!\n")
    }
  }
}


object GenerateTop extends StageMain(new TapeoutStage(doHarness = false))

object GenerateTopAndHarness extends StageMain(new TapeoutStage(doHarness = true))
