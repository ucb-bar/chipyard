package customAccRoCC

import chisel3._
import chisel3.util._
import chiseltest._
import org.chipsalliance.cde.config._
import freechips.rocketchip.tile._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.rocket._
import org.scalatest.flatspec.AnyFlatSpec

class vectorAddTest extends AnyFlatSpec with ChiselScalatestTester {

  behavior of "Vector-Add RoCC Accelerator"

  it should "add two vectors" in {
    test(new vectorAdd()(Parameters.empty)).withAnnotations(Seq(VerilatorBackendAnnotation, WriteFstAnnotation)) { c =>

      /* expect ready */
      c.io.cmd.ready.expect(true.B) 
      
      /* send input */
      c.io.cmd.bits.rs1.poke("h_07_06_05_04_03_02_01_00".U)
      c.io.cmd.bits.rs2.poke("h_08_07_06_05_04_03_02_01".U)
      c.io.cmd.valid.poke(/* YOUR CODE HERE */)

			/* step the clock by 1 cycle */
      /* YOUR CODE HERE */

      /* expect valid */
      c.io.resp.valid.expect(true.B)
      c.io.resp.ready.poke(true.B)
      c.io.resp.bits.data.expect(/* YOUR CODE HERE */)
      
			println("Observed output value :" + c.io.resp.bits.data.peek().litValue)
      
      c.clock.step(1)

    }
  }
}
