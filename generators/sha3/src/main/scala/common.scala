//see LICENSE for license
//authors: Colin Schmidt, Adam Izraelevitz
package sha3

import Chisel._
import scala.util.Random

class SHA3_State extends Bundle {
  val words = Vec.fill(5){ Vec.fill(5) { Bits(width = 64) }}
}

object common {
  val rand     = new Random(2013)
  //def generate_random_bigint() = BigInt(rand.nextLong()) + (BigInt(1) << 63)
  def generate_random_bigint(size: Int) : BigInt = BigInt(rand.nextLong()) + (BigInt(1) << (size-1))
  def generate_random_int(size: Int) = math.abs(rand.nextInt()) % (1 << size) //128
  def generate_test_matrix(size: Int) = Array.fill(5*5)(generate_random_int(size))
  def print_matrix(testmatrix: Array[Int]) = {
    (0 until 5).foreach(row => println((0 until 5).toArray.map(col => "%016x".format(testmatrix(row*5+col))).reduce(_+" "+_)))
  }
  def print_bigmatrix(testmatrix: Array[BigInt]) = {
    (0 until 5).foreach(row => println((0 until 5).toArray.map(col => "%d".format(testmatrix(row*5+col))).reduce(_+" "+_)))
  }
  def ROTL(x: UInt, y: UInt, W: UInt) = (((x) << (y)) | ((x) >> (W - (y))))
  //def ROTLscala(x: UInt, y: Int, W: Int) = (((x) << (y)) | ((x) >> (W - (y))))
}
//use with 
//val state = new SHA3_State()
//state.words(i)(j)(k) ? not sure if this line works
