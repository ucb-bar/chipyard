//see LICENSE for license
//authors: Colin Schmidt, Adam Izraelevitz
package sha3

import Chisel._
import chisel3.iotesters.PeekPokeTester
import scala.collection.mutable.HashMap
import scala.collection.mutable.ArrayBuffer
import scala.util.Random

class ChiModule(val W: Int = 64) extends Module {
  //val W = 64
  val io = new Bundle { 
    val state_i = Vec(5*5, Bits(INPUT,W))
    val state_o = Vec(5*5, Bits(OUTPUT,W))
  }

  //TODO: c code uses falttened rep for this
  for(i <- 0 until 5) {
    for(j <- 0 until 5) {
      io.state_o(i*5+j) := io.state_i(i*5+j) ^ 
      ( (~io.state_i(((i+1)%5)*5+((j)%5))) & io.state_i(((i+2)%5)*5+((j)%5)))
    }
  }
}

class ChiModuleTests(c: ChiModule) extends PeekPokeTester(c) {
    var allGood = true
    val rand     = new Random(1)
    val W       = 4
    val maxInt  = 1 << (5*5*W)
    for (i <- 0 until 1) {
      val state = Array.fill(5*5){BigInt(rand.nextInt(1 <<W))}
      val out_state = Array.fill(5*5){BigInt(0)}
      for(i <- 0 until 5) {
        for(j <- 0 until 5) {
          out_state(i*5+j) = state(i*5+j) ^
               ( ~state(i*5+((j+1)%5)) & state(i*5+((j+2)%5)))
        }
      }
      poke(c.io.state_i, state)
      step(1)
      expect(c.io.state_o, out_state)
  }
}
/*
object chiMain { 
  def main(args: Array[String]): Unit = {
    //chiselMainTest(Array[String]("--backend", "c", "--genHarness", "--compile", "--test"),
    chiselMainTest(args,
      () => Module(new ChiModule())){c => new ChiModuleTests(c)
    }
  }
}
*/
