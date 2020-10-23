// See LICENSE for license details.

package barstools.tapeout.transforms.retime.test

import barstools.tapeout.transforms.retime._
import chisel3._
import chisel3.stage.ChiselStage
import firrtl._
import logger.Logger
import org.scalatest.{FlatSpec, Matchers}

class RetimeSpec extends FlatSpec with Matchers {
  def normalized(s: String): String = {
    require(!s.contains("\n"))
    s.replaceAll("\\s+", " ").trim
  }
  def uniqueDirName[T](gen: => T, name: String): String = {
    val genClassName = gen.getClass.getName
    name + genClassName.hashCode.abs
  }

  behavior of "retime library"

  it should "pass simple retime module annotation" in {
    val gen = () => new RetimeModule()
    val dir = uniqueDirName(gen, "RetimeModule")

    Logger.makeScope(Seq.empty) {
      val captor = new Logger.OutputCaptor
      Logger.setOutput(captor.printStream)
      val firrtl = (new ChiselStage).emitFirrtl(
        new RetimeModule(),
        Array("-td", s"test_run_dir/$dir", "-foaf", s"test_run_dir/$dir/final", "--log-level", "info")
      )
      firrtl.nonEmpty should be(true)
      //Make sure we got the RetimeTransform scheduled
      captor.getOutputAsString should include ("barstools.tapeout.transforms.retime.RetimeTransform")
    }

    val lines = FileUtils.getLines(s"test_run_dir/$dir/test_run_dir/$dir/final.anno.json")
      .map(normalized)
      .mkString("\n")
    lines should include("barstools.tapeout.transforms.retime.RetimeAnnotation")
    lines should include(""""target":"RetimeModule.RetimeModule"""")
  }

  it should "pass simple retime instance annotation" in {
    val gen = () => new RetimeInstance()
    val dir = uniqueDirName(gen, "RetimeInstance")

    Logger.makeScope(Seq.empty) {
      val captor = new Logger.OutputCaptor
      Logger.setOutput(captor.printStream)
      val firrtl = (new ChiselStage).emitFirrtl(
        new RetimeInstance(),
        Array("-td", s"test_run_dir/$dir", "-foaf", s"test_run_dir/$dir/final", "--log-level", "info")
      )
      firrtl.nonEmpty should be(true)
      //Make sure we got the RetimeTransform scheduled
      captor.getOutputAsString should include ("barstools.tapeout.transforms.retime.RetimeTransform")
    }

    val lines = FileUtils.getLines(s"test_run_dir/$dir/test_run_dir/$dir/final.anno.json")
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
