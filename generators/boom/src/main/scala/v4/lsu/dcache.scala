//******************************************************************************
// Ported from Rocket-Chip
// See LICENSE.Berkeley and LICENSE.SiFive in Rocket-Chip for license details.
//------------------------------------------------------------------------------
//------------------------------------------------------------------------------

package boom.v4.lsu

import chisel3._
import chisel3.util._

import org.chipsalliance.cde.config.Parameters
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.tile._
import freechips.rocketchip.util._
import freechips.rocketchip.rocket._

import boom.v4.common._
import boom.v4.exu.BrUpdateInfo
import boom.v4.util._


class BoomWritebackUnit(implicit edge: TLEdgeOut, p: Parameters) extends L1HellaCacheModule()(p) {
  val io = IO(new Bundle {
    val req = Flipped(Decoupled(new WritebackReq(edge.bundle)))
    val meta_read = Decoupled(new L1MetaReadReq)
    val resp = Output(Bool())
    val idx = Output(Valid(UInt()))
    val data_req = Decoupled(new L1DataReadReq)
    val data_resp = Input(UInt(encRowBits.W))
    val mem_grant = Input(Bool())
    val release = Decoupled(new TLBundleC(edge.bundle))
    val lsu_release = Decoupled(new TLBundleC(edge.bundle))
  })

  val req = Reg(new WritebackReq(edge.bundle))
  val s_invalid :: s_fill_buffer :: s_lsu_release :: s_active :: s_grant :: Nil = Enum(5)
  val state = RegInit(s_invalid)
  val r1_data_req_fired = RegInit(false.B)
  val r2_data_req_fired = RegInit(false.B)
  val r1_data_req_cnt = Reg(UInt(log2Up(refillCycles+1).W))
  val r2_data_req_cnt = Reg(UInt(log2Up(refillCycles+1).W))
  val data_req_cnt = RegInit(0.U(log2Up(refillCycles+1).W))
  val (_, last_beat, all_beats_done, beat_count) = edge.count(io.release)
  val wb_buffer = Reg(Vec(refillCycles, UInt(encRowBits.W)))
  val acked = RegInit(false.B)

  io.idx.valid       := state =/= s_invalid
  io.idx.bits        := req.idx
  io.release.valid   := false.B
  io.release.bits    := DontCare
  io.req.ready       := false.B
  io.meta_read.valid := false.B
  io.meta_read.bits  := DontCare
  io.data_req.valid  := false.B
  io.data_req.bits   := DontCare
  io.resp            := false.B
  io.lsu_release.valid := false.B
  io.lsu_release.bits := DontCare


  val r_address = Cat(req.tag, req.idx) << blockOffBits
  val id = cfg.nMSHRs
  val probeResponse = edge.ProbeAck(
                          fromSource = req.source,
                          toAddress = r_address,
                          lgSize = lgCacheBlockBytes.U,
                          reportPermissions = req.param,
                          data = wb_buffer(data_req_cnt))

  val voluntaryRelease = edge.Release(
                          fromSource = id.U,
                          toAddress = r_address,
                          lgSize = lgCacheBlockBytes.U,
                          shrinkPermissions = req.param,
                          data = wb_buffer(data_req_cnt))._2


  when (state === s_invalid) {
    io.req.ready := true.B
    when (io.req.fire) {
      state := s_fill_buffer
      data_req_cnt := 0.U
      req := io.req.bits
      acked := false.B
    }
  } .elsewhen (state === s_fill_buffer) {
    io.meta_read.valid := data_req_cnt < refillCycles.U
    io.meta_read.bits.idx := req.idx
    io.meta_read.bits.tag := req.tag

    io.data_req.valid := data_req_cnt < refillCycles.U
    io.data_req.bits.way_en := req.way_en
    io.data_req.bits.addr := (if(refillCycles > 1)
                              Cat(req.idx, data_req_cnt(log2Up(refillCycles)-1,0))
                            else req.idx) << rowOffBits

    r1_data_req_fired := false.B
    r1_data_req_cnt   := 0.U
    r2_data_req_fired := r1_data_req_fired
    r2_data_req_cnt   := r1_data_req_cnt
    when (io.data_req.fire && io.meta_read.fire) {
      r1_data_req_fired := true.B
      r1_data_req_cnt   := data_req_cnt
      data_req_cnt := data_req_cnt + 1.U
    }
    when (r2_data_req_fired) {
      wb_buffer(r2_data_req_cnt) := io.data_resp
      when (r2_data_req_cnt === (refillCycles-1).U) {
        io.resp := true.B
        state := s_lsu_release
        data_req_cnt := 0.U
      }
    }
  } .elsewhen (state === s_lsu_release) {
    io.lsu_release.valid := true.B
    io.lsu_release.bits := probeResponse
    when (io.lsu_release.fire) {
     state := s_active
    }
  } .elsewhen (state === s_active) {
    io.release.valid := data_req_cnt < refillCycles.U
    io.release.bits := Mux(req.voluntary, voluntaryRelease, probeResponse)

    when (io.mem_grant) {
      acked := true.B
    }
    when (io.release.fire) {
      data_req_cnt := data_req_cnt + 1.U
    }
    when ((data_req_cnt === (refillCycles-1).U) && io.release.fire) {
      state := Mux(req.voluntary, s_grant, s_invalid)
    }
  } .elsewhen (state === s_grant) {
    when (io.mem_grant) {
      acked := true.B
    }
    when (acked) {
      state := s_invalid
    }
  }
}

class BoomProbeUnit(implicit edge: TLEdgeOut, p: Parameters) extends L1HellaCacheModule()(p) {
  val io = IO(new Bundle {
    val req = Flipped(Decoupled(new TLBundleB(edge.bundle)))
    val rep = Decoupled(new TLBundleC(edge.bundle))
    val meta_read = Decoupled(new L1MetaReadReq)
    val meta_write = Decoupled(new L1MetaWriteReq)
    val wb_req = Decoupled(new WritebackReq(edge.bundle))
    val way_en = Input(UInt(nWays.W))
    val wb_rdy = Input(Bool()) // Is writeback unit currently busy? If so need to retry meta read when its done
    val mshr_rdy = Input(Bool()) // Is MSHR ready for this request to proceed?
    val mshr_wb_rdy = Output(Bool()) // Should we block MSHR writebacks while we finish our own?
    val block_state = Input(new ClientMetadata())
    val lsu_release = Decoupled(new TLBundleC(edge.bundle))

    val state = Output(Valid(UInt(coreMaxAddrBits.W)))
  })

  val (s_invalid :: s_meta_read :: s_meta_resp :: s_mshr_req ::
       s_mshr_resp :: s_lsu_release :: s_release :: s_writeback_req :: s_writeback_resp ::
       s_meta_write :: s_meta_write_resp :: Nil) = Enum(11)
  val state = RegInit(s_invalid)

  val req = Reg(new TLBundleB(edge.bundle))
  val req_idx = req.address(idxMSB, idxLSB)
  val req_tag = req.address >> untagBits

  val way_en = Reg(UInt())
  val tag_matches = way_en.orR
  val old_coh = Reg(new ClientMetadata)
  val miss_coh = ClientMetadata.onReset
  val reply_coh = Mux(tag_matches, old_coh, miss_coh)
  val (is_dirty, report_param, new_coh) = reply_coh.onProbe(req.param)

  io.state.valid := state =/= s_invalid
  io.state.bits  := req.address

  io.req.ready := state === s_invalid
  io.rep.valid := state === s_release
  io.rep.bits := edge.ProbeAck(req, report_param)

  assert(!io.rep.valid || !edge.hasData(io.rep.bits),
    "ProbeUnit should not send ProbeAcks with data, WritebackUnit should handle it")

  io.meta_read.valid := state === s_meta_read
  io.meta_read.bits.idx := req_idx
  io.meta_read.bits.tag := req_tag
  io.meta_read.bits.way_en := ~(0.U(nWays.W))

  io.meta_write.valid := state === s_meta_write
  io.meta_write.bits.way_en := way_en
  io.meta_write.bits.idx := req_idx
  io.meta_write.bits.tag := req_tag
  io.meta_write.bits.data.tag := req_tag
  io.meta_write.bits.data.coh := new_coh

  io.wb_req.valid := state === s_writeback_req
  io.wb_req.bits.source := req.source
  io.wb_req.bits.idx := req_idx
  io.wb_req.bits.tag := req_tag
  io.wb_req.bits.param := report_param
  io.wb_req.bits.way_en := way_en
  io.wb_req.bits.voluntary := false.B


  io.mshr_wb_rdy := !state.isOneOf(s_release, s_writeback_req, s_writeback_resp, s_meta_write, s_meta_write_resp)

  io.lsu_release.valid := state === s_lsu_release
  io.lsu_release.bits  := edge.ProbeAck(req, report_param)

  // state === s_invalid
  when (state === s_invalid) {
    when (io.req.fire) {
      state := s_meta_read
      req := io.req.bits
    }
  } .elsewhen (state === s_meta_read) {
    when (io.meta_read.fire) {
      state := s_meta_resp
    }
  } .elsewhen (state === s_meta_resp) {
    // we need to wait one cycle for the metadata to be read from the array
    state := s_mshr_req
  } .elsewhen (state === s_mshr_req) {
    old_coh := io.block_state
    way_en := io.way_en
    // if the read didn't go through, we need to retry
    state := Mux(io.mshr_rdy && io.wb_rdy, s_mshr_resp, s_meta_read)
  } .elsewhen (state === s_mshr_resp) {
    state := Mux(tag_matches && is_dirty, s_writeback_req, s_lsu_release)
  } .elsewhen (state === s_lsu_release) {
    when (io.lsu_release.fire) {
      state := s_release
    }
  } .elsewhen (state === s_release) {
    when (io.rep.ready) {
      state := Mux(tag_matches, s_meta_write, s_invalid)
    }
  } .elsewhen (state === s_writeback_req) {
    when (io.wb_req.fire) {
      state := s_writeback_resp
    }
  } .elsewhen (state === s_writeback_resp) {
    // wait for the writeback request to finish before updating the metadata
    when (io.wb_req.ready) {
      state := s_meta_write
    }
  } .elsewhen (state === s_meta_write) {
    when (io.meta_write.fire) {
      state := s_meta_write_resp
    }
  } .elsewhen (state === s_meta_write_resp) {
    state := s_invalid
  }
}

class BoomL1MetaReadReq(implicit p: Parameters) extends BoomBundle()(p) {
  val req = Vec(lsuWidth, new L1MetaReadReq)
}

class BoomL1DataReadReq(implicit p: Parameters) extends BoomBundle()(p) {
  val req = Vec(lsuWidth, new L1DataReadReq)
  val valid = Vec(lsuWidth, Bool())
}

abstract class AbstractBoomDataArray(implicit p: Parameters) extends BoomModule with HasL1HellaCacheParameters {
  val io = IO(new BoomBundle {
    val read  = Input(Vec(lsuWidth, Valid(new L1DataReadReq)))
    val write = Input(Valid(new L1DataWriteReq))
    val resp  = Output(Vec(lsuWidth, Vec(nWays, Bits(encRowBits.W))))
    val s1_nacks = Output(Vec(lsuWidth, Bool()))
  })

  def pipeMap[T <: Data](f: Int => T) = VecInit((0 until lsuWidth).map(f))

}

class BoomDuplicatedDataArray(implicit p: Parameters) extends AbstractBoomDataArray
{

  val waddr = io.write.bits.addr >> rowOffBits
  for (j <- 0 until lsuWidth) {

    val raddr = io.read(j).bits.addr >> rowOffBits
    for (w <- 0 until nWays) {
      val array = DescribedSRAM(
        name = s"array_${w}_${j}",
        desc = "Non-blocking DCache Data Array",
        size = nSets * refillCycles,
        data = Vec(rowWords, Bits(encDataBits.W))
      )
      when (io.write.bits.way_en(w) && io.write.valid) {
        val data = VecInit((0 until rowWords) map (i => io.write.bits.data(encDataBits*(i+1)-1,encDataBits*i)))
        array.write(waddr, data, io.write.bits.wmask.asBools)
      }
      if (dcacheSinglePorted)
        io.resp(j)(w) := RegNext(array.read(raddr, io.read(j).bits.way_en(w) && io.read(j).valid).asUInt)
      else
        io.resp(j)(w) := RegNext(array.read(raddr, io.read(j).valid).asUInt)
    }
    io.s1_nacks(j) := false.B
  }
}

class BoomBankedDataArray(implicit p: Parameters) extends AbstractBoomDataArray {

  val nBanks   = boomParams.numDCacheBanks
  val bankSize = nSets * refillCycles / nBanks
  require (nBanks >= lsuWidth)
  require (bankSize > 0)

  val bankBits    = log2Ceil(nBanks)
  val bankOffBits = log2Ceil(rowWords) + log2Ceil(wordBytes)
  val bidxBits    = log2Ceil(bankSize)
  val bidxOffBits = bankOffBits + bankBits

  //----------------------------------------------------------------------------------------------------

  val s0_rbanks = if (nBanks > 1) VecInit(io.read.map(r => (r.bits.addr >> bankOffBits)(bankBits-1,0))) else VecInit(0.U)
  val s0_wbank  = if (nBanks > 1) (io.write.bits.addr >> bankOffBits)(bankBits-1,0) else 0.U
  val s0_ridxs  = VecInit(io.read.map(r => (r.bits.addr >> bidxOffBits)(bidxBits-1,0)))
  val s0_widx   = (io.write.bits.addr >> bidxOffBits)(bidxBits-1,0)

  val s0_read_valids    = VecInit(io.read.map(_.valid))
  val s0_bank_conflicts = pipeMap(w => {
    ((s0_rbanks(w) === s0_wbank) && io.write.valid && dcacheSinglePorted.B) ||
    (0 until w).foldLeft(false.B)((c,i) => c || io.read(i).valid && s0_rbanks(i) === s0_rbanks(w))
  })
  val s0_do_bank_read   = s0_read_valids zip s0_bank_conflicts map {case (v,c) => v && !c}
  val s0_bank_read_gnts = Transpose(VecInit(s0_rbanks zip s0_do_bank_read map {case (b,d) => VecInit((UIntToOH(b) & Fill(nBanks,d)).asBools)}))
  val s0_bank_write_gnt = (UIntToOH(s0_wbank) & Fill(nBanks, io.write.valid)).asBools

  //----------------------------------------------------------------------------------------------------

  val s1_rbanks         = RegNext(s0_rbanks)
  val s1_ridxs          = RegNext(s0_ridxs)
  val s1_read_valids    = RegNext(s0_read_valids)
  val s1_pipe_selection = pipeMap(i => VecInit(PriorityEncoderOH(pipeMap(j =>
                            if (j < i) s1_read_valids(j) && s1_rbanks(j) === s1_rbanks(i)
                            else if (j == i) true.B else false.B))))
  val s1_ridx_match     = pipeMap(i => pipeMap(j => if (j < i) s1_ridxs(j) === s1_ridxs(i)
                                                    else if (j == i) true.B else false.B))
  val s1_nacks          = pipeMap(w => s1_read_valids(w)
    && (!RegNext(s0_do_bank_read(w)) || (s1_pipe_selection(w).asUInt & ~s1_ridx_match(w).asUInt).orR)
  )
  val s1_bank_selection = pipeMap(w => Mux1H(s1_pipe_selection(w), s1_rbanks))

  //----------------------------------------------------------------------------------------------------

  val s2_bank_selection = RegNext(s1_bank_selection)
  io.s1_nacks          := s1_nacks

  val data_arrays = Seq.tabulate(nBanks) {
    b => DescribedSRAM(
      name = s"array_${b}",
      desc = "Boom DCache data array",
      size = bankSize,
      data = Vec(nWays * rowWords, Bits(encDataBits.W))
    )
  }
  val s2_bank_reads = Reg(Vec(nBanks, Vec(nWays, Bits(encRowBits.W))))
  for (b <- 0 until nBanks) {
    val array = data_arrays(b)

    val ridx = Mux1H(s0_bank_read_gnts(b), s0_ridxs)
    val way_en = Mux1H(s0_bank_read_gnts(b), io.read.map(_.bits.way_en))
    val write_en = s0_bank_write_gnt(b)
    val write_mask = Cat(Seq.tabulate(nWays) { w =>
      Mux(io.write.bits.way_en(w), io.write.bits.wmask, 0.U(rowWords.W))
    }.reverse).asBools
    val read_en = WireInit(s0_bank_read_gnts(b).reduce(_||_))

    s2_bank_reads(b) := (if (dcacheSinglePorted) {
      assert(!(read_en && write_en))
      array.read(ridx, !write_en && read_en)
    } else {
      array.read(ridx, read_en)
    }).asTypeOf(Vec(nWays, Bits(encRowBits.W)))

    when (write_en) {
      val data = Wire(Vec(nWays * rowWords, Bits(encDataBits.W)))
      for (w <- 0 until nWays) {
        for (i <- 0 until rowWords) {
          data(w*rowWords+i) := io.write.bits.data(encDataBits*(i+1)-1,encDataBits*i)
        }
      }
      array.write(s0_widx, data, write_mask)
    }
  }
  for (w <- 0 until nWays) {
    for (i <- 0 until lsuWidth) {
      io.resp(i)(w) := s2_bank_reads(s2_bank_selection(i))(w)
    }
  }
}

/**
 * Top level class wrapping a non-blocking dcache.
 *
 * @param hartid hardware thread for the cache
 */
class BoomNonBlockingDCache(staticIdForMetadataUseOnly: Int)(implicit p: Parameters) extends LazyModule
{
  private val tileParams = p(TileKey)
  protected val cfg = tileParams.dcache.get

  protected def cacheClientParameters = cfg.scratch.map(x => Seq()).getOrElse(Seq(TLMasterParameters.v1(
    name          = s"Core ${staticIdForMetadataUseOnly} DCache",
    sourceId      = IdRange(0, 1 max (cfg.nMSHRs + 1)),
    supportsProbe = TransferSizes(cfg.blockBytes, cfg.blockBytes))))

  protected def mmioClientParameters = Seq(TLMasterParameters.v1(
    name          = s"Core ${staticIdForMetadataUseOnly} DCache MMIO",
    sourceId      = IdRange(cfg.nMSHRs + 1, cfg.nMSHRs + 1 + cfg.nMMIOs),
    requestFifo   = true))

  val node = TLClientNode(Seq(TLMasterPortParameters.v1(
    cacheClientParameters ++ mmioClientParameters,
    minLatency = 1)))


  lazy val module = new BoomNonBlockingDCacheModule(this)

  def flushOnFenceI = cfg.scratch.isEmpty && !node.edges.out(0).manager.managers.forall(m => !m.supportsAcquireT || !m.executable || m.regionType >= RegionType.TRACKED || m.regionType <= RegionType.IDEMPOTENT)

  require(!tileParams.core.haveCFlush || cfg.scratch.isEmpty, "CFLUSH_D_L1 instruction requires a D$")
}


class BoomDCacheBundle(implicit p: Parameters, edge: TLEdgeOut) extends BoomBundle()(p) {
  val errors = new DCacheErrors
  val lsu   = Flipped(new LSUDMemIO)
}

class BoomNonBlockingDCacheModule(outer: BoomNonBlockingDCache) extends LazyModuleImp(outer)
  with HasL1HellaCacheParameters
  with HasBoomCoreParameters
{
  implicit val edge = outer.node.edges.out(0)
  val (tl_out, _) = outer.node.out(0)
  val io = IO(new BoomDCacheBundle)

  io.errors := DontCare

  private val fifoManagers = edge.manager.managers.filter(TLFIFOFixer.allVolatile)
  fifoManagers.foreach { m =>
    require (m.fifoId == fifoManagers.head.fifoId,
      s"IOMSHRs must be FIFO for all regions with effects, but HellaCache sees ${m.nodePath.map(_.name)}")
  }

  def widthMap[T <: Data](f: Int => T) = VecInit((0 until lsuWidth).map(f))

  val t_replay :: t_probe :: t_wb :: t_mshr_meta_read :: t_lsu :: t_prefetch :: Nil = Enum(6)

  val wb = Module(new BoomWritebackUnit)
  val prober = Module(new BoomProbeUnit)
  val mshrs = Module(new BoomMSHRFile)
  mshrs.io.clear_all    := io.lsu.force_order
  mshrs.io.brupdate       := io.lsu.brupdate
  mshrs.io.exception    := io.lsu.exception
  mshrs.io.rob_pnr_idx  := io.lsu.rob_pnr_idx
  mshrs.io.rob_head_idx := io.lsu.rob_head_idx

  // tags
  def onReset = L1Metadata(0.U, ClientMetadata.onReset)
  val meta = Seq.fill(lsuWidth) { Module(new L1MetadataArray(onReset _)) }
  val metaWriteArb = Module(new Arbiter(new L1MetaWriteReq, 2))
  // 0 goes to MSHR refills, 1 goes to prober
  val metaReadArb = Module(new Arbiter(new BoomL1MetaReadReq, 6))
  // 0 goes to MSHR replays, 1 goes to prober, 2 goes to wb, 3 goes to MSHR meta read,
  // 4 goes to pipeline, 5 goes to prefetcher

  metaReadArb.io.in := DontCare
  for (w <- 0 until lsuWidth) {
    meta(w).io.write.valid := metaWriteArb.io.out.fire
    meta(w).io.write.bits  := metaWriteArb.io.out.bits
    meta(w).io.read.valid  := metaReadArb.io.out.valid
    meta(w).io.read.bits   := metaReadArb.io.out.bits.req(w)
  }
  metaReadArb.io.out.ready  := meta.map(_.io.read.ready).reduce(_||_)
  metaWriteArb.io.out.ready := meta.map(_.io.write.ready).reduce(_||_)

  // data
  val data = Module(if (boomParams.numDCacheBanks == 1) new BoomDuplicatedDataArray else new BoomBankedDataArray)
  val dataWriteArb = Module(new Arbiter(new L1DataWriteReq, 2))
  // 0 goes to pipeline, 1 goes to MSHR refills
  val dataReadArb = Module(new Arbiter(new BoomL1DataReadReq, 3))
  // 0 goes to MSHR replays, 1 goes to wb, 2 goes to pipeline
  dataReadArb.io.in := DontCare

  for (w <- 0 until lsuWidth) {
    data.io.read(w).valid := dataReadArb.io.out.bits.valid(w) && dataReadArb.io.out.valid
    data.io.read(w).bits  := dataReadArb.io.out.bits.req(w)
  }
  dataReadArb.io.out.ready := true.B

  data.io.write.valid := dataWriteArb.io.out.fire
  data.io.write.bits  := dataWriteArb.io.out.bits
  dataWriteArb.io.out.ready := true.B
  val singlePortedDCacheWrite = data.io.write.valid && dcacheSinglePorted.B

  // ------------
  // New requests

  // In a 1-wide LSU, load/store wakeups and MSHR resps contend for same port, so
  // we should block incoming requests when the MSHR trying to respond
  val block_incoming_reqs = (lsuWidth == 1).B && mshrs.io.resp.valid
  io.lsu.req.ready := metaReadArb.io.in(4).ready && dataReadArb.io.in(2).ready && !block_incoming_reqs
  metaReadArb.io.in(4).valid := io.lsu.req.valid && !block_incoming_reqs
  dataReadArb.io.in(2).valid := io.lsu.req.valid && !block_incoming_reqs
  for (w <- 0 until lsuWidth) {
    // Tag read for new requests
    metaReadArb.io.in(4).bits.req(w).idx    := io.lsu.req.bits(w).bits.addr >> blockOffBits
    metaReadArb.io.in(4).bits.req(w).way_en := DontCare
    metaReadArb.io.in(4).bits.req(w).tag    := DontCare
    // Data read for new requests
    dataReadArb.io.in(2).bits.valid(w)      := io.lsu.req.bits(w).valid
    dataReadArb.io.in(2).bits.req(w).addr   := io.lsu.req.bits(w).bits.addr
    dataReadArb.io.in(2).bits.req(w).way_en := ~0.U(nWays.W)
  }

  // ------------
  // MSHR Replays
  val replay_req = Wire(Vec(lsuWidth, new BoomDCacheReq))
  replay_req               := DontCare
  replay_req(0).uop        := mshrs.io.replay.bits.uop
  replay_req(0).addr       := mshrs.io.replay.bits.addr
  replay_req(0).data       := mshrs.io.replay.bits.data
  replay_req(0).is_hella   := mshrs.io.replay.bits.is_hella
  // Don't let replays get nacked due to conflict with dcache write
  mshrs.io.replay.ready    := metaReadArb.io.in(0).ready && dataReadArb.io.in(0).ready && !singlePortedDCacheWrite
  // Tag read for MSHR replays
  // We don't actually need to read the metadata, for replays we already know our way
  metaReadArb.io.in(0).valid              := mshrs.io.replay.valid && !singlePortedDCacheWrite
  metaReadArb.io.in(0).bits.req(0).idx    := mshrs.io.replay.bits.addr >> blockOffBits
  metaReadArb.io.in(0).bits.req(0).way_en := DontCare
  metaReadArb.io.in(0).bits.req(0).tag    := DontCare
  // Data read for MSHR replays
  dataReadArb.io.in(0).valid              := mshrs.io.replay.valid && !singlePortedDCacheWrite
  dataReadArb.io.in(0).bits.req(0).addr   := mshrs.io.replay.bits.addr
  dataReadArb.io.in(0).bits.req(0).way_en := mshrs.io.replay.bits.way_en
  dataReadArb.io.in(0).bits.valid         := widthMap(w => (w == 0).B)

  // -----------
  // MSHR Meta read
  val mshr_read_req = Wire(Vec(lsuWidth, new BoomDCacheReq))
  mshr_read_req             := DontCare
  mshr_read_req(0).uop      := NullMicroOp
  mshr_read_req(0).addr     := Cat(mshrs.io.meta_read.bits.tag, mshrs.io.meta_read.bits.idx) << blockOffBits
  mshr_read_req(0).data     := DontCare
  mshr_read_req(0).is_hella := false.B
  metaReadArb.io.in(3).valid       := mshrs.io.meta_read.valid
  metaReadArb.io.in(3).bits.req(0) := mshrs.io.meta_read.bits
  mshrs.io.meta_read.ready         := metaReadArb.io.in(3).ready



  // -----------
  // Write-backs
  val wb_fire = wb.io.meta_read.fire && wb.io.data_req.fire
  val wb_req = Wire(Vec(lsuWidth, new BoomDCacheReq))
  wb_req             := DontCare
  wb_req(0).uop      := NullMicroOp
  wb_req(0).addr     := Cat(wb.io.meta_read.bits.tag, wb.io.data_req.bits.addr)
  wb_req(0).data     := DontCare
  wb_req(0).is_hella := false.B
  // Couple the two decoupled interfaces of the WBUnit's meta_read and data_read
  // Can't launch data read if possibility of conflict w. write
  // Tag read for write-back
  metaReadArb.io.in(2).valid        := wb.io.meta_read.valid && !singlePortedDCacheWrite
  metaReadArb.io.in(2).bits.req(0)  := wb.io.meta_read.bits
  wb.io.meta_read.ready := metaReadArb.io.in(2).ready && dataReadArb.io.in(1).ready && !singlePortedDCacheWrite
  // Data read for write-back
  dataReadArb.io.in(1).valid        := wb.io.data_req.valid && !singlePortedDCacheWrite
  dataReadArb.io.in(1).bits.req(0)  := wb.io.data_req.bits
  dataReadArb.io.in(1).bits.valid   := widthMap(w => (w == 0).B)
  wb.io.data_req.ready  := metaReadArb.io.in(2).ready && dataReadArb.io.in(1).ready && !singlePortedDCacheWrite
  assert(!(wb.io.meta_read.fire ^ wb.io.data_req.fire))

  // -------
  // Prober
  val prober_fire  = prober.io.meta_read.fire
  val prober_req   = Wire(Vec(lsuWidth, new BoomDCacheReq))
  prober_req             := DontCare
  prober_req(0).uop      := NullMicroOp
  prober_req(0).addr     := Cat(prober.io.meta_read.bits.tag, prober.io.meta_read.bits.idx) << blockOffBits
  prober_req(0).data     := DontCare
  prober_req(0).is_hella := false.B
  // Tag read for prober
  metaReadArb.io.in(1).valid       := prober.io.meta_read.valid
  metaReadArb.io.in(1).bits.req(0) := prober.io.meta_read.bits
  prober.io.meta_read.ready := metaReadArb.io.in(1).ready
  // Prober does not need to read data array

  // -------
  // Prefetcher
  val prefetch_fire = mshrs.io.prefetch.fire
  val prefetch_req  = Wire(Vec(lsuWidth, new BoomDCacheReq))
  prefetch_req    := DontCare
  prefetch_req(0) := mshrs.io.prefetch.bits
  // Tag read for prefetch
  metaReadArb.io.in(5).valid              := mshrs.io.prefetch.valid
  metaReadArb.io.in(5).bits.req(0).idx    := mshrs.io.prefetch.bits.addr >> blockOffBits
  metaReadArb.io.in(5).bits.req(0).way_en := DontCare
  metaReadArb.io.in(5).bits.req(0).tag    := DontCare
  mshrs.io.prefetch.ready := metaReadArb.io.in(5).ready
  // Prefetch does not need to read data array

  val s0_valid = Mux(io.lsu.req.fire, VecInit(io.lsu.req.bits.map(_.valid)),
                 Mux(mshrs.io.replay.fire || wb_fire || prober_fire || prefetch_fire || mshrs.io.meta_read.fire,
                                        VecInit(1.U(lsuWidth.W).asBools), VecInit(0.U(lsuWidth.W).asBools)))
  val s0_req   = Mux(io.lsu.req.fire          , VecInit(io.lsu.req.bits.map(_.bits)),
                 Mux(wb_fire                  , wb_req,
                 Mux(prober_fire              , prober_req,
                 Mux(prefetch_fire            , prefetch_req,
                 Mux(mshrs.io.meta_read.fire, mshr_read_req
                                              , replay_req)))))
  val s0_type  = Mux(io.lsu.req.fire        , t_lsu,
                 Mux(wb_fire                  , t_wb,
                 Mux(prober_fire              , t_probe,
                 Mux(prefetch_fire            , t_prefetch,
                 Mux(mshrs.io.meta_read.fire, t_mshr_meta_read
                                              , t_replay)))))

  // Does this request need to send a response or nack
  val s0_send_resp_or_nack = Mux(io.lsu.req.fire, s0_valid,
    VecInit(Mux(mshrs.io.replay.fire && isRead(mshrs.io.replay.bits.uop.mem_cmd), 1.U(lsuWidth.W), 0.U(lsuWidth.W)).asBools))


  val s1_req          = RegNext(s0_req)
  for (w <- 0 until lsuWidth)
    s1_req(w).uop.br_mask := GetNewBrMask(io.lsu.brupdate, s0_req(w).uop)
  val s2_store_failed = Wire(Bool())
  val s1_valid = widthMap(w =>
                 RegNext(s0_valid(w)                                     &&
                         !IsKilledByBranch(io.lsu.brupdate, false.B, s0_req(w).uop) &&
                         !(io.lsu.exception && s0_req(w).uop.uses_ldq)   &&
                         !(s2_store_failed && io.lsu.req.fire && s0_req(w).uop.uses_stq),
                         init=false.B))

  for (w <- 0 until lsuWidth)
    assert(!(io.lsu.s1_kill(w) && !RegNext(io.lsu.req.fire) && !RegNext(io.lsu.req.bits(w).valid)))
  val s1_addr         = s1_req.map(_.addr)
  val s1_nack         = s1_addr.map(a => a(idxMSB,idxLSB) === prober.io.meta_write.bits.idx && !prober.io.req.ready)
  val s1_send_resp_or_nack = RegNext(s0_send_resp_or_nack)
  val s1_type         = RegNext(s0_type)

  val s1_mshr_meta_read_way_en = RegNext(mshrs.io.meta_read.bits.way_en)
  val s1_replay_way_en         = RegNext(mshrs.io.replay.bits.way_en) // For replays, the metadata isn't written yet
  val s1_wb_way_en             = RegNext(wb.io.data_req.bits.way_en)

  // tag check
  def wayMap[T <: Data](f: Int => T) = VecInit((0 until nWays).map(f))
  val s1_tag_eq_way = widthMap(i => wayMap((w: Int) => meta(i).io.resp(w).tag === (s1_addr(i) >> untagBits)).asUInt)
  val s1_tag_match_way = widthMap(i =>
                         Mux(s1_type === t_replay, s1_replay_way_en,
                         Mux(s1_type === t_wb,     s1_wb_way_en,
                         Mux(s1_type === t_mshr_meta_read, s1_mshr_meta_read_way_en,
                           wayMap((w: Int) => s1_tag_eq_way(i)(w) && meta(i).io.resp(w).coh.isValid()).asUInt))))

  val s1_wb_idx_matches = widthMap(i => (s1_addr(i)(untagBits-1,blockOffBits) === wb.io.idx.bits) && wb.io.idx.valid)

  for (w <- 0 until lsuWidth) {
    io.lsu.s1_nack_advisory(w) := data.io.s1_nacks(w)
  }

  val s2_req   = RegNext(s1_req)
  val s2_type  = RegNext(s1_type)
  val s2_valid = widthMap(w =>
                  RegNext(s1_valid(w) &&
                         !io.lsu.s1_kill(w) &&
                         !IsKilledByBranch(io.lsu.brupdate, false.B, s1_req(w).uop) &&
                         !(io.lsu.exception && s1_req(w).uop.uses_ldq) &&
                         !(s2_store_failed && (s1_type === t_lsu) && s1_req(w).uop.uses_stq)))
  for (w <- 0 until lsuWidth)
    s2_req(w).uop.br_mask := GetNewBrMask(io.lsu.brupdate, s1_req(w).uop)

  val s2_tag_match_way = RegNext(s1_tag_match_way)
  val s2_tag_match     = s2_tag_match_way.map(_.orR)
  val s2_hit_state     = widthMap(i => Mux1H(s2_tag_match_way(i), wayMap((w: Int) => RegNext(meta(i).io.resp(w).coh))))
  val s2_has_permission = widthMap(w => s2_hit_state(w).onAccess(s2_req(w).uop.mem_cmd)._1)
  val s2_new_hit_state  = widthMap(w => s2_hit_state(w).onAccess(s2_req(w).uop.mem_cmd)._3)

  val s2_hit = widthMap(w => (s2_tag_match(w) && s2_has_permission(w) && s2_hit_state(w) === s2_new_hit_state(w) && !mshrs.io.block_hit(w)) || s2_type.isOneOf(t_replay, t_wb))
  val s2_nack = Wire(Vec(lsuWidth, Bool()))
  assert(!(s2_type === t_replay && !s2_hit(0)), "Replays should always hit")
  assert(!(s2_type === t_wb && !s2_hit(0)), "Writeback should always see data hit")

  val s2_wb_idx_matches = RegNext(s1_wb_idx_matches)

  // lr/sc
  val debug_sc_fail_addr = RegInit(0.U)
  val debug_sc_fail_cnt  = RegInit(0.U(8.W))

  val lrsc_count = RegInit(0.U(log2Ceil(lrscCycles).W))
  val lrsc_valid = lrsc_count > lrscBackoff.U
  val lrsc_addr  = Reg(UInt())
  val s2_lr = s2_req(0).uop.mem_cmd === M_XLR && (!RegNext(s1_nack(0)) || s2_type === t_replay)
  val s2_sc = s2_req(0).uop.mem_cmd === M_XSC && (!RegNext(s1_nack(0)) || s2_type === t_replay)
  val s2_lrsc_addr_match = widthMap(w => lrsc_valid && lrsc_addr === (s2_req(w).addr >> blockOffBits))
  val s2_sc_fail = s2_sc && !s2_lrsc_addr_match(0)
  when (lrsc_count > 0.U) { lrsc_count := lrsc_count - 1.U }
  when (s2_valid(0) && ((s2_type === t_lsu && s2_hit(0) && !s2_nack(0)) ||
                     (s2_type === t_replay && s2_req(0).uop.mem_cmd =/= M_FLUSH_ALL))) {
    when (s2_lr) {
      lrsc_count := (lrscCycles - 1).U
      lrsc_addr := s2_req(0).addr >> blockOffBits
    }
    when (lrsc_count > 0.U) {
      lrsc_count := 0.U
    }
  }
  for (w <- 0 until lsuWidth) {
    when (s2_valid(w)                            &&
      s2_type === t_lsu                          &&
      !s2_hit(w)                                 &&
      !(s2_has_permission(w) && s2_tag_match(w)) &&
      s2_lrsc_addr_match(w)                      &&
      !s2_nack(w)) {
      lrsc_count := 0.U
    }
  }

  when (s2_valid(0)) {
    when (s2_req(0).addr === debug_sc_fail_addr) {
      when (s2_sc_fail) {
        debug_sc_fail_cnt := debug_sc_fail_cnt + 1.U
      } .elsewhen (s2_sc) {
        debug_sc_fail_cnt := 0.U
      }
    } .otherwise {
      when (s2_sc_fail) {
        debug_sc_fail_addr := s2_req(0).addr
        debug_sc_fail_cnt  := 1.U
      }
    }
  }
  assert(debug_sc_fail_cnt < 100.U, "L1DCache failed too many SCs in a row")

  val s2_data = Wire(Vec(lsuWidth, Vec(nWays, UInt(encRowBits.W))))
  for (i <- 0 until lsuWidth) {
    for (w <- 0 until nWays) {
      s2_data(i)(w) := data.io.resp(i)(w)
    }
  }

  val s2_data_muxed = widthMap(w => Mux1H(s2_tag_match_way(w), s2_data(w)))
  val s2_word_idx   = widthMap(w => if (rowWords == 1) 0.U else s2_req(w).addr(log2Up(rowWords*wordBytes)-1, log2Up(wordBytes)))

  // replacement policy
  val replacer = cacheParams.replacement
  val s1_replaced_way_en = UIntToOH(replacer.way)
  val s2_replaced_way_en = UIntToOH(RegNext(replacer.way))
  val s2_repl_meta = widthMap(i => Mux1H(s2_replaced_way_en, wayMap((w: Int) => RegNext(meta(i).io.resp(w))).toSeq))

  // nack because of incoming probe
  val s2_nack_hit    = RegNext(VecInit(s1_nack))
  // Nack when we hit something currently being evicted
  val s2_nack_victim = widthMap(w => s2_valid(w) &&  s2_hit(w) && mshrs.io.secondary_miss(w))
  // MSHRs not ready for request
  val s2_nack_miss   = widthMap(w => s2_valid(w) && !s2_hit(w) && !mshrs.io.req(w).ready)
  // Bank conflict on data arrays
  val s2_nack_data   = widthMap(w => s2_valid(w) && RegNext(data.io.s1_nacks(w)))
  // Can't allocate MSHR for same set currently being written back
  val s2_nack_wb     = widthMap(w => s2_valid(w) && !s2_hit(w) && s2_wb_idx_matches(w))

  s2_nack           := widthMap(w => (s2_nack_miss(w) || s2_nack_hit(w) || s2_nack_victim(w) || s2_nack_data(w) || s2_nack_wb(w)) && s2_type =/= t_replay)
  assert(!(s2_nack_data.reduce(_||_) && s2_type.isOneOf(t_replay, t_wb)))
  val s2_send_resp = widthMap(w => (
    RegNext(s1_send_resp_or_nack(w)) &&
      (!(s2_nack_hit(w) || s2_nack_victim(w) || s2_nack_data(w)) || s2_type === t_replay) &&
      s2_hit(w) && isRead(s2_req(w).uop.mem_cmd)
  ))
  val s2_send_store_ack = widthMap(w => (
    RegNext(s1_send_resp_or_nack(w)) && !s2_nack(w) && isWrite(s2_req(w).uop.mem_cmd) &&
      (s2_hit(w) || mshrs.io.req(w).fire)))
  val s2_send_nack = widthMap(w => (RegNext(s1_send_resp_or_nack(w)) && s2_nack(w)))
  for (w <- 0 until lsuWidth)
    assert(!(s2_send_resp(w) && s2_send_nack(w)))

  // hits always send a response
  // If MSHR is not available, LSU has to replay this request later
  // If MSHR is available and this is only a store(not a amo), we don't need to wait for resp later
  s2_store_failed := s2_valid(0) && s2_nack(0) && s2_send_nack(0) && s2_req(0).uop.uses_stq

  // Miss handling
  for (w <- 0 until lsuWidth) {
    mshrs.io.req(w).valid := s2_valid(w)          &&
                            !s2_hit(w)            &&
                            !s2_nack_hit(w)       &&
                            !s2_nack_victim(w)    &&
                            !s2_nack_data(w)      &&
                            !s2_nack_wb(w)        &&
                             s2_type.isOneOf(t_lsu, t_prefetch)             &&
                            !(io.lsu.exception && s2_req(w).uop.uses_ldq)   &&
                             (isPrefetch(s2_req(w).uop.mem_cmd) ||
                              isRead(s2_req(w).uop.mem_cmd)     ||
                              isWrite(s2_req(w).uop.mem_cmd))
    assert(!(mshrs.io.req(w).valid && s2_type === t_replay), "Replays should not need to go back into MSHRs")
    mshrs.io.req(w).bits             := DontCare
    mshrs.io.req(w).bits.uop         := s2_req(w).uop
    mshrs.io.req(w).bits.addr        := s2_req(w).addr
    mshrs.io.req(w).bits.tag_match   := s2_tag_match(w)
    mshrs.io.req(w).bits.old_meta    := Mux(s2_tag_match(w), L1Metadata(s2_repl_meta(w).tag, s2_hit_state(w)), s2_repl_meta(w))
    mshrs.io.req(w).bits.way_en      := Mux(s2_tag_match(w), s2_tag_match_way(w), s2_replaced_way_en)

    mshrs.io.req(w).bits.data        := s2_req(w).data
    mshrs.io.req(w).bits.is_hella    := s2_req(w).is_hella
    mshrs.io.req_is_probe(w)         := s2_type === t_probe && s2_valid(w)
  }

  mshrs.io.meta_resp.valid      := !s2_nack_hit(0) || prober.io.mshr_wb_rdy
  mshrs.io.meta_resp.bits       := Mux1H(s2_tag_match_way(0), RegNext(meta(0).io.resp))
  when (mshrs.io.req.map(_.fire).reduce(_||_)) { replacer.miss }
  tl_out.a <> mshrs.io.mem_acquire

  // probes and releases
  prober.io.req.valid   := tl_out.b.valid && !lrsc_valid
  tl_out.b.ready        := prober.io.req.ready && !lrsc_valid
  prober.io.req.bits    := tl_out.b.bits
  prober.io.way_en      := s2_tag_match_way(0)
  prober.io.block_state := s2_hit_state(0)
  metaWriteArb.io.in(1) <> prober.io.meta_write
  prober.io.mshr_rdy    := mshrs.io.probe_rdy
  prober.io.wb_rdy      := (prober.io.meta_write.bits.idx =/= wb.io.idx.bits) || !wb.io.idx.valid
  mshrs.io.prober_state := prober.io.state

  // refills
  when (tl_out.d.bits.source === cfg.nMSHRs.U) {
    // This should be ReleaseAck
    tl_out.d.ready := true.B
    mshrs.io.mem_grant.valid := false.B
    mshrs.io.mem_grant.bits  := DontCare
  } .otherwise {
    // This should be GrantData
    mshrs.io.mem_grant <> tl_out.d
  }

  dataWriteArb.io.in(1) <> mshrs.io.refill
  metaWriteArb.io.in(0) <> mshrs.io.meta_write

  tl_out.e <> mshrs.io.mem_finish

  // writebacks
  val wbArb = Module(new Arbiter(new WritebackReq(edge.bundle), 2))
  // 0 goes to prober, 1 goes to MSHR evictions
  wbArb.io.in(0)       <> prober.io.wb_req
  wbArb.io.in(1)       <> mshrs.io.wb_req
  wb.io.req            <> wbArb.io.out
  wb.io.data_resp       := s2_data_muxed(0)
  mshrs.io.wb_resp      := wb.io.resp
  wb.io.mem_grant       := tl_out.d.fire && tl_out.d.bits.source === cfg.nMSHRs.U

  val lsu_release_arb = Module(new Arbiter(new TLBundleC(edge.bundle), 2))
  io.lsu.release <> lsu_release_arb.io.out
  lsu_release_arb.io.in(0) <> wb.io.lsu_release
  lsu_release_arb.io.in(1) <> prober.io.lsu_release

  TLArbiter.lowest(edge, tl_out.c, wb.io.release, prober.io.rep)

  io.lsu.perf.release := edge.done(tl_out.c)
  io.lsu.perf.acquire := edge.done(tl_out.a)

  // load data gen
  val s2_data_word_prebypass = widthMap(w => s2_data_muxed(w) >> Cat(s2_word_idx(w), 0.U(log2Ceil(coreDataBits).W)))
  val s2_data_word = Wire(Vec(lsuWidth, UInt()))

  val loadgen = (0 until lsuWidth).map { w =>
    new LoadGen(s2_req(w).uop.mem_size, s2_req(w).uop.mem_signed, s2_req(w).addr,
                s2_data_word(w), s2_sc && (w == 0).B, wordBytes)
  }
  // Mux between cache responses and uncache responses
  for (w <- 0 until lsuWidth) {
    io.lsu.resp(w).valid := s2_valid(w) && s2_send_resp(w)
    io.lsu.resp(w).bits.uop := s2_req(w).uop
    io.lsu.resp(w).bits.data := loadgen(w).data | s2_sc_fail
    io.lsu.resp(w).bits.is_hella := s2_req(w).is_hella



    io.lsu.nack(w).valid := s2_valid(w) && s2_send_nack(w)
    io.lsu.nack(w).bits  := s2_req(w)
    assert(!(io.lsu.nack(w).valid && s2_type =/= t_lsu))

    io.lsu.store_ack(w).valid := s2_valid(w) && s2_send_store_ack(w) && (w == 0).B
    io.lsu.store_ack(w).bits  := s2_req(w)
  }

  io.lsu.ll_resp <> mshrs.io.resp

  // Store/amo hits
  val s3_req   = Wire(new BoomDCacheReq)
  s3_req := RegNext(s2_req(0))
  val s3_valid = RegNext(s2_valid(0) && s2_hit(0) && isWrite(s2_req(0).uop.mem_cmd) &&
                         !s2_sc_fail && !(s2_send_nack(0) && s2_nack(0)))
  val s3_data_word = RegNext(s2_data_word(0))
  for (w <- 1 until lsuWidth) {
    assert(!(s2_valid(w) && s2_hit(w) && isWrite(s2_req(w).uop.mem_cmd) &&
                         !s2_sc_fail && !(s2_send_nack(w) && s2_nack(w))),
      "Store must go through 0th pipe in L1D")
  }


  // For bypassing
  val s4_req   = RegNext(s3_req)
  val s4_valid = RegNext(s3_valid)
  val s5_req   = RegNext(s4_req)
  val s5_valid = RegNext(s4_valid)

  val s3_bypass = widthMap(w => s3_valid && ((s2_req(w).addr >> wordOffBits) === (s3_req.addr >> wordOffBits)))
  val s4_bypass = widthMap(w => s4_valid && ((s2_req(w).addr >> wordOffBits) === (s4_req.addr >> wordOffBits)))
  val s5_bypass = widthMap(w => s5_valid && ((s2_req(w).addr >> wordOffBits) === (s5_req.addr >> wordOffBits)))

  // Store -> Load bypassing
  for (w <- 0 until lsuWidth) {
    s2_data_word(w) := Mux(s3_bypass(w), s3_req.data,
                       Mux(s4_bypass(w), s4_req.data,
                       Mux(s5_bypass(w), s5_req.data,
                                         s2_data_word_prebypass(w))))
  }
  val amoalu   = Module(new AMOALU(xLen))
  amoalu.io.mask := new StoreGen(s3_req.uop.mem_size, s3_req.addr, 0.U, xLen/8).mask
  amoalu.io.cmd  := s3_req.uop.mem_cmd
  amoalu.io.lhs  := s3_data_word
  amoalu.io.rhs  := RegNext(s2_req(0).data)


  s3_req.data := amoalu.io.out
  val s3_way   = RegNext(s2_tag_match_way(0))

  dataWriteArb.io.in(0).valid       := s3_valid
  dataWriteArb.io.in(0).bits.addr   := s3_req.addr
  dataWriteArb.io.in(0).bits.wmask  := UIntToOH(s3_req.addr.extract(rowOffBits-1,offsetlsb))
  dataWriteArb.io.in(0).bits.data   := Fill(rowWords, s3_req.data)
  dataWriteArb.io.in(0).bits.way_en := s3_way


  io.lsu.ordered := mshrs.io.fence_rdy && !s1_valid.reduce(_||_) && !s2_valid.reduce(_||_)
}
