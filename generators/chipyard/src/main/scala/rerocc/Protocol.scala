package chipyard.rerocc

import chisel3._
import chisel3.util._
import chisel3.internal.sourceinfo.SourceInfo
import freechips.rocketchip.config._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.tile._
import freechips.rocketchip.rocket._
import freechips.rocketchip.util._

object ReRoCCProtocolOpcodes {
  val width = 3
  // beat0: data = mstatus[63:0]
  // beat1: data = mstatus[127:64]
  // beat3: data = ptbr
  val mAcquire = 0.U(width.W)
  // beat0: data = tag # has_mstatus # inst[31:0]
  // beat1: data = mstatus[63:0]
  // beat2: data = mstatus[127:64]
  // beat3: data = op1
  // beat4: data = op2
  val mInst    = 1.U(width.W)
  val mRelease = 2.U(width.W)
  val mUnbusy  = 3.U(width.W)

  // data
  // data = tagEntries # acquired
  val sAcqResp   = 0.U(width.W)
  // data = 0
  val sInstAck   = 1.U(width.W)
  // beat0: data = data
  // beat1: data = rd
  val sWrite     = 2.U(width.W)
  val sRelResp   = 3.U(width.W)
  val sUnbusyAck = 4.U(width.W)

}

case class ReRoCCManagerParams(
  nManagers: Int,
  ibufEntries: Seq[Int])

case class ReRoCCClientParams(
  nClients: Int
)

case class ReRoCCEdgeParams(
  mParams: ReRoCCManagerParams,
  cParams: ReRoCCClientParams
) {
  require(cParams.nClients >= 1 && mParams.nManagers >= 1)
  val bundle = ReRoCCBundleParams(log2Ceil(cParams.nClients), log2Ceil(mParams.nManagers))
}

case class ReRoCCBundleParams(
  clientIdBits: Int,
  managerIdBits: Int
)

class ReRoCCMsgBundle(val params: ReRoCCBundleParams) extends Bundle {
  val opcode     = UInt(ReRoCCProtocolOpcodes.width.W)
  val client_id  = UInt(params.clientIdBits.W)
  val manager_id = UInt(params.managerIdBits.W)
  val data       = UInt(64.W)
  val last       = Bool()
}

class ReRoCCBundle(val params: ReRoCCBundleParams) extends Bundle {
  val req = Decoupled(new ReRoCCMsgBundle(params))
  val resp = Flipped(Decoupled(new ReRoCCMsgBundle(params)))
}


case class EmptyParams()

object ReRoCCImp extends SimpleNodeImp[ReRoCCClientParams, ReRoCCManagerParams, ReRoCCEdgeParams, ReRoCCBundle] {
  def edge(pd: ReRoCCClientParams, pu: ReRoCCManagerParams, p: Parameters, sourceInfo: SourceInfo) = {
    ReRoCCEdgeParams(pu, pd)
  }
  def bundle(e: ReRoCCEdgeParams) = new ReRoCCBundle(e.bundle)

  def render(ei: ReRoCCEdgeParams) = RenderedEdge(colour = "#000000" /* black */)
}

case class ReRoCCClientNode()(implicit valName: ValName) extends SourceNode(ReRoCCImp)(Seq(ReRoCCClientParams(1)))

case class ReRoCCManagerNode(ibufEntries: Int)(implicit valName: ValName) extends SinkNode(ReRoCCImp)(Seq(ReRoCCManagerParams(1, Seq(ibufEntries))))




class ReRoCCBuffer(b: BufferParams = BufferParams.default)(implicit p: Parameters) extends LazyModule {
  val node = new AdapterNode(ReRoCCImp)({s => s}, {s => s})
  lazy val module = new LazyModuleImp(this) {
    (node.in zip node.out) foreach { case ((in, _), (out, _)) =>
      out.req <> b(in.req)
      in.resp <> b(out.resp)
    }
  }
}

object ReRoCCBuffer {
  def apply(b: BufferParams = BufferParams.default)(implicit p: Parameters) = {
    val rerocc_buffer = LazyModule(new ReRoCCBuffer(b)(p))
    rerocc_buffer.node
  }
}


case class ReRoCCIdentityNode()(implicit valName: ValName) extends IdentityNode(ReRoCCImp)()
