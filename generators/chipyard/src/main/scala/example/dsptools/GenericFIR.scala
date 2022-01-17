//// See LICENSE for license details.
//
package chipyard.example

import chisel3._
import chisel3.experimental.FixedPoint
import chisel3.util._
import dspblocks._
import dsptools.numbers._
import freechips.rocketchip.amba.axi4stream._
import freechips.rocketchip.config.{Parameters, Field, Config}
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.subsystem._

// FIR params
case class GenericFIRParams(
  writeAddress: BigInt = 0x2000,
  readAddress: BigInt = 0x2100,
  depth: Int
)

case object GenericFIRKey extends Field[Option[GenericFIRParams]](None)

class GenericFIRCellBundle[T<:Data:Ring](genIn:T, genOut:T) extends Bundle {
  val data: T = genIn.cloneType
  val carry: T = genOut.cloneType
}
object GenericFIRCellBundle {
  def apply[T<:Data:Ring](genIn:T, genOut:T): GenericFIRCellBundle[T] = new GenericFIRCellBundle(genIn, genOut)
}

class GenericFIRCellIO[T<:Data:Ring](genIn:T, genOut:T) extends Bundle {
  val coeff = Input(genIn.cloneType)
  val in = Flipped(Decoupled(GenericFIRCellBundle(genIn, genOut)))
  val out = Decoupled(GenericFIRCellBundle(genIn, genOut))
}
object GenericFIRCellIO {
  def apply[T<:Data:Ring](genIn:T, genOut:T): GenericFIRCellIO[T] = new GenericFIRCellIO(genIn, genOut)
}

class GenericFIRBundle[T<:Data:Ring](proto: T) extends Bundle {
  val data: T = proto.cloneType
}
object GenericFIRBundle {
  def apply[T<:Data:Ring](proto: T): GenericFIRBundle[T] = new GenericFIRBundle(proto)
}

class GenericFIRIO[T<:Data:Ring](genIn:T, genOut:T) extends Bundle {
  val in = Flipped(Decoupled(GenericFIRBundle(genIn)))
  val out = Decoupled(GenericFIRBundle(genOut))
}
object GenericFIRIO {
  def apply[T<:Data:Ring](genIn:T, genOut:T): GenericFIRIO[T] = new GenericFIRIO(genIn, genOut)
}

// A generic FIR filter
// DOC include start: GenericFIR chisel
class GenericFIR[T<:Data:Ring](genIn:T, genOut:T, coeffs: Seq[T]) extends Module {
  val io = IO(GenericFIRIO(genIn, genOut))

  // Construct a vector of genericFIRDirectCells
  val directCells = Seq.fill(coeffs.length){ Module(new GenericFIRDirectCell(genIn, genOut)).io }

  // Construct the direct FIR chain
  for ((cell, coeff) <- directCells.zip(coeffs)) {
    cell.coeff := coeff
  }

  // Connect input to first cell
  directCells.head.in.bits.data := io.in.bits.data
  directCells.head.in.bits.carry := Ring[T].zero
  directCells.head.in.valid := io.in.valid
  io.in.ready := directCells.head.in.ready

  // Connect adjacent cells
  // Note that .tail() returns a collection that consists of all
  // elements in the inital collection minus the first one.
  // This means that we zip together directCells[0, n] and
  // directCells[1, n]. However, since zip ignores unmatched elements,
  // the resulting zip is (directCells[0], directCells[1]) ...
  // (directCells[n-1], directCells[n])
  for ((current, next) <- directCells.zip(directCells.tail)) {
    next.in.bits := current.out.bits
    next.in.valid := current.out.valid
    current.out.ready := next.in.ready
  }

  // Connect output to last cell
  io.out.bits.data := directCells.last.out.bits.carry
  directCells.last.out.ready := io.out.ready
  io.out.valid := directCells.last.out.valid

}
// DOC include end: GenericFIR chisel

// A generic FIR direct cell used to construct a larger direct FIR chain
//
//   in ----- [z^-1]-- out
//	        |
//   coeff ----[*]
//	        |
//   carryIn --[+]-- carryOut
//
// DOC include start: GenericFIRDirectCell chisel
class GenericFIRDirectCell[T<:Data:Ring](genIn: T, genOut: T) extends Module {
  val io = IO(GenericFIRCellIO(genIn, genOut))

  // Registers to delay the input and the valid to propagate with calculations
  val hasNewData = RegInit(0.U)
  val inputReg = Reg(genIn.cloneType)

  // Passthrough ready
  io.in.ready := io.out.ready

  // When a new transaction is ready on the input, we will have new data to output
  // next cycle. Take this data in
  when (io.in.fire) {
    hasNewData := 1.U
    inputReg := io.in.bits.data
  }

  // We should output data when our cell has new data to output and is ready to
  // recieve new data. This insures that every cell in the chain passes its data
  // on at the same time
  io.out.valid := hasNewData & io.in.fire
  io.out.bits.data := inputReg

  // Compute carry
  // This uses the ring implementation for + and *, i.e.
  // (a * b) maps to (Ring[T].prod(a, b)) for whicever T you use
  io.out.bits.carry := inputReg * io.coeff + io.in.bits.carry
}
// DOC include end: GenericFIRDirectCell chisel


// DOC include start: GenericFIRBlock chisel
abstract class GenericFIRBlock[D, U, EO, EI, B<:Data, T<:Data:Ring]
(
  genIn: T,
  genOut: T,
  coeffs: Seq[T]
)(implicit p: Parameters) extends DspBlock[D, U, EO, EI, B] {
  val streamNode = AXI4StreamIdentityNode()
  val mem = None

  lazy val module = new LazyModuleImp(this) {
    require(streamNode.in.length == 1)
    require(streamNode.out.length == 1)

    val in = streamNode.in.head._1
    val out = streamNode.out.head._1

    // instantiate generic fir
    val fir = Module(new GenericFIR(genIn, genOut, coeffs))

    // Attach ready and valid to outside interface
    in.ready := fir.io.in.ready
    fir.io.in.valid := in.valid

    fir.io.out.ready := out.ready
    out.valid := fir.io.out.valid

    // cast UInt to T
    fir.io.in.bits := in.bits.data.asTypeOf(GenericFIRBundle(genIn))

    // cast T to UInt
    out.bits.data := fir.io.out.bits.asUInt
  }
}
// DOC include end: GenericFIRBlock chisel

// DOC include start: TLGenericFIRBlock chisel
class TLGenericFIRBlock[T<:Data:Ring]
(
  val genIn: T,
  val genOut: T,
  coeffs: Seq[T]
)(implicit p: Parameters) extends
GenericFIRBlock[TLClientPortParameters, TLManagerPortParameters, TLEdgeOut, TLEdgeIn, TLBundle, T](
    genIn, genOut, coeffs
) with TLDspBlock
// DOC include end: TLGenericFIRBlock chisel

// DOC include start: TLGenericFIRChain chisel
class TLGenericFIRChain[T<:Data:Ring] (genIn: T, genOut: T, coeffs: Seq[T], params: GenericFIRParams)(implicit p: Parameters)
  extends TLChain(Seq(
    TLWriteQueue(params.depth, AddressSet(params.writeAddress, 0xff))(_),
    { implicit p: Parameters =>
      val fir = LazyModule(new TLGenericFIRBlock(genIn, genOut, coeffs))
      fir
    },
    TLReadQueue(params.depth, AddressSet(params.readAddress, 0xff))(_)
  ))
// DOC include end: TLGenericFIRChain chisel

// DOC include start: CanHavePeripheryStreamingFIR chisel
trait CanHavePeripheryStreamingFIR extends BaseSubsystem {
  val streamingFIR = p(GenericFIRKey) match {
    case Some(params) => {
      val streamingFIR = LazyModule(new TLGenericFIRChain(
        genIn = FixedPoint(8.W, 3.BP),
        genOut = FixedPoint(8.W, 3.BP),
        coeffs = Seq(1.F(0.BP), 2.F(0.BP), 3.F(0.BP)),
        params = params))
      pbus.toVariableWidthSlave(Some("streamingFIR")) { streamingFIR.mem.get := TLFIFOFixer() }
      Some(streamingFIR)
    }
    case None => None
  }
}
// DOC include end: CanHavePeripheryStreamingFIR chisel

/**
 * Mixin to add FIR to rocket config
 */
// DOC include start: WithStreamingFIR
class WithStreamingFIR extends Config((site, here, up) => {
  case GenericFIRKey => Some(GenericFIRParams(depth = 8))
})
// DOC include end: WithStreamingFIR
