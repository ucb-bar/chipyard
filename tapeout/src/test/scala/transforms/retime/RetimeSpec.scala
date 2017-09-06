// See LICENSE for license details.

package barstools.tapeout.transforms.retime.test

import chisel3._
import firrtl._
import org.scalatest.{FlatSpec, Matchers}
import chisel3.experimental._
import chisel3.util.HasBlackBoxInline
import chisel3.iotesters._
import barstools.tapeout.transforms.retime._

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
    chisel3.Driver.execute(Array("-td", s"test_run_dir/$dir", "-foaf", s"test_run_dir/$dir/final.anno"), gen) shouldBe a [ChiselExecutionSuccess]

    val lines = io.Source.fromFile(s"test_run_dir/$dir/final.anno").getLines().map(normalized).toSeq
    lines should contain ("Annotation(ModuleName(RetimeModule,CircuitName(RetimeModule)),class barstools.tapeout.transforms.retime.RetimeTransform,retime)")
  }
  
  // TODO(azidar): need to fix/add instance annotations
  ignore should "pass simple retime instance annotation" in {
    val gen = () => new RetimeInstance()
    val dir = uniqueDirName(gen, "RetimeInstance")
    chisel3.Driver.execute(Array("-td", s"test_run_dir/$dir", "-foaf", s"test_run_dir/$dir/final.anno"), gen) shouldBe a [ChiselExecutionSuccess]

    val lines = io.Source.fromFile(s"test_run_dir/$dir/final.anno").getLines().map(normalized).toSeq
    lines should contain ("Annotation(ComponentName(instance, ModuleName(RetimeInstance,CircuitName(RetimeInstance))),class barstools.tapeout.transforms.retime.RetimeTransform,retime)")
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
