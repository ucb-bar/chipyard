//see LICENSE for license
//authors: Colin Schmidt, Adam Izraelevitz
package sha3

import Chisel._
import chisel3.iotesters.PeekPokeTester
import scala.collection.mutable.HashMap
import scala.collection.mutable.ArrayBuffer


class ThetaModule(val W: Int = 64) extends Module {
  //val W = 64
  //val W = RangeParam(64, 8, 64, 8).register(this, "W")
  val io = new Bundle {
    val state_i = Vec(5*5, Bits(INPUT, width = W))
    val state_o = Vec(5*5, Bits(OUTPUT,width = W))
  }

  val bc = Vec.fill(5){Wire(Bits(width = W))}
  for(i <- 0 until 5) {
    bc(i) := io.state_i(i*5+0) ^ io.state_i(i*5+1) ^ io.state_i(i*5+2) ^ io.state_i(i*5+3) ^ io.state_i(i*5+4)
  }

  for(i <- 0 until 5) {
    val t = Wire(Bits(width = W))
    t := bc((i+4)%5) ^ common.ROTL(bc((i+1)%5), UInt(1), UInt(W))
    for(j <- 0 until 5) {
      io.state_o(i*5+j) := io.state_i(i*5+j) ^ t
    }
  }
}

class Parity extends Module {
  val io = new Bundle {
    val in = Vec.fill(5){Bool(INPUT)}
    val res = Bool(OUTPUT)
  }
  io.res := io.in(0) ^ io.in(1) ^ io.in(2) ^ io.in(3) ^ io.in(4)
}
/*
class ThetaModuleTests(c: ThetaModule) extends Tester(c, Array(c.io)) {
  defTests {
    var allGood = true
    val vars    = new HashMap[Node, Node]()
    val W       = 4
    for (i <- 0 until 1) {
      val state = Vec.fill(5*5){Bits(width = W)}
      val out_state = Vec.fill(5*5){Bits(width = W)}
      val matrix = common.generate_test_matrix(W)
      var out_matrix = ArrayBuffer.empty[BigInt]
      
      for (i <- 0 until 5) {
        for (j <- 0 until 5) {
          val word = matrix(i*5+j)
          state(i*5+j) = Bits(word,width=W)
          vars(c.io.state_i(i*5+j)) = state(i*5+j)
        }
      }

      val bc = Vec.fill(5){Bits(width = W)}
      for(i <- 0 until 5) {
        bc(i) := state(0*5+i) ^ state(1*5+i) ^ state(2*5+i) ^ state(3*5+i) ^ state(4*5+i)
      }

      for(i <- 0 until 5) {
        val t = Bits(width = W)
        t := bc((i+4)%5) ^ common.ROTL(bc((i+1)%5), UInt(1), UInt(W))
        for(j <- 0 until 5) {
          out_state(i*5+j) := state(i*5+j) ^ t
          vars(c.io.state_o(i*5+j)) = out_state(i*5+j)
        }
      }
      allGood = step(vars) && allGood
      common.print_matrix(matrix)
      //common.print_bigmatrix(out_matrix.toArray)
    }
    printf("Test passed: " + allGood + "\n")
    allGood
  }
}
*/
/*
object thetaMain { 
  def main(args: Array[String]): Unit = {
    val res =
    args(0) match {
      // Generate default design and dump parameter space
      case "THETA_dump" => {
        chiselMain(args.slice(2,args.length), () => Module(new ThetaModule()))
        Params.dump(args(1))
      }
      // Generate design based on design point input
      case "THETA" => {
        Params.load(args(1))
        chiselMain(args.slice(2,args.length), () => Module(new ThetaModule()))
      }
      case "THETA_test" => {
        Params.load(args(1))
        chiselMainTest(args.slice(1,args.length), () => Module(new ThetaModule())) {c => new ThetaModuleTests(c) }
      }
      case "THETA_NP_test" => {
        chiselMainTest(args.slice(1,args.length), () => Module(new ThetaModule())) {c => new ThetaModuleTests(c) }
      }
      case _ => {
        printf("Bad arg(0)\n")
      }
    }
  }
}

*/
