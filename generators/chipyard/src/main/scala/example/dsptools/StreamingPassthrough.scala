//// See LICENSE for license details.
//
package chipyard.example

import chisel3._
import chisel3.{Bundle, Module}
import chisel3.util._
import dspblocks._
import dsptools.numbers._
import freechips.rocketchip.amba.axi4stream._
import freechips.rocketchip.config.{Parameters, Field, Config}
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.subsystem._

// Simple passthrough to use as testbed sanity check
// StreamingPassthrough params
case class StreamingPassthroughParams(
  writeAddress: BigInt = 0x2000,
  readAddress: BigInt = 0x2100,
  depth: Int
)

// StreamingPassthrough key
case object StreamingPassthroughKey extends Field[Option[StreamingPassthroughParams]](None)

class StreamingPassthroughBundle[T<:Data:Ring](proto: T) extends Bundle {
    val data: T = proto.cloneType
}
object StreamingPassthroughBundle {
    def apply[T<:Data:Ring](proto: T): StreamingPassthroughBundle[T] = new StreamingPassthroughBundle(proto)
}

class StreamingPassthroughIO[T<:Data:Ring](proto: T) extends Bundle {
    val in = Flipped(Decoupled(StreamingPassthroughBundle(proto)))
    val out = Decoupled(StreamingPassthroughBundle(proto))
}
object StreamingPassthroughIO {
    def apply[T<:Data:Ring](proto: T): StreamingPassthroughIO[T] = new StreamingPassthroughIO(proto)
}

class StreamingPassthrough[T<:Data:Ring](proto: T) extends Module {
    val io = IO(StreamingPassthroughIO(proto))

    io.in.ready := io.out.ready
    io.out.bits.data := io.in.bits.data
    io.out.valid := io.in.valid
}

/**
  * Make DspBlock wrapper for StreamingPassthrough
  * @param cordicParams parameters for cordic
  * @param ev$1
  * @param ev$2
  * @param ev$3
  * @param p
  * @tparam D
  * @tparam U
  * @tparam EO
  * @tparam EI
  * @tparam B
  * @tparam T Type parameter for passthrough, i.e. FixedPoint or DspReal
  */
abstract class StreamingPassthroughBlock[D, U, EO, EI, B<:Data, T<:Data:Ring]
(
  proto: T
)(implicit p: Parameters) extends DspBlock[D, U, EO, EI, B] {
  val streamNode = AXI4StreamIdentityNode()
  val mem = None

  lazy val module = new LazyModuleImp(this) {
    require(streamNode.in.length == 1)
    require(streamNode.out.length == 1)

    val in = streamNode.in.head._1
    val out = streamNode.out.head._1

    // instantiate passthrough
    val passthrough = Module(new StreamingPassthrough(proto))

    // Pass ready and valid from read queue to write queue
    in.ready := passthrough.io.in.ready
    passthrough.io.in.valid := in.valid

    // cast UInt to T
    passthrough.io.in.bits := in.bits.data.asTypeOf(StreamingPassthroughBundle(proto))

    passthrough.io.out.ready := out.ready
    out.valid := passthrough.io.out.valid

    // cast T to UInt
    out.bits.data := passthrough.io.out.bits.asUInt
  }
}

/**
  * TLDspBlock specialization of StreamingPassthrough
  * @param cordicParams parameters for passthrough
  * @param ev$1
  * @param ev$2
  * @param ev$3
  * @param p
  * @tparam T Type parameter for passthrough data type
  */
class TLStreamingPassthroughBlock[T<:Data:Ring]
(
  val proto: T
)(implicit p: Parameters) extends
StreamingPassthroughBlock[TLClientPortParameters, TLManagerPortParameters, TLEdgeOut, TLEdgeIn, TLBundle, T](proto)
with TLDspBlock

/**
  * A chain of queues acting as our MMIOs with the passthrough module in between them.
  * @param depth depth of queues
  * @param ev$1
  * @param ev$2
  * @param ev$3
  * @param p
  * @tparam T Type parameter for passthrough, i.e. FixedPoint or DspReal
  */
class TLStreamingPassthroughChain[T<:Data:Ring](params: StreamingPassthroughParams, proto: T)(implicit p: Parameters)
  extends TLChain(Seq(
    TLWriteQueue(params.depth, AddressSet(params.writeAddress, 0xff))(_),
    { implicit p: Parameters => {
      val streamingPassthrough = LazyModule(new TLStreamingPassthroughBlock(proto))
      streamingPassthrough
    }},
    TLReadQueue(params.depth, AddressSet(params.readAddress, 0xff))(_)
  ))

trait CanHavePeripheryStreamingPassthrough { this: BaseSubsystem =>
  val passthrough = p(StreamingPassthroughKey) match {
    case Some(params) => {
      val streamingPassthroughChain = LazyModule(new TLStreamingPassthroughChain(params, UInt(32.W)))
      pbus.toVariableWidthSlave(Some("streamingPassthrough")) { streamingPassthroughChain.mem.get := TLFIFOFixer() }
      Some(streamingPassthroughChain)
    }
    case None => None
  }
}

/**
 * Mixin to add passthrough to rocket config
 */
class WithStreamingPassthrough extends Config((site, here, up) => {
  case StreamingPassthroughKey => Some(StreamingPassthroughParams(depth = 8))
})

