//see LICENSE for license
//authors: Colin Schmidt, Adam Izraelevitz
package sha3

import Chisel._
import scala.collection.mutable.HashMap
import scala.collection.mutable.ArrayBuffer
import scala.util.Random
import chisel3.iotesters.PeekPokeTester

class IotaModule(val W: Int = 64) extends Module {
  val io = new Bundle { 
    val state_i = Vec(5*5, Bits(INPUT,W))
    val state_o = Vec(5*5, Bits(OUTPUT,W))
    val round = UInt(INPUT, 5)
  }

  //TODO: c code uses look up table for this
  for(i <- 0 until 5) {
    for(j <- 0 until 5) {
      if(i !=0 || j!=0)
        io.state_o(i*5+j) := io.state_i(i*5+j)
    }
  }
  //val const = ROUND_CONST.value(io.round)
  val const = IOTA.round_const(io.round)
  debug(const)
  io.state_o(0) := io.state_i(0) ^ const
/*
  io.state_o(0) := Cat(io.state_i(0)(63) ^ const(6),
                       io.state_i(0)(62,32),
                       io.state_i(0)(31) ^ const(5),
                       io.state_i(0)(30,16),
                       io.state_i(0)(15) ^ const(4),
                       io.state_i(0)(14,8),
                       io.state_i(0)( 7) ^ const(3),
                       io.state_i(0)(6,4),
                       io.state_i(0)( 3) ^ const(2),
                       io.state_i(0)( 2),
                       io.state_i(0)( 1) ^ const(1),
                       io.state_i(0)( 0) ^ const(0))
*/
}

class IotaModuleTests(c: IotaModule) extends PeekPokeTester(c) {
    val W       = 64
    val maxInt  = 1 << (5*5*W)
      //val state_i = rnd.nextInt(maxInt)
      val round = 0
      val state = Array.fill(5*5){BigInt(3)}
      val out_state = Array.fill(5*5){BigInt(3)}
      out_state(0) = state(0) ^ BigInt(1)
      poke(c.io.state_i, state)
      poke(c.io.round, round)
      step(1)
      expect(c.io.state_o, out_state)
}
/*
object iotaMain { 
  def main(args: Array[String]): Unit = {
    //chiselMainTest(Array[String]("--backend", "c", "--genHarness", "--compile", "--test"),
    chiselMainTest(args,
    () => Module(new IotaModule())){c => new IotaModuleTests(c)
    }
  }
}
*/
