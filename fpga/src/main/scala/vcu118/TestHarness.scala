package chipyard.fpga.vcu118

import chisel3._
import chisel3.experimental.{Analog, IO}

import freechips.rocketchip.diplomacy.{LazyModule, LazyRawModuleImp}
import freechips.rocketchip.config.{Parameters}
import freechips.rocketchip.diplomacy.{InModuleBody}

import sifive.fpgashells.shell.xilinx._
import sifive.fpgashells.ip.xilinx._
import sifive.fpgashells.shell._
import sifive.fpgashells.clocks._

import chipyard.{BuildTop, HasHarnessSignalReferences, HasTestHarnessFunctions}

class VCU118FPGATestHarness(override implicit val p: Parameters) extends VCU118Shell with HasHarnessSignalReferences {
  val pllResetAsReset = InModuleBody{ Wire(Reset()) }

  InModuleBody {
    pllResetAsReset := pllReset
  }

  lazy val harnessClock = this.module.sysclk
  lazy val harnessReset = pllResetAsReset.getWrappedValue
  val success = false.B
  lazy val dutReset = pllResetAsReset.getWrappedValue

  // must be after HasHarnessSignalReferences assignments
  println(s"DEBUG: ----- sz:${topDesign.harnessFunctions.size}")
  topDesign match { case d: HasTestHarnessFunctions =>
    println(s"DEBUG: ----- sz:${d.harnessFunctions.size}")
    d.harnessFunctions.foreach(_(this))
  }
}

