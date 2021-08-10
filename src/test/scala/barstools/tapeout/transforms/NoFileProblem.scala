//// SPDX-License-Identifier: Apache-2.0
//
//package barstools.tapeout.transforms
//
//import firrtl.AnnotationSeq
//import firrtl.options.{Shell, Stage, StageMain}
//import firrtl.stage.{FirrtlCli, FirrtlStage}
//import logger.Logger.OutputCaptor
//import logger.{LazyLogging, LogLevel, Logger}
//import org.scalatest.freespec.AnyFreeSpec
//
//import java.io.{ByteArrayOutputStream, PrintStream}
//
//class NoFileStage extends Stage {
//  override val shell: Shell = new Shell(applicationName = "tapeout") with FirrtlCli
//
//  override def run(annotations: AnnotationSeq): AnnotationSeq = {
//    Logger.makeScope(annotations) {
//      val annos = new FirrtlStage().execute(Array.empty, annotations)
//    }
//    annotations
//  }
//}
//
//class NoFileGenerator(annotationSeq: AnnotationSeq) extends LazyLogging {
//
//}
//
//object NoFileGenerator extends StageMain(new NoFileStage)
//
//class NoFileProblem extends AnyFreeSpec {
//  //  "should fail in a way that discloses missing file" - {
//  //    (new NoFileStage).execute(Array("-i", "jackalope"), Seq.empty)
//  //  }
//
//  "should fail in a way that discloses missing file with output capture" in {
//    val buffer = new ByteArrayOutputStream()
//    Console.withOut(new PrintStream(buffer)) {
//      NoFileGenerator.main(Array("-i", "jackalope", "-ll", "info"))
//    }
//    println(buffer.toString)
//  }
//
//  "don't uses Console.withOut" in {
//    val captor = new OutputCaptor
//    Logger.setOutput(captor.printStream)
//    Logger.setLevel(getClass.getName, LogLevel.Info)
//    NoFileGenerator.main(Array("-i", "jackalope", "-ll", "info"))
//    println(captor.getOutputAsString)
//  }
//}
