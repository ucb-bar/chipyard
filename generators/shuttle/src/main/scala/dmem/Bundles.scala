// See LICENSE.Berkeley for license details.

package shuttle.dmem

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config.Parameters
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.util._
import freechips.rocketchip.tile._
import freechips.rocketchip.rocket._

class ShuttleDMemReq(implicit p: Parameters) extends CoreBundle()(p) with HasL1HellaCacheParameters {
  val addr = UInt(coreMaxAddrBits.W)
  val tag  = UInt((coreParams.dcacheReqTagBits + log2Ceil(dcacheArbPorts)).W)
  val cmd  = UInt(M_SZ.W)
  val size = UInt(log2Ceil(coreDataBytes.log2 + 1).W)
  val signed = Bool()
  val data = UInt(coreDataBits.W)
  val mask = UInt(coreDataBytes.W)
}

class ShuttleDMemResp(implicit p: Parameters) extends CoreBundle()(p) with HasL1HellaCacheParameters{
  val has_data = Bool()
  val tag  = UInt((coreParams.dcacheReqTagBits + log2Ceil(dcacheArbPorts)).W)
  val data = UInt(coreDataBits.W)
  val size = UInt(log2Ceil(coreDataBytes.log2 + 1).W)
}

class ShuttleMSHRReq(implicit p: Parameters) extends ShuttleDMemReq {
  val tag_match = Bool()
  val old_meta = new L1Metadata
  val way_en = Bits(nWays.W)
  val sdq_id = UInt(log2Up(cfg.nSDQ).W)
}
