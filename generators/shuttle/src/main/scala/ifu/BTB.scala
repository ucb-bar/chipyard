package shuttle.ifu

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.rocket._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.tile._
import freechips.rocketchip.util._
import freechips.rocketchip.util.property._

// Forked from Rocket
class ShuttleBTBUpdate(implicit p: Parameters) extends BTBUpdate()(p) {
  val mispredict = Bool()
}

class ShuttleBTB(implicit p: Parameters) extends BtbModule {
  val io = IO(new Bundle {
    val req = Flipped(Valid(new BTBReq))
    val resp = Valid(new BTBResp)
    val btb_update = Flipped(Valid(new ShuttleBTBUpdate))
    val bht_update = Flipped(Valid(new BHTUpdate))
    val bht_advance = Flipped(Valid(new BTBResp))
    val flush = Input(Bool())
  })

  val idxs = Reg(Vec(entries, UInt((matchBits - log2Up(coreInstBytes)).W)))
  val idxPages = Reg(Vec(entries, UInt(log2Up(nPages).W)))
  val tgts = Reg(Vec(entries, UInt((matchBits - log2Up(coreInstBytes)).W)))
  val tgtPages = Reg(Vec(entries, UInt(log2Up(nPages).W)))
  val ubits = Reg(Vec(entries, Bool()))
  val pages = Reg(Vec(nPages, UInt((vaddrBits - matchBits).W)))
  val pageValid = RegInit(0.U(nPages.W))
  val pagesMasked = (pageValid.asBools zip pages).map { case (v, p) => Mux(v, p, 0.U) }

  val isValid = RegInit(0.U(entries.W))
  val cfiType = Reg(Vec(entries, CFIType()))
  val brIdx = Reg(Vec(entries, UInt(log2Up(fetchWidth).W)))

  private def page(addr: UInt) = addr >> matchBits
  private def pageMatch(addr: UInt) = {
    val p = page(addr)
    pageValid & pages.map(_ === p).asUInt
  }
  private def idxMatch(addr: UInt) = {
    val idx = addr(matchBits-1, log2Up(coreInstBytes))
    idxs.map(_ === idx).asUInt & isValid
  }

  val r_btb_update = Pipe(io.btb_update)
  val update_target = r_btb_update.bits.target

  val pageHit = pageMatch(io.req.bits.addr)
  val idxHit = idxMatch(io.req.bits.addr)

  val updatePageHit = pageMatch(r_btb_update.bits.pc)
  val (updateHit, updateHitAddr) =
    if (updatesOutOfOrder) {
      val updateHits = (pageHit << 1)(Mux1H(idxMatch(r_btb_update.bits.pc), idxPages))
      (updateHits.orR, OHToUInt(updateHits))
    } else (r_btb_update.bits.prediction.entry < entries.U, r_btb_update.bits.prediction.entry)

  val useUpdatePageHit = updatePageHit.orR
  val usePageHit = pageHit.orR
  val doIdxPageRepl = !useUpdatePageHit
  val nextPageRepl = RegInit(0.U(log2Ceil(nPages).W))
  val idxPageRepl = Cat(pageHit(nPages-2,0), pageHit(nPages-1)) | Mux(usePageHit, 0.U, UIntToOH(nextPageRepl))
  val idxPageUpdateOH = Mux(useUpdatePageHit, updatePageHit, idxPageRepl)
  val idxPageUpdate = OHToUInt(idxPageUpdateOH)
  val idxPageReplEn = Mux(doIdxPageRepl, idxPageRepl, 0.U)

  val samePage = page(r_btb_update.bits.pc) === page(update_target)
  val doTgtPageRepl = !samePage && !usePageHit
  val tgtPageRepl = Mux(samePage, idxPageUpdateOH, Cat(idxPageUpdateOH(nPages-2,0), idxPageUpdateOH(nPages-1)))
  val tgtPageUpdate = OHToUInt(pageHit | Mux(usePageHit, 0.U, tgtPageRepl))
  val tgtPageReplEn = Mux(doTgtPageRepl, tgtPageRepl, 0.U)

  when (r_btb_update.valid && r_btb_update.bits.mispredict && (doIdxPageRepl || doTgtPageRepl)) {
    val both = doIdxPageRepl && doTgtPageRepl
    val next = nextPageRepl + Mux[UInt](both, 2.U, 1.U)
    nextPageRepl := Mux(next >= nPages.U, next(0), next)
  }

  val repl = new PseudoLRU(entries)
  val waddr = Mux(updateHit, updateHitAddr, repl.way)
  val r_resp = Pipe(io.resp)
  when (r_resp.valid && r_resp.bits.taken || (r_btb_update.valid && r_btb_update.bits.mispredict)) {
    repl.access(Mux(r_btb_update.valid, waddr, r_resp.bits.entry))
  }

  when (r_btb_update.valid && r_btb_update.bits.mispredict) {
    ubits(waddr) := 0.U
  }
  when (r_btb_update.valid && !r_btb_update.bits.mispredict) {
    ubits(waddr) := 1.U
  }
  when (r_btb_update.valid && r_btb_update.bits.mispredict && !(updateHit && ubits(waddr) && isValid(waddr))) {
    val mask = UIntToOH(waddr)
    idxs(waddr) := r_btb_update.bits.pc(matchBits-1, log2Up(coreInstBytes))
    tgts(waddr) := update_target(matchBits-1, log2Up(coreInstBytes))
    ubits(waddr) := 1.U
    idxPages(waddr) := idxPageUpdate +& 1.U // the +1 corresponds to the <<1 on io.resp.valid
    tgtPages(waddr) := tgtPageUpdate
    cfiType(waddr) := r_btb_update.bits.cfiType
    isValid := Mux(r_btb_update.bits.isValid, isValid | mask, isValid & ~mask)
    if (fetchWidth > 1)
      brIdx(waddr) := r_btb_update.bits.br_pc >> log2Up(coreInstBytes)

    require(nPages % 2 == 0)
    val idxWritesEven = !idxPageUpdate(0)

    def writeBank(i: Int, mod: Int, en: UInt, data: UInt) =
      for (i <- i until nPages by mod)
        when (en(i)) { pages(i) := data }

    writeBank(0, 2, Mux(idxWritesEven, idxPageReplEn, tgtPageReplEn),
      Mux(idxWritesEven, page(r_btb_update.bits.pc), page(update_target)))
    writeBank(1, 2, Mux(idxWritesEven, tgtPageReplEn, idxPageReplEn),
      Mux(idxWritesEven, page(update_target), page(r_btb_update.bits.pc)))
    pageValid := pageValid | tgtPageReplEn | idxPageReplEn
  }

  io.resp.valid := (pageHit << 1)(Mux1H(idxHit, idxPages))
  io.resp.bits.taken := true.B
  io.resp.bits.target := Cat(pagesMasked(Mux1H(idxHit, tgtPages)), Mux1H(idxHit, tgts) << log2Up(coreInstBytes))
  io.resp.bits.entry := OHToUInt(idxHit)
  io.resp.bits.bridx := (if (fetchWidth > 1) Mux1H(idxHit, brIdx) else 0.U)
  io.resp.bits.mask := Cat((1.U << ~Mux(io.resp.bits.taken, ~io.resp.bits.bridx, 0.U))-1.U, 1.U)
  io.resp.bits.cfiType := Mux1H(idxHit, cfiType)

  // if multiple entries for same PC land in BTB, zap them
  when (PopCountAtLeast(idxHit, 2)) {
    isValid := isValid & ~idxHit
  }
  when (io.flush) {
    isValid := 0.U
  }

  if (btbParams.bhtParams.nonEmpty) {
    val bht = new BHT(btbParams.bhtParams.get)
    val isBranch = (idxHit & cfiType.map(_ === CFIType.branch).asUInt).orR
    val res = bht.get(io.req.bits.addr)
    when (io.bht_advance.valid) {
      bht.advanceHistory(io.bht_advance.bits.bht.taken)
    }
    when (io.bht_update.valid) {
      when (io.bht_update.bits.branch) {
        bht.updateTable(io.bht_update.bits.pc, io.bht_update.bits.prediction, io.bht_update.bits.taken)
        when (io.bht_update.bits.mispredict) {
          bht.updateHistory(io.bht_update.bits.pc, io.bht_update.bits.prediction, io.bht_update.bits.taken)
        }
      }.elsewhen (io.bht_update.bits.mispredict) {
        bht.resetHistory(io.bht_update.bits.prediction)
      }
    }
    when (!res.taken && isBranch) { io.resp.bits.taken := false.B }
    io.resp.bits.bht := res
  }
}
