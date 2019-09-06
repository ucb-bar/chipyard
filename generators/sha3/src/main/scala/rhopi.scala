//see LICENSE for license
//authors: Colin Schmidt, Adam Izraelevitz
package sha3

import Chisel._
import chisel3.iotesters.PeekPokeTester
import scala.collection.mutable.HashMap
import scala.collection.mutable.ArrayBuffer
import scala.util.Random

class RhoPiModule(val W : Int = 64) extends Module {
  //val W = 64
  val io = new Bundle { 
    val state_i = Vec(25, Bits(INPUT, W))
    val state_o = Vec(25, Bits(OUTPUT,W))
  }

  //TODO: c code uses falttened rep for this
/*
  val piln = Array(
     0, 18, 21, 19, 14,
    10, 3,  24, 13,  22, 
    7, 5,  4, 12, 9, 
    11, 16, 15,  2, 6, 
    17, 8, 23,  20,  1 
  )

  val tri = Array(
      0, 36,  3, 41, 18,
      1, 44, 10, 45,  2,
     62,  6, 43, 15, 61,
     28, 55, 25, 21, 56,
     27, 20, 39,  8, 14
  )
*/

  for(i <- 0 until 5) {
    for(j <- 0 until 5) {
      val temp = Wire(Bits())
      if((RHOPI.tri(i*5+j)%W) == 0){
        temp := io.state_i(i*5+j)
      }else{
        temp := Cat(io.state_i(i*5+j)((W-1) - (RHOPI.tri(i*5+j)-1)%W,0),io.state_i(i*5+j)(W-1,W-1 - ((RHOPI.tri(i*5+j)-1)%W)))
      }
      io.state_o(j*5+((2*i+3*j)%5)) := temp
    }
  }
}
