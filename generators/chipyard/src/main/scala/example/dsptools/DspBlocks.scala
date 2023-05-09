package chipyard.example

import chisel3._
import chisel3.util._
import dspblocks._
import dsptools.numbers._
import freechips.rocketchip.amba.axi4stream._
import org.chipsalliance.cde.config.Parameters
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.regmapper._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.subsystem._

/**
  * The memory interface writes entries into the queue.
  * They stream out the streaming interface
  * @param depth number of entries in the queue
  * @param streamParameters parameters for the stream node
  * @param p
  */
abstract class WriteQueue[D, U, E, O, B <: Data]
(
  val depth: Int,
  val streamParameters: AXI4StreamMasterParameters = AXI4StreamMasterParameters()
)(implicit p: Parameters) extends DspBlock[D, U, E, O, B] with HasCSR {
  // stream node, output only
  val streamNode = AXI4StreamMasterNode(streamParameters)

  lazy val module = new LazyModuleImp(this) {
    require(streamNode.out.length == 1)

    // get the output bundle associated with the AXI4Stream node
    val out = streamNode.out.head._1
    // width (in bits) of the output interface
    val width = out.params.n * 8
    // instantiate a queue
    val queue = Module(new Queue(UInt(out.params.dataBits.W), depth))
    // connect queue output to streaming output
    out.valid := queue.io.deq.valid
    out.bits.data := queue.io.deq.bits
    // don't use last
    out.bits.last := false.B
    queue.io.deq.ready := out.ready

    regmap(
      // each write adds an entry to the queue
      0x0 -> Seq(RegField.w(width, queue.io.enq)),
      // read the number of entries in the queue
      (width+7)/8 -> Seq(RegField.r(width, queue.io.count)),
    )
  }
}

/**
  * TLDspBlock specialization of WriteQueue
  * @param depth number of entries in the queue
  * @param csrAddress address range for peripheral
  * @param beatBytes beatBytes of TL interface
  * @param p
  */
class TLWriteQueue (depth: Int, csrAddress: AddressSet, beatBytes: Int)
(implicit p: Parameters) extends WriteQueue[
  TLClientPortParameters, TLManagerPortParameters, TLEdgeOut, TLEdgeIn, TLBundle
](depth) with TLHasCSR {
  val devname = "tlQueueIn"
  val devcompat = Seq("ucb-art", "dsptools")
  val device = new SimpleDevice(devname, devcompat) {
    override def describe(resources: ResourceBindings): Description = {
      val Description(name, mapping) = super.describe(resources)
      Description(name, mapping)
    }
  }
  // make diplomatic TL node for regmap
  override val mem = Some(TLRegisterNode(address = Seq(csrAddress), device = device, beatBytes = beatBytes))
}

object TLWriteQueue {
  def apply(
    depth: Int = 8,
    csrAddress: AddressSet = AddressSet(0x2000, 0xff),
    beatBytes: Int = 8,
  )(implicit p: Parameters) = {
    val writeQueue = LazyModule(new TLWriteQueue(depth = depth, csrAddress = csrAddress, beatBytes = beatBytes))
    writeQueue
  }
}

/**
  * The streaming interface adds elements into the queue.
  * The memory interface can read elements out of the queue.
  * @param depth number of entries in the queue
  * @param streamParameters parameters for the stream node
  * @param p
  */
abstract class ReadQueue[D, U, E, O, B <: Data]
(
  val depth: Int,
  val streamParameters: AXI4StreamSlaveParameters = AXI4StreamSlaveParameters()
)(implicit p: Parameters) extends DspBlock[D, U, E, O, B] with HasCSR {
  val streamNode = AXI4StreamSlaveNode(streamParameters)

  lazy val module = new LazyModuleImp(this) {
    require(streamNode.in.length == 1)

    // get the input associated with the stream node
    val in = streamNode.in.head._1
    // make a Decoupled[UInt] that RegReadFn can do something with
    val out = Wire(Decoupled(UInt()))
    // get width of streaming input interface
    val width = in.params.n * 8
    // instantiate a queue
    val queue = Module(new Queue(UInt(in.params.dataBits.W), depth))
    // connect input to the streaming interface
    queue.io.enq.valid := in.valid
    queue.io.enq.bits := in.bits.data
    in.ready := queue.io.enq.ready
    // connect output to wire
    out.valid := queue.io.deq.valid
    out.bits := queue.io.deq.bits
    queue.io.deq.ready := out.ready

    regmap(
      // map the output of the queue
      0x0         -> Seq(RegField.r(width, RegReadFn(out))),
      // read the number of elements in the queue
      (width+7)/8 -> Seq(RegField.r(width, queue.io.count)),
    )
  }
}

/**
  * TLDspBlock specialization of ReadQueue
  * @param depth number of entries in the queue
  * @param csrAddress address range
  * @param beatBytes beatBytes of TL interface
  * @param p
  */
class TLReadQueue( depth: Int, csrAddress: AddressSet, beatBytes: Int)
(implicit p: Parameters) extends ReadQueue[
  TLClientPortParameters, TLManagerPortParameters, TLEdgeOut, TLEdgeIn, TLBundle
](depth) with TLHasCSR {
  val devname = "tlQueueOut"
  val devcompat = Seq("ucb-art", "dsptools")
  val device = new SimpleDevice(devname, devcompat) {
    override def describe(resources: ResourceBindings): Description = {
      val Description(name, mapping) = super.describe(resources)
      Description(name, mapping)
    }
  }
  // make diplomatic TL node for regmap
  override val mem = Some(TLRegisterNode(address = Seq(csrAddress), device = device, beatBytes = beatBytes))
}

object TLReadQueue {
  def apply(
    depth: Int = 8,
    csrAddress: AddressSet = AddressSet(0x2100, 0xff),
    beatBytes: Int = 8)(implicit p: Parameters) = {
    val readQueue = LazyModule(new TLReadQueue(depth = depth, csrAddress = csrAddress, beatBytes = beatBytes))
    readQueue
  }
}
