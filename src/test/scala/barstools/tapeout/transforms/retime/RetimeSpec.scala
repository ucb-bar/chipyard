// See LICENSE for license details.

package barstools.tapeout.transforms.retime

import chisel3._
import chisel3.stage.{ChiselGeneratorAnnotation, ChiselStage}
import firrtl.{EmittedFirrtlCircuitAnnotation, EmittedFirrtlModuleAnnotation, FileUtils}
import logger.Logger
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class RetimeSpec extends AnyFlatSpec with Matchers {
  def normalized(s: String): String = {
    require(!s.contains("\n"))
    s.replaceAll("\\s+", " ").trim
  }
  def uniqueDirName[T](gen: => T, name: String): String = {
    val genClassName = gen.getClass.getName
    name + genClassName.hashCode.abs
  }
  def getLowFirrtl[T <: RawModule](gen: () => T, extraArgs: Array[String] = Array.empty): String = {
    // generate low firrtl
    (new ChiselStage)
      .execute(
        Array("-X", "low") ++ extraArgs,
        Seq(ChiselGeneratorAnnotation(gen))
      )
      .collect {
        case EmittedFirrtlCircuitAnnotation(a) => a
        case EmittedFirrtlModuleAnnotation(a)  => a
      }
      .map(_.value)
      .mkString("")
  }

  behavior.of("retime library")

  it should "pass simple retime module annotation" in {
    val gen = () => new RetimeModule
    val dir = uniqueDirName(gen, "RetimeModule")

    Logger.makeScope(Seq.empty) {
      val captor = new Logger.OutputCaptor
      Logger.setOutput(captor.printStream)

      // generate low firrtl
      val firrtl = getLowFirrtl(
        gen,
        Array("-td", s"test_run_dir/$dir", "-foaf", s"test_run_dir/$dir/final", "--log-level", "info")
      )

      firrtl.nonEmpty should be(true)
      //Make sure we got the RetimeTransform scheduled
      captor.getOutputAsString should include("barstools.tapeout.transforms.retime.RetimeTransform")
    }

    val lines = FileUtils
      .getLines(s"test_run_dir/$dir/test_run_dir/$dir/final.anno.json")
      .map(normalized)
      .mkString("\n")
    lines should include("barstools.tapeout.transforms.retime.RetimeAnnotation")
    lines should include(""""target":"RetimeModule.RetimeModule"""")
  }

  it should "pass simple retime instance annotation" in {
    val gen = () => new RetimeInstance
    val dir = uniqueDirName(gen, "RetimeInstance")

    Logger.makeScope(Seq.empty) {
      val captor = new Logger.OutputCaptor
      Logger.setOutput(captor.printStream)

      // generate low firrtl
      val firrtl = getLowFirrtl(
        gen,
        Array("-td", s"test_run_dir/$dir", "-foaf", s"test_run_dir/$dir/final", "--log-level", "info")
      )

      firrtl.nonEmpty should be(true)
      //Make sure we got the RetimeTransform scheduled
      captor.getOutputAsString should include("barstools.tapeout.transforms.retime.RetimeTransform")
    }

    val lines = FileUtils
      .getLines(s"test_run_dir/$dir/test_run_dir/$dir/final.anno.json")
      .map(normalized)
      .mkString("\n")
    lines should include("barstools.tapeout.transforms.retime.RetimeAnnotation")
    lines should include(""""target":"RetimeInstance.MyModule"""")
  }
}

class RetimeModule extends Module with RetimeLib {
  val io = IO(new Bundle {
    val in = Input(UInt(15.W))
    val out = Output(UInt(15.W))
  })
  io.out := io.in
  retime(this)
}

class MyModule extends Module with RetimeLib {
  val io = IO(new Bundle {
    val in = Input(UInt(15.W))
    val out = Output(UInt(15.W))
  })
  io.out := io.in
}

class RetimeInstance extends Module with RetimeLib {
  val io = IO(new Bundle {
    val in = Input(UInt(15.W))
    val out = Output(UInt(15.W))
  })
  val instance = Module(new MyModule)
  retime(instance)
  instance.io.in := io.in
  io.out := instance.io.out
}
