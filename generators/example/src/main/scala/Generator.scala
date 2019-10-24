package example

import scala.util.Try

import chisel3._
import chisel3.experimental.{RawModule}
import chisel3.internal.firrtl.{Circuit}
import chisel3.stage.{ChiselStage, ChiselCli, ChiselOptions}
import firrtl.AnnotationSeq
import firrtl.options.{Phase, PhaseManager, PreservesAll, Shell, Stage, StageError, StageMain}
import firrtl.options.phases.DeletedWrapper
import firrtl.stage.FirrtlCli
import firrtl.options.Viewer.view
import firrtl.annotations.JsonProtocol

import freechips.rocketchip.config.{Parameters}
import freechips.rocketchip.util.{GeneratorApp, ParsedInputNames}
import freechips.rocketchip.system.{TestGeneration}
import freechips.rocketchip.diplomacy.{LazyModule}

import utilities.{TestSuiteHelper}

import java.io.{StringWriter, PrintWriter}
import java.io.{File, FileWriter}

class ElaborateStage extends Stage with PreservesAll[Phase] {
  val shell: Shell = new Shell("chisel") with ChiselCli with FirrtlCli

  private val targets =
    Seq( classOf[chisel3.stage.phases.Checks],
         classOf[chisel3.stage.phases.Elaborate],
         classOf[chisel3.stage.phases.MaybeAspectPhase]
         )

  //TODO: we only duplicate this class because targets is private
  // remove when chisel3#1216 is merged
  def run(annotations: AnnotationSeq): AnnotationSeq = try {
    new PhaseManager(targets) { override val wrappers = Seq( (a: Phase) => DeletedWrapper(a) ) }
      .transformOrder
      .map(firrtl.options.phases.DeletedWrapper(_))
      .foldLeft(annotations)( (a, f) => f.transform(a) )
  } catch {
    case ce: ChiselException =>
      val stackTrace = if (!view[ChiselOptions](annotations).printFullStackTrace) {
        ce.chiselStackTrace
      } else {
        val sw = new StringWriter
        ce.printStackTrace(new PrintWriter(sw))
        sw.toString
      }
      Predef
        .augmentString(stackTrace)
        .lines
        .foreach(line => println(s"Error: $line")) // scalastyle:ignore regex
      throw new StageError()
  }

}
object ElaborateMain extends StageMain(new ElaborateStage)

object Generator extends GeneratorApp {

  override lazy val names: ParsedInputNames = {
    require(args.size >= 5, "Usage: sbt> " +
      "run TargetDir TopModuleProjectName TopModuleName " +
      "ConfigProjectName ConfigNameString")
    ParsedInputNames(
      targetDir = args(0),
      topModuleProject = args(1),
      topModuleClass = args(2),
      configProject = args(3),
      configs = args(4))
  }

  // add unique test suites
  override def addTestSuites {
    implicit val p: Parameters = params
    TestSuiteHelper.addRocketTestSuites
    TestSuiteHelper.addBoomTestSuites

    // if hwacha parameter exists then generate its tests
    // TODO: find a more elegant way to do this. either through
    // trying to disambiguate BuildRoCC, having a AccelParamsKey,
    // or having the Accelerator/Tile add its own tests
    import hwacha.HwachaTestSuites._
    if (Try(p(hwacha.HwachaNLanes)).getOrElse(0) > 0) {
      TestGeneration.addSuites(rv64uv.map(_("p")))
      TestGeneration.addSuites(rv64uv.map(_("vp")))
      TestGeneration.addSuite(rv64sv("p"))
      TestGeneration.addSuite(hwachaBmarks)
    }
  }

  // specify the name that the generator outputs files as
  val longName = names.topModuleProject + "." + names.topModuleClass + "." + names.configs
  val additionalAnnos = new scala.collection.mutable.ArrayBuffer[firrtl.annotations.Annotation]()

  override def elaborate(fullTopModuleClassName: String, params: Parameters): Circuit = {
    val top = () =>
      Class.forName(fullTopModuleClassName)
          .getConstructor(classOf[Parameters])
          .newInstance(params) match {
        case m: RawModule => m
        case l: LazyModule => LazyModule(l).module
    }
    val annos = ElaborateMain.stage.execute(args, Seq(
      chisel3.stage.ChiselGeneratorAnnotation(top)))
    val nonCircuitAnnos = annos.filterNot{
      case a: chisel3.stage.ChiselCircuitAnnotation => true
      case a: aoplib.floorplan.MemberTracker => true // TODO: We'll probably need to serialize these at some point
      case a: firrtl.annotations.DeletedAnnotation => true
      case a: firrtl.options.TargetDirAnnotation => true // Barstools supplies this
      case _ => false
    }
    additionalAnnos ++= (nonCircuitAnnos)
    annos.collectFirst{ case a: chisel3.stage.ChiselCircuitAnnotation => a.circuit }.get
  }

  override def generateAnno {
    val annotationFile = new File(td, s"$longName.anno.json")
    val af = new FileWriter(annotationFile)
    val allAnnos = circuit.annotations.map(_.toFirrtl) ++ additionalAnnos
    af.write(JsonProtocol.serialize(allAnnos))
    af.close()
  }

  // generate files
  generateFirrtl
  generateAnno
  generateTestSuiteMakefrags
  generateArtefacts
}
