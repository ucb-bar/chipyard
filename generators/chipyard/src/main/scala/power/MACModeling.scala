package power
import chisel3._
import chisel3.util.Counter
import chisel3.util.random.LFSR
import gemmini.Arithmetic

class MACConfig[T <: Data](val aType: T, val bType: T, val cType: T)
// In OS mode: a and b are 8 bits and c is 32 bits of the internal accumulator
// In WS mode: a and b are also 8 bits, but c is 19 bits of the PE from above
case class OSMACConfig() extends MACConfig(SInt(8.W), SInt(8.W), SInt(32.W))
case class WSMACConfig() extends MACConfig(SInt(8.W), SInt(8.W), SInt(19.W))

case class MACIO[T <: Data](config: MACConfig[T]) extends Bundle {
  val a = Input(config.aType)
  val b = Input(config.bType)
  val c = Input(config.cType)
  val out = Output(config.cType)
}

// Input data registering is modeled here (pipeline registers on a and b from the PE above and to the left, and
// register on c which models the internal register of the PE)
class MAC[T <: Data](config: MACConfig[T])(implicit ev: Arithmetic[T]) extends Module {
  import ev._
  val io = IO(MACIO(config))
  val aReg = RegNext(io.a)
  val bReg = RegNext(io.b)
  val cReg = RegNext(io.c)
  val outReg = RegNext(cReg.mac(aReg, bReg))
  io.out := outReg
}

// Maybe we need to explicitly split the test driver and the RTL for power evaluation ...
// An bunch of identical MACs to be driven by variable input densities
/*
class MACArray[T <: Data](config: MACConfig[T], numMACs: Int) extends Module {
  val io = IO(new Bundle {
    val macIOs = Vec(numMACs, MACIO(config))
  })

}
 */

class MACDriver[T <: Data:Arithmetic](config: MACConfig[T], numMacs: Int) extends RawModule {
  // TODO: how do you create a module with no IOs except clock/reset?
  val clock = IO(Input(Clock()))
  val reset = IO(Input(Bool()))

  val random = new scala.util.Random
  def randomInRange(low: Int, high: Int): Int = {
    low + random.nextInt( (high - low) + 1 )
  }

  withClockAndReset(clock, reset) {
    // Assuming WS dataflow MAC patterns
    val (cntval, cntwrap) = Counter(true.B, 16) // only change B every 16 cycles
    val aVals = LFSR(config.aType.getWidth, seed=Some(randomInRange(1, (2^config.aType.getWidth) - 1)))
    val bVals = LFSR(config.bType.getWidth, increment=cntwrap, seed=Some(randomInRange(1, (2^config.bType.getWidth) - 1)))
    val cVals = LFSR(config.cType.getWidth, seed=Some(randomInRange(1, (2^config.cType.getWidth) - 1)))
    val sparsitylfsr = LFSR(8)
    val macs = Seq.fill(numMacs)(Module(new MAC(config)))
    macs.zipWithIndex.foreach { case (mac, idx) =>
      val sparsity: Float = (idx + 1) / numMacs
      mac.io.a := Mux(sparsitylfsr < (sparsity/(2^8)).toInt.U, 0.U, aVals).asTypeOf(config.aType)
      mac.io.b := bVals.asTypeOf(config.bType)
      mac.io.c := cVals.asTypeOf(config.cType)
      dontTouch(mac.io.a)
      dontTouch(mac.io.b)
      dontTouch(mac.io.c)
      dontTouch(mac.io.out)
    }
  }
}


object MACModeling extends App {
  val vlog = chisel3.Driver.execute(args, () => new MACDriver(WSMACConfig(), 10))
}
