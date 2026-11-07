//> using scala "2.13.16"
//> using repository sonatype-s01:snapshots
//> using dep "org.chipsalliance::chisel::7.0.0-M2+508-57ab35eb-SNAPSHOT"
//> using plugin "org.chipsalliance:::chisel-plugin::7.0.0-M2+508-57ab35eb-SNAPSHOT"
//> using options "-unchecked", "-deprecation", "-language:reflectiveCalls", "-feature", "-Xcheckinit", "-Xfatal-warnings", "-Ywarn-dead-code", "-Ywarn-unused", "-Ymacro-annotations"

package boom.v4.util

import chisel3._
import chisel3.util.{log2Ceil,log2Floor, Cat,Mux1H}
import chisel3.experimental.hierarchy.{Definition, Instance, instantiable, public}
import circt.stage.ChiselStage

@instantiable
class ParallelFindOne(decodeWidth : Int, favor: Boolean) extends Module {

  override val desiredName = s"Parallel_${decodeWidth}_f${favor}_FindOne"
  @public val bitmask = IO(Input(UInt(decodeWidth.W)))
  @public val success = IO(Output(new Bool()))
  @public val index = IO(Output(UInt(log2Ceil(decodeWidth).W)))

  // must be log of 2
  require(log2Ceil(decodeWidth) == log2Floor(decodeWidth))

  if (decodeWidth ==2 ) {
    success := bitmask.orR
    if (favor) {
      index := bitmask(1)
    } else {
      index := !bitmask(0)
    }
    // index := Mux1H(Seq(
    //   bitmask(1) -> 1.U(1.W),
    //   bitmask(0) -> 0.U(1.W)
    // )) // Note: not truly parallel for lack of X
  } else {
    val decoder_def = Definition(new ParallelFindOne(decodeWidth / 2, favor))
    val lower_dec = Instance(decoder_def)
    val upper_dec = Instance(decoder_def)

    val lower_success = Wire(Bool())
    val upper_success = Wire(Bool())
    val lower_index = Wire(UInt((log2Ceil(decodeWidth)-1).W))
    val upper_index = Wire(UInt((log2Ceil(decodeWidth)-1).W))
    lower_dec.bitmask := bitmask(decodeWidth/2-1, 0)
    upper_dec.bitmask := bitmask(decodeWidth-1, decodeWidth/2)
    lower_success := lower_dec.success
    upper_success := upper_dec.success
    lower_index := lower_dec.index
    upper_index := upper_dec.index
    success := lower_success | upper_success
    if (favor) {
      index := Mux(upper_success, Cat(1.U(1.W), upper_index), Cat(0.U(1.W), lower_index))
    } else {
      index := Mux(lower_success, Cat(0.U(1.W), lower_index), Cat(1.U(1.W), upper_index))
    }
    // index := Mux1H(Seq(
    //   upper_success ->Cat(1.U(1.W), upper_index),
    //   lower_success -> Cat(0.U(1.W), lower_index)
    // ))
  }
}

object ParallelFindOne {
  def apply(bv: UInt, favor: Boolean) : (Bool, UInt) = {
    val widthRoundUp = scala.math.pow(2, (log2Ceil(bv.getWidth))).intValue
    val pfo = Module(new ParallelFindOne(widthRoundUp, favor))
    pfo.bitmask := bv
//    assert(pfo.index < bv.getWidth.U,"non-pow2 index out of range")
    (pfo.success, pfo.index)
  }
}

class testmodule extends Module {
  val input = IO(Input(UInt(4.W)))
  val success = IO(Output(Bool()))
  val index = IO(Output(UInt(2.W)))

  val (a, b) = ParallelFindOne(input, true)
  success := a
  index := b

//   (success, index)  := 
}

object Elaborate extends App {

  println(ChiselStage.emitSystemVerilog(new testmodule))
//  println(ChiselStage.emitFIRRTLDialect(new ParallelFindOne(4)))
//  println(ChiselStage.emitSystemVerilog(new PrefixedMux))

}
