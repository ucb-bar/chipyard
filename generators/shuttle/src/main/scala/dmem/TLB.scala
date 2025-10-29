// See LICENSE.Berkeley for license details.

package shuttle.dmem

import chisel3._
import chisel3.util._

import org.chipsalliance.cde.config.{Field, Parameters}
import freechips.rocketchip.subsystem.CacheBlockBytes
import freechips.rocketchip.diplomacy.RegionType
import freechips.rocketchip.tile.{CoreModule, CoreBundle}
import freechips.rocketchip.tilelink._
import freechips.rocketchip.rocket._
import freechips.rocketchip.util._
import freechips.rocketchip.util.property
import freechips.rocketchip.devices.debug.DebugModuleKey
import chisel3.experimental.SourceInfo

class ShuttleDTLBReq(lgMaxSize: Int)(implicit p: Parameters) extends CoreBundle()(p) {
  val vaddr = UInt(vaddrBitsExtended.W)
  val size = UInt(log2Ceil(lgMaxSize + 1).W)
  val cmd  = Bits(M_SZ.W)
  val prv = UInt(PRV.SZ.W)
}

class ShuttleDTLBExceptions extends Bundle {
  val ld = Bool()
  val st = Bool()
}

class ShuttleDTLBResp(implicit p: Parameters) extends CoreBundle()(p) {
  val miss = Bool()
  val paddr = UInt(paddrBits.W)
  val pf = new TLBExceptions
  val ae = new TLBExceptions
  val ma = new TLBExceptions
}


class ShuttleDTLB(ports: Int, lgMaxSize: Int, cfg: TLBConfig)(implicit edge: TLEdgeOut, p: Parameters) extends CoreModule()(p) {
  val io = IO(new Bundle {
    val req = Vec(ports, Flipped(Decoupled(new ShuttleDTLBReq(lgMaxSize))) )
    val resp = Vec(ports, Output(new ShuttleDTLBResp))
    val sfence = Flipped(Valid(new SFenceReq))
    val ptw = new TLBPTWIO
  })
  io.ptw.customCSRs := DontCare

  require(nPMPs == 0)
  require(!usingHypervisor)
  require(usingVM)

  /** TLB Entry */
  val sectored_entries = Reg(Vec(cfg.nSets, Vec(cfg.nWays / cfg.nSectors, new TLBEntry(cfg.nSectors, false, false))))
  /** Superpage Entry */
  val superpage_entries = Reg(Vec(cfg.nSuperpageEntries, new TLBEntry(1, true, true)))

  val s_ready :: s_request :: s_wait :: s_wait_invalidate :: Nil = Enum(4)
  val state = RegInit(s_ready)
  // use vpn as refill_tag
  val r_refill_tag = Reg(UInt(vpnBits.W))
  val r_superpage_repl_addr = Reg(UInt(log2Ceil(superpage_entries.size).W))
  val r_sectored_repl_addr = Reg(UInt(log2Ceil(sectored_entries.head.size).W))
  val r_sectored_hit = Reg(Valid(UInt(log2Ceil(sectored_entries.head.size).W)))
  val r_superpage_hit = Reg(Valid(UInt(log2Ceil(superpage_entries.size).W)))

  // share a single physical memory attribute checker (unshare if critical path)
  val refill_ppn = io.ptw.resp.bits.pte.ppn(ppnBits-1, 0)
  /** refill signal */
  val do_refill = io.ptw.resp.valid
  /** sfence invalidate refill */
  val invalidate_refill = state.isOneOf(s_request /* don't care */, s_wait_invalidate) || io.sfence.valid

  io.ptw.req.valid := state === s_request
  io.ptw.req.bits.valid := true.B
  io.ptw.req.bits.bits.addr := r_refill_tag
  io.ptw.req.bits.bits.vstage1 := false.B
  io.ptw.req.bits.bits.stage2 := false.B
  io.ptw.req.bits.bits.need_gpa := false.B

  val sfence = io.sfence.valid

  // Handle SFENCE.VMA when send request to PTW.
  // SFENCE.VMA    io.ptw.req.ready     kill
  //       ?                 ?            1
  //       0                 0            0
  //       0                 1            0 -> s_wait
  //       1                 0            0 -> s_wait_invalidate
  //       1                 0            0 -> s_ready
  when (state === s_request) {
    // SFENCE.VMA will kill TLB entries based on rs1 and rs2. It will take 1 cycle.
    when (sfence) { state := s_ready }
    // here should be io.ptw.req.fire, but assert(io.ptw.req.ready === true.B)
    // fire -> s_wait
    when (io.ptw.req.ready) { state := Mux(sfence, s_wait_invalidate, s_wait) }
  }
  // sfence in refill will results in invalidate
  when (state === s_wait && sfence) {
    state := s_wait_invalidate
  }
  // after CPU acquire response, go back to s_ready.
  when (io.ptw.resp.valid) {
    state := s_ready
  }

  // SFENCE processing logic.
  when (sfence) {
    assert(!io.sfence.bits.rs1 || io.sfence.bits.addr(vaddrBits-1, pgIdxBits) === io.req.last.bits.vaddr(vaddrBits-1, pgIdxBits))
    def all_real_entries = sectored_entries.flatten ++ superpage_entries
    for (e <- all_real_entries) {
      val hv = false.B
      val hg = false.B
      when (!hg && io.sfence.bits.rs1) { e.invalidateVPN(io.req.last.bits.vaddr(vaddrBits-1,pgIdxBits), hv) }
        .elsewhen (!hg && io.sfence.bits.rs2) { e.invalidateNonGlobal(hv) }
        .otherwise { e.invalidate(hv || hg) }
    }
  }


  for (i <- (0 until ports).reverse) {

    val vpn = io.req(i).bits.vaddr(vaddrBits-1, pgIdxBits)
    /** index for sectored_Entry */
    val memIdx = vpn.extract(cfg.nSectors.log2 + cfg.nSets.log2 - 1, cfg.nSectors.log2)
    def ordinary_entries = sectored_entries(memIdx) ++ superpage_entries
    def all_entries = ordinary_entries
    def all_real_entries = sectored_entries.flatten ++ superpage_entries


    /** privilege mode */
    val priv = io.req(i).bits.prv
    val priv_s = priv(0)
    // user mode and supervisor mode
    val priv_uses_vm = priv <= PRV.S.U
    val satp = io.ptw.ptbr
    val stage1_en = satp.mode(satp.mode.getWidth-1)
    val vm_enabled = stage1_en && priv_uses_vm

    val mpu_ppn = Mux(do_refill, refill_ppn, io.req(i).bits.vaddr >> pgIdxBits)
    val mpu_physaddr = Cat(mpu_ppn, io.req(i).bits.vaddr(pgIdxBits-1, 0))
    val mpu_priv = Mux[UInt](do_refill, PRV.S.U, Cat(io.ptw.status.debug, priv))

    // PMA
    val pma = Module(new PMAChecker(edge.manager)(p))
    pma.io.paddr := mpu_physaddr
    // todo: using DataScratchpad doesn't support cacheable.
    val cacheable = pma.io.resp.cacheable
    val deny_access_to_debug = mpu_priv <= PRV.M.U && p(DebugModuleKey).map(dmp => dmp.address.contains(mpu_physaddr)).getOrElse(false.B)
    val prot_r = pma.io.resp.r && !deny_access_to_debug
    val prot_w = pma.io.resp.w && !deny_access_to_debug
    val prot_pp = pma.io.resp.pp
    val prot_al = pma.io.resp.al
    val prot_aa = pma.io.resp.aa
    val prot_x = pma.io.resp.x && !deny_access_to_debug
    val prot_eff = pma.io.resp.eff

    // hit check
    val sector_hits = sectored_entries(memIdx).map(_.sectorHit(vpn, false.B))
    val superpage_hits = superpage_entries.map(_.hit(vpn, false.B))
    val hitsVec = all_entries.map(vm_enabled && _.hit(vpn, false.B))
    val real_hits = hitsVec.asUInt
    val hits = Cat(!vm_enabled, real_hits)

    // use ptw response to refill
    // permission bit arrays
    if (i == 0) { when (do_refill) {
      val pte = io.ptw.resp.bits.pte
      val refill_v = false.B
      val newEntry = Wire(new TLBEntryData)
      newEntry.ppn := pte.ppn
      newEntry.c := cacheable
      newEntry.u := pte.u
      newEntry.g := pte.g && pte.v
      newEntry.ae_ptw := io.ptw.resp.bits.ae_ptw
      newEntry.ae_final := io.ptw.resp.bits.ae_final
      newEntry.ae_stage2 := false.B
      newEntry.pf := io.ptw.resp.bits.pf
      newEntry.gf := io.ptw.resp.bits.gf
      newEntry.hr := io.ptw.resp.bits.hr
      newEntry.hw := io.ptw.resp.bits.hw
      newEntry.hx := io.ptw.resp.bits.hx
      newEntry.sr := pte.sr()
      newEntry.sw := pte.sw()
      newEntry.sx := pte.sx()
      newEntry.pr := prot_r
      newEntry.pw := prot_w
      newEntry.px := prot_x
      newEntry.ppp := prot_pp
      newEntry.pal := prot_al
      newEntry.paa := prot_aa
      newEntry.eff := prot_eff
      newEntry.fragmented_superpage := io.ptw.resp.bits.fragmented_superpage
      // refill special_entry
      when (io.ptw.resp.bits.level < (pgLevels-1).U) {
        val waddr = r_superpage_repl_addr
        for ((e, i) <- superpage_entries.zipWithIndex) when (r_superpage_repl_addr === i.U) {
          e.insert(r_refill_tag, refill_v, io.ptw.resp.bits.level, newEntry)
          when (invalidate_refill) { e.invalidate() }
        }
        // refill sectored_hit
      }.otherwise {
        val r_memIdx = r_refill_tag.extract(cfg.nSectors.log2 + cfg.nSets.log2 - 1, cfg.nSectors.log2)
        val waddr = Mux(r_sectored_hit.valid, r_sectored_hit.bits, r_sectored_repl_addr)
        for ((e, i) <- sectored_entries(r_memIdx).zipWithIndex) when (waddr === i.U) {
          when (!r_sectored_hit.valid) { e.invalidate() }
          e.insert(r_refill_tag, refill_v, 0.U, newEntry)
          when (invalidate_refill) { e.invalidate() }
        }
      }
    }}

    // get all entries data.
    val entries = all_entries.map(_.getData(vpn))
    val normal_entries = entries.take(ordinary_entries.size)
    // parallel query PPN from [[all_entries]], if VM not enabled return VPN instead
    val ppn = Mux1H(hitsVec :+ !vm_enabled, (all_entries zip entries).map{ case (entry, data) => entry.ppn(vpn, data) } :+ vpn(ppnBits-1, 0))

    val nPhysicalEntries = 1
    // generally PTW misaligned load exception.
    val ptw_ae_array = Cat(false.B, entries.map(_.ae_ptw).asUInt)
    val final_ae_array = Cat(false.B, entries.map(_.ae_final).asUInt)
    val ptw_pf_array = Cat(false.B, entries.map(_.pf).asUInt)
    val ptw_gf_array = Cat(false.B, entries.map(_.gf).asUInt)
    val sum = io.ptw.status.sum
    // if in hypervisor/machine mode, cannot read/write user entries.
    // if in superviosr/user mode, "If the SUM bit in the sstatus register is set, supervisor mode software may also access pages with U=1.(from spec)"
    val priv_rw_ok = Mux(!priv_s || sum, entries.map(_.u).asUInt, 0.U) | Mux(priv_s, ~entries.map(_.u).asUInt, 0.U)
    // if in hypervisor/machine mode, other than user pages, all pages are executable.
    // if in superviosr/user mode, only user page can execute.
    val priv_x_ok = Mux(priv_s, ~entries.map(_.u).asUInt, entries.map(_.u).asUInt)
    val mxr = io.ptw.status.mxr
    // "The vsstatus field MXR, which makes execute-only pages readable, only overrides VS-stage page protection.(from spec)"
    val r_array = Cat(true.B, (priv_rw_ok & (entries.map(_.sr).asUInt | Mux(mxr, entries.map(_.sx).asUInt, 0.U))))
    val w_array = Cat(true.B, (priv_rw_ok & entries.map(_.sw).asUInt))
    val x_array = Cat(true.B, (priv_x_ok & entries.map(_.sx).asUInt))
    // These array is for each TLB entries.
    // user mode can read: PMA OK, TLB OK, AE OK
    val pr_array = Cat(Fill(nPhysicalEntries, prot_r), normal_entries.map(_.pr).asUInt) & ~(ptw_ae_array | final_ae_array)
    // user mode can write: PMA OK, TLB OK, AE OK
    val pw_array = Cat(Fill(nPhysicalEntries, prot_w), normal_entries.map(_.pw).asUInt) & ~(ptw_ae_array | final_ae_array)
    // user mode can write: PMA OK, TLB OK, AE OK
    val px_array = Cat(Fill(nPhysicalEntries, prot_x), normal_entries.map(_.px).asUInt) & ~(ptw_ae_array | final_ae_array)
    // put effect
    val eff_array = Cat(Fill(nPhysicalEntries, prot_eff), normal_entries.map(_.eff).asUInt)
    // cacheable
    val c_array = Cat(Fill(nPhysicalEntries, cacheable), normal_entries.map(_.c).asUInt)
    // put partial
    val ppp_array = Cat(Fill(nPhysicalEntries, prot_pp), normal_entries.map(_.ppp).asUInt)
    // atomic arithmetic
    val paa_array = Cat(Fill(nPhysicalEntries, prot_aa), normal_entries.map(_.paa).asUInt)
    // atomic logic
    val pal_array = Cat(Fill(nPhysicalEntries, prot_al), normal_entries.map(_.pal).asUInt)
    val ppp_array_if_cached = ppp_array | c_array
    val paa_array_if_cached = paa_array | c_array
    val pal_array_if_cached = pal_array | c_array


    // vaddr misaligned: vaddr[1:0]=b00
    val misaligned = (io.req(i).bits.vaddr & (UIntToOH(io.req(i).bits.size) - 1.U)).orR
    def badVA: Bool = {
      val additionalPgLevels = (satp).additionalPgLevels
      val extraBits = 0
      val signed = true
      val nPgLevelChoices = pgLevels - minPgLevels + 1
      val minVAddrBits = pgIdxBits + minPgLevels * pgLevelBits + extraBits
        (for (j <- 0 until nPgLevelChoices) yield {
          val mask = ((BigInt(1) << vaddrBitsExtended) - (BigInt(1) << (minVAddrBits + j * pgLevelBits - signed.toInt))).U
          val maskedVAddr = io.req(i).bits.vaddr & mask
          additionalPgLevels === j.U && !(maskedVAddr === 0.U || signed.B && maskedVAddr === mask)
        }).orR
    }
    val bad_va =
      if (!usingVM || (minPgLevels == pgLevels && vaddrBits == vaddrBitsExtended)) false.B
      else vm_enabled && stage1_en && badVA

    val cmd_lrsc = usingAtomics.B && io.req(i).bits.cmd.isOneOf(M_XLR, M_XSC)
    val cmd_amo_logical = usingAtomics.B && isAMOLogical(io.req(i).bits.cmd)
    val cmd_amo_arithmetic = usingAtomics.B && isAMOArithmetic(io.req(i).bits.cmd)
    val cmd_put_partial = io.req(i).bits.cmd === M_PWR
    val cmd_read = isRead(io.req(i).bits.cmd)
    val cmd_write = isWrite(io.req(i).bits.cmd)
    val cmd_write_perms = cmd_write ||
      io.req(i).bits.cmd.isOneOf(M_FLUSH_ALL, M_WOK) // not a write, but needs write permissions

    val lrscAllowed = Mux((usingDataScratchpad || usingAtomicsOnlyForIO).B, 0.U, c_array)
    val ae_array =
      Mux(misaligned, eff_array, 0.U) |
      Mux(cmd_lrsc, ~lrscAllowed, 0.U)

    // access exception needs SoC information from PMA
    val ae_ld_array = Mux(cmd_read, ae_array | ~pr_array, 0.U)
    val ae_st_array =
      Mux(cmd_write_perms, ae_array | ~pw_array, 0.U) |
      Mux(cmd_put_partial, ~ppp_array_if_cached, 0.U) |
      Mux(cmd_amo_logical, ~pal_array_if_cached, 0.U) |
      Mux(cmd_amo_arithmetic, ~paa_array_if_cached, 0.U)
    val must_alloc_array =
      Mux(cmd_put_partial, ~ppp_array, 0.U) |
      Mux(cmd_amo_logical, ~pal_array, 0.U) |
      Mux(cmd_amo_arithmetic, ~paa_array, 0.U) |
      Mux(cmd_lrsc, ~0.U(pal_array.getWidth.W), 0.U)
    val pf_ld_array = Mux(cmd_read, ((~r_array & ~ptw_ae_array) | ptw_pf_array) & ~ptw_gf_array, 0.U)
    val pf_st_array = Mux(cmd_write_perms, ((~w_array & ~ptw_ae_array) | ptw_pf_array) & ~ptw_gf_array, 0.U)

    val tlb_hit = (real_hits).orR
    // leads to s_request
    val tlb_miss = vm_enabled && !bad_va && !tlb_hit

    val sectored_plru = new SetAssocLRU(cfg.nSets, sectored_entries.head.size, "plru")
    val superpage_plru = new PseudoLRU(superpage_entries.size)
    when (io.req(i).valid && vm_enabled) {
      // replace
      when (sector_hits.orR) { sectored_plru.access(memIdx, OHToUInt(sector_hits)) }
      when (superpage_hits.orR) { superpage_plru.access(OHToUInt(superpage_hits)) }
    }

    // Superpages create the possibility that two entries in the TLB may match.
    // This corresponds to a software bug, but we can't return complete garbage;
    // we must return either the old translation or the new translation.  This
    // isn't compatible with the Mux1H approach.  So, flush the TLB and report
    // a miss on duplicate entries.
    val multipleHits = PopCountAtLeast(real_hits, 2)

    // only pull up req.ready when this is s_ready state.
    io.req(i).ready := state === s_ready
    // page fault
    io.resp(i).pf.ld := (bad_va && cmd_read) || (pf_ld_array & hits).orR
    io.resp(i).pf.st := (bad_va && cmd_write_perms) || (pf_st_array & hits).orR
    io.resp(i).pf.inst := DontCare
    // access exception
    io.resp(i).ae.ld := (ae_ld_array & hits).orR
    io.resp(i).ae.st := (ae_st_array & hits).orR
    io.resp(i).ae.inst := DontCare
    // misaligned
    io.resp(i).ma.ld := misaligned && cmd_read
    io.resp(i).ma.st := misaligned && cmd_write
    io.resp(i).ma.inst := DontCare
    io.resp(i).miss := do_refill || tlb_miss || multipleHits
    io.resp(i).paddr := Cat(ppn, io.req(i).bits.vaddr(pgIdxBits-1, 0))

    // this is [[s_ready]]
    // handle miss/hit at the first cycle.
    // if miss, request PTW(L2TLB).
    when (io.req(i).fire && tlb_miss) {
      state := s_request
      r_refill_tag := vpn
      r_superpage_repl_addr := replacementEntry(superpage_entries, superpage_plru.way)
      r_sectored_repl_addr := replacementEntry(sectored_entries(memIdx), sectored_plru.way(memIdx))
      r_sectored_hit.valid := sector_hits.orR
      r_sectored_hit.bits := OHToUInt(sector_hits)
      r_superpage_hit.valid := superpage_hits.orR
      r_superpage_hit.bits := OHToUInt(superpage_hits)
    }
    when ((io.req(i).valid && multipleHits) || reset.asBool) {
      all_real_entries.foreach(_.invalidate())
    }
  }
  /** Decides which entry to be replaced
    *
    * If there is a invalid entry, replace it with priorityencoder;
    * if not, replace the alt entry
    *
    * @return mask for TLBEntry replacement
    */
  def replacementEntry(set: Seq[TLBEntry], alt: UInt) = {
    val valids = set.map(_.valid.orR).asUInt
    Mux(valids.andR, alt, PriorityEncoder(~valids))
  }
}
