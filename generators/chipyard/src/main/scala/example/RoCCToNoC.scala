package chipyard.example

import chisel3._
import chisel3.util._
import freechips.rocketchip.config._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.rocket._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.tile._
import freechips.rocketchip.subsystem._
import constellation.protocol.{ProtocolParams, ProtocolNoC, ProtocolNoCParams}
import constellation.noc.{NoCTerminalIO, NoCParams}
import constellation.channel.{FlowParams}
import scala.collection.immutable.{ListMap}

import boom.common.{BoomTile}

/*
 * This class instantiates a simple RoCC example to demonstrate
 * how a RoCC accelerator can utilize a custom Constellation-generated
 * NoC.
 *
 * The example RoCCOverNoC (RoN) "accelerator" implements a register which can be
 * written by other RoN accelerators
 */

class RoN(opcode: OpcodeSet = OpcodeSet.custom0)(implicit p: Parameters) extends LazyRoCC(opcode) {
  // use BundleBridge to punch through the RoN bundle to the subsystem, where the
  // RoN noc lives.
  // Note: this implies that the tiles are on the same clock domain as the subsystem

  val ronIONode = BundleBridgeSource(() => new RoNIO)

  override lazy val module = new Impl
  class Impl extends LazyRoCCModuleImp(this) {
    val reg = Reg(UInt(64.W))

    // Send messages from this hart to the NoC
    val cmd = Queue(io.cmd)

    val cmd_write = cmd.bits.inst.funct === 0.U
    val cmd_read = !cmd_write
    // cmd_target is the target hartId of the RoN to access
    // if the targeted hartId does not have a RoN unit, then the
    // instruction will no-op
    val cmd_target = cmd.bits.rs2

    val ron_out = ronIONode.bundle.out

    ron_out.valid              := cmd_write && cmd.valid && io.resp.ready
    ron_out.bits.sink_hartid   := cmd_target
    ron_out.bits.wdata         := cmd.bits.rs1

    cmd.ready := ron_out.ready && io.resp.ready
    io.resp.valid := cmd.valid && ron_out.ready
    io.resp.bits.data := reg
    io.resp.bits.rd := cmd.bits.inst.rd

    val ron_in = ronIONode.bundle.in
    ron_in.ready := true.B
    when (ron_in.fire()) { reg := ron_in.bits.wdata }
  }
}

class RoNIO(implicit p: Parameters) extends Bundle {
  // out is from a RoN to the NoC
  val out = Decoupled(new RoNRequest)
  // in is incoming from the NoC
  val in = Flipped(Decoupled(new RoNRequest))
}

class RoNRequest(implicit p: Parameters) extends Bundle {
  val sink_hartid = UInt(p(MaxHartIdBits).W)
  val wdata = UInt(64.W)
}

class RoNInterconnectInterface(harts: Seq[Int])(implicit p: Parameters) extends Bundle {
  val from_source = Vec(harts.size, Flipped(Decoupled(new RoNRequest)))
  val to_sink = Vec(harts.size, Decoupled(new RoNRequest))

  def connect(hartId: Int, source: DecoupledIO[RoNRequest], sink: DecoupledIO[RoNRequest]) = {
    require(harts.contains(hartId))
    val index = harts.indexOf(hartId)
    from_source(index) <> source
    sink <> to_sink(index)
  }
}

// The RoNProtocolParams class tells constellation how to generate a
// NoC for the RoN protocol.
// It includes information for mapping the RoN sinks and sources to nodes
// in the network, as well as converters to map between RoNBundles and
// Constellation packets
case class RoNNoCProtocolParams(
  hartMappings: ListMap[Int, Int] = ListMap(), // maps hartId to a nodeId
  nocParams: NoCParams = NoCParams()
) extends ProtocolParams {
  val nHarts = hartMappings.size
  // For demonstration, RoN splits 64-bit messages into 2-flit packets
  val minPayloadWidth: Int = 32
  val ingressNodes = hartMappings.values.toSeq
  val egressNodes = hartMappings.values.toSeq
  val nVirtualNetworks = 1
  val vNetBlocking = (_: Int, _: Int) => false
  // For this example, all-to-all messaging is supported, so support all flows for any i,j
  val flows = Seq.tabulate(nHarts, nHarts) { (i, j) => FlowParams(i, j, 0) }.flatten
  def genIO()(implicit p: Parameters): Data = new RoNInterconnectInterface(hartMappings.keys.toSeq)
  def interface(terminals: NoCTerminalIO, ingressOffset: Int, egressOffset: Int,
    protocol: Data)(implicit p: Parameters) = {
    require(ingressOffset == 0 && egressOffset == 0)

    val ingresses = terminals.ingress
    val egresses = terminals.egress
    val ron_io = protocol.asInstanceOf[RoNInterconnectInterface]

    // Generate interface from sources into the NoC
    for ((hartId, index) <- hartMappings.keys.zipWithIndex) {
      val beat = RegInit(0.U(1.W))
      val ingress = ingresses(index)
      val source = Queue(ron_io.from_source(index), 2)
      ingress.flit.valid          := source.valid
      ingress.flit.bits.payload   := source.bits.wdata >> (beat << 5)
      ingress.flit.bits.head      := beat === 0.U
      ingress.flit.bits.tail      := beat === 1.U
      ingress.flit.bits.egress_id := source.bits.sink_hartid
      source.ready                := ingress.flit.ready && beat === 1.U
      when (ingress.flit.fire()) { beat := Mux(beat === 1.U, 0.U, beat + 1.U) }
    }

    // Generate egresses from the NoC into sinks
    for ((hartId, index) <- hartMappings.keys.zipWithIndex) {
      val beat = RegInit(0.U(1.W))
      val egress = egresses(index)
      val sink = ron_io.to_sink(index)
      val data = Reg(UInt(32.W))
      sink.valid            := egress.flit.valid && beat === 1.U
      sink.bits.sink_hartid := hartId.U
      sink.bits.wdata       := Cat(egress.flit.bits.payload(31, 0), data)
      egress.flit.ready     := beat === 0.U || sink.ready
      when (egress.flit.fire()) {
        data := egress.flit.bits.payload(31, 0)
        beat := Mux(beat === 1.U, 0.U, beat + 1.U)
      }
    }
  }
}

case object RoNNoCKey extends Field[RoNNoCProtocolParams]()

trait CanHaveRoNBlocks { this: HasTiles =>
  // grab a list of all the RoN accelerators
  val rons = tiles.map { t => t match {
    case r: RocketTile => r.roccs collect { case r: RoN => t.hartId -> r }
    case b: BoomTile => b.roccs collect { case r: RoN => t.hartId -> r }
    case _ => Nil
  }}.flatten.toMap

  val ronIONodes = rons.mapValues { r =>
    val ronIONode = BundleBridgeSink[RoNIO]()
    ronIONode := r.ronIONode
    ronIONode
  }.toMap

  if (rons.size > 0) { InModuleBody {
    val ron_noc = Module(new ProtocolNoC(ProtocolNoCParams(
      p(RoNNoCKey).nocParams.copy(nocName="ron_noc"),
      Seq(p(RoNNoCKey))
    )))
    val noc_io = ron_noc.io.protocol(0).asInstanceOf[RoNInterconnectInterface]
    for ((hartId, r) <- ronIONodes) {
      noc_io.connect(hartId, r.bundle.out, r.bundle.in)
    }
  }}
}

// Config fragment to add a RoN block to all cores which reference BuildRoCC
class WithRoCCToNoC(op: OpcodeSet = OpcodeSet.custom0) extends Config((site, here, up) => {
  case BuildRoCC => up(BuildRoCC) ++ Seq((p: Parameters) => {
    val ron = LazyModule(new RoN(op)(p))
    ron
  })
})

// Config fragment to configure the RoN NoC
class WithRoNNoC(params: RoNNoCProtocolParams) extends Config((site, here, up) => {
  case RoNNoCKey => params
})
