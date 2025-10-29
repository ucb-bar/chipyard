package boom.v4.lsu

import chisel3._
import chisel3.util._

import org.chipsalliance.cde.config.Parameters
import freechips.rocketchip.rocket._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.subsystem.{CacheBlockBytes}
import freechips.rocketchip.diplomacy.{RegionType}
import freechips.rocketchip.util._

import boom.v4.common._
import boom.v4.exu.{BrResolutionInfo, Exception, CommitSignals}
import boom.v4.util._

class NBDTLB(instruction: Boolean, lgMaxSize: Int, cfg: TLBConfig)(implicit edge: TLEdgeOut, p: Parameters) extends BoomModule()(p) {
  require(!instruction)
  val io = IO(new Bundle {
    val req = Flipped(Vec(lsuWidth, Decoupled(new TLBReq(lgMaxSize))))
    val miss_rdy = Output(Bool())
    val resp = Output(Vec(lsuWidth, new TLBResp))
    val sfence = Input(Valid(new SFenceReq))
    val ptw = new TLBPTWIO
    val kill = Input(Bool())
  })

  class EntryData extends Bundle {
    val ppn = UInt(ppnBits.W)
    val u = Bool()
    val g = Bool()
    val ae = Bool()
    val sw = Bool()
    val sx = Bool()
    val sr = Bool()
    val pw = Bool()
    val px = Bool()
    val pr = Bool()
    val pal = Bool() // AMO logical
    val paa = Bool() // AMO arithmetic
    val eff = Bool() // get/put effects
    val c = Bool()
    val fragmented_superpage = Bool()
  }

  class Entry(val nSectors: Int, val superpage: Boolean, val superpageOnly: Boolean) extends Bundle {
    require(nSectors == 1 || !superpage)
    require(isPow2(nSectors))
    require(!superpageOnly || superpage)

    val level = UInt(log2Ceil(pgLevels).W)
    val tag = UInt(vpnBits.W)
    val data = Vec(nSectors, UInt(new EntryData().getWidth.W))
    val valid = Vec(nSectors, Bool())
    def entry_data = data.map(_.asTypeOf(new EntryData))

    private def sectorIdx(vpn: UInt) = vpn.extract(log2Ceil(nSectors)-1, 0)
    def getData(vpn: UInt) = OptimizationBarrier(data(sectorIdx(vpn)).asTypeOf(new EntryData))
    def sectorHit(vpn: UInt) = valid.orR && sectorTagMatch(vpn)
    def sectorTagMatch(vpn: UInt) = ((tag ^ vpn) >> log2Ceil(nSectors)) === 0.U
    def hit(vpn: UInt) = {
      if (superpage && usingVM) {
        var tagMatch = valid.head
        for (j <- 0 until pgLevels) {
          val base = vpnBits - (j + 1) * pgLevelBits
          val ignore = level < j.U || (superpageOnly && j == pgLevels - 1).B
          tagMatch = tagMatch && (ignore || tag(base + pgLevelBits - 1, base) === vpn(base + pgLevelBits - 1, base))
        }
        tagMatch
      } else {
        val idx = sectorIdx(vpn)
        valid(idx) && sectorTagMatch(vpn)
      }
    }
    def ppn(vpn: UInt) = {
      val data = getData(vpn)
      if (superpage && usingVM) {
        var res = data.ppn >> pgLevelBits*(pgLevels - 1)
        for (j <- 1 until pgLevels) {
          val ignore = (level < j.U) || (superpageOnly && j == pgLevels - 1).B
          res = Cat(res, (Mux(ignore, vpn, 0.U) | data.ppn)(vpnBits - j*pgLevelBits - 1, vpnBits - (j + 1)*pgLevelBits))
        }
        res
      } else {
        data.ppn
      }
    }

    def insert(tag: UInt, level: UInt, entry: EntryData) {
      this.tag := tag
      this.level := level.extract(log2Ceil(pgLevels - superpageOnly.toInt)-1, 0)

      val idx = sectorIdx(tag)
      valid(idx) := true.B
      data(idx) := entry.asUInt
    }

    def invalidate() { valid.foreach(_ := false.B) }
    def invalidateVPN(vpn: UInt) {
      if (superpage) {
         when (hit(vpn)) { invalidate() }
      } else {
        when (sectorTagMatch(vpn)) { valid(sectorIdx(vpn)) := false.B }

        // For fragmented superpage mappings, we assume the worst (largest)
        // case, and zap entries whose most-significant VPNs match
         when (((tag ^ vpn) >> (pgLevelBits * (pgLevels - 1))) === 0.U) {
           for ((v, e) <- valid zip entry_data)
             when (e.fragmented_superpage) { v := false.B }
         }
      }
    }
    def invalidateNonGlobal() {
       for ((v, e) <- valid zip entry_data)
         when (!e.g) { v := false.B }
    }
  }

  def widthMap[T <: Data](f: Int => T) = VecInit((0 until lsuWidth).map(f))

  val pageGranularityPMPs = pmpGranularity >= (1 << pgIdxBits)
  val sectored_entries = Reg(Vec((cfg.nSets * cfg.nWays) / cfg.nSectors, new Entry(cfg.nSectors, false, false)))
  val superpage_entries = Reg(Vec(cfg.nSuperpageEntries, new Entry(1, true, true)))
  val special_entry = (!pageGranularityPMPs).option(Reg(new Entry(1, true, false)))
  def ordinary_entries = sectored_entries ++ superpage_entries
  def all_entries = ordinary_entries ++ special_entry

  val s_ready :: s_request :: s_wait :: s_wait_invalidate :: Nil = Enum(4)
  val state = RegInit(s_ready)
  val r_refill_tag = Reg(UInt(vpnBits.W))
  val r_superpage_repl_addr = Reg(UInt(log2Ceil(superpage_entries.size).W))
  val r_sectored_repl_addr = Reg(UInt(log2Ceil(sectored_entries.size).W))
  val r_sectored_hit_addr = Reg(UInt(log2Ceil(sectored_entries.size).W))
  val r_sectored_hit = Reg(Bool())

  val priv = if (instruction) io.ptw.status.prv else io.ptw.status.dprv
  val priv_s = priv(0)
  val priv_uses_vm = priv <= PRV.S.U
  val vm_enabled = widthMap(w => usingVM.B && io.ptw.ptbr.mode(io.ptw.ptbr.mode.getWidth-1) && priv_uses_vm && !io.req(w).bits.passthrough)

  // share a single physical memory attribute checker (unshare if critical path)
  val vpn = widthMap(w => io.req(w).bits.vaddr(vaddrBits-1, pgIdxBits))
  val refill_ppn = io.ptw.resp.bits.pte.ppn(ppnBits-1, 0)
  val do_refill = usingVM.B && io.ptw.resp.valid
  val invalidate_refill = state.isOneOf(s_request /* don't care */, s_wait_invalidate) || io.sfence.valid
  val mpu_ppn = widthMap(w =>
                Mux(do_refill, refill_ppn,
                Mux(vm_enabled(w) && special_entry.nonEmpty.B, special_entry.map(_.ppn(vpn(w))).getOrElse(0.U), io.req(w).bits.vaddr >> pgIdxBits)))
  val mpu_physaddr = widthMap(w => Cat(mpu_ppn(w), io.req(w).bits.vaddr(pgIdxBits-1, 0)))
  val pmp = Seq.fill(lsuWidth) { Module(new PMPChecker(lgMaxSize)) }
  for (w <- 0 until lsuWidth) {
    pmp(w).io.addr := mpu_physaddr(w)
    pmp(w).io.size := io.req(w).bits.size
    pmp(w).io.pmp := (io.ptw.pmp: Seq[PMP])
    pmp(w).io.prv := Mux(usingVM.B && (do_refill || io.req(w).bits.passthrough /* PTW */), PRV.S.U, priv) // TODO should add separate bit to track PTW
  }
  val legal_address = widthMap(w => edge.manager.findSafe(mpu_physaddr(w)).reduce(_||_))
  def fastCheck(member: TLManagerParameters => Boolean, w: Int) =
    legal_address(w) && edge.manager.fastProperty(mpu_physaddr(w), member, (b:Boolean) => b.B)
  val cacheable = widthMap(w => fastCheck(_.supportsAcquireT, w) && (instruction || !usingDataScratchpad).B)
  val homogeneous = widthMap(w => TLBPageLookup(edge.manager.managers, xLen, p(CacheBlockBytes), BigInt(1) << pgIdxBits, 1 << lgMaxSize)(mpu_physaddr(w)).homogeneous)
  val prot_r   = widthMap(w => fastCheck(_.supportsGet, w) && pmp(w).io.r)
  val prot_w   = widthMap(w => fastCheck(_.supportsPutFull, w) && pmp(w).io.w)
  val prot_al  = widthMap(w => fastCheck(_.supportsLogical, w))
  val prot_aa  = widthMap(w => fastCheck(_.supportsArithmetic, w))
  val prot_x   = widthMap(w => fastCheck(_.executable, w) && pmp(w).io.x)
  val prot_eff = widthMap(w => fastCheck(Seq(RegionType.PUT_EFFECTS, RegionType.GET_EFFECTS) contains _.regionType, w))

  val sector_hits = widthMap(w => VecInit(sectored_entries.map(_.sectorHit(vpn(w)))))
  val superpage_hits = widthMap(w => VecInit(superpage_entries.map(_.hit(vpn(w)))))
  val hitsVec = widthMap(w => VecInit(all_entries.map(vm_enabled(w) && _.hit(vpn(w)))))
  val real_hits = widthMap(w => hitsVec(w).asUInt)
  val hits = widthMap(w => Cat(!vm_enabled(w), real_hits(w)))
  val ppn = widthMap(w => Mux1H(hitsVec(w) :+ !vm_enabled(w), all_entries.map(_.ppn(vpn(w))) :+ vpn(w)(ppnBits-1, 0)))

    // permission bit arrays
  when (do_refill) {
    val pte = io.ptw.resp.bits.pte
    val newEntry = Wire(new EntryData)
    newEntry.ppn := pte.ppn
    newEntry.c := cacheable(0)
    newEntry.u := pte.u
    newEntry.g := pte.g
    newEntry.ae := io.ptw.resp.bits.ae_final
    newEntry.sr := pte.sr()
    newEntry.sw := pte.sw()
    newEntry.sx := pte.sx()
    newEntry.pr := prot_r(0)
    newEntry.pw := prot_w(0)
    newEntry.px := prot_x(0)
    newEntry.pal := prot_al(0)
    newEntry.paa := prot_aa(0)
    newEntry.eff := prot_eff(0)
    newEntry.fragmented_superpage := io.ptw.resp.bits.fragmented_superpage

    when (special_entry.nonEmpty.B && !io.ptw.resp.bits.homogeneous) {
      special_entry.foreach(_.insert(r_refill_tag, io.ptw.resp.bits.level, newEntry))
    }.elsewhen (io.ptw.resp.bits.level < (pgLevels-1).U) {
      for ((e, i) <- superpage_entries.zipWithIndex) when (r_superpage_repl_addr === i.U) {
        e.insert(r_refill_tag, io.ptw.resp.bits.level, newEntry)
      }
    }.otherwise {
      val waddr = Mux(r_sectored_hit, r_sectored_hit_addr, r_sectored_repl_addr)
      for ((e, i) <- sectored_entries.zipWithIndex) when (waddr === i.U) {
        when (!r_sectored_hit) { e.invalidate() }
        e.insert(r_refill_tag, 0.U, newEntry)
      }
    }
  }

  val entries = widthMap(w => VecInit(all_entries.map(_.getData(vpn(w)))))
  val normal_entries = widthMap(w => VecInit(ordinary_entries.map(_.getData(vpn(w)))))
  val nPhysicalEntries = 1 + special_entry.size
  val ptw_ae_array = widthMap(w => Cat(false.B, entries(w).map(_.ae).asUInt))
  val priv_rw_ok   = widthMap(w => Mux(!priv_s || io.ptw.status.sum, entries(w).map(_.u).asUInt, 0.U) | Mux(priv_s, ~entries(w).map(_.u).asUInt, 0.U))
  val priv_x_ok    = widthMap(w => Mux(priv_s, ~entries(w).map(_.u).asUInt, entries(w).map(_.u).asUInt))
  val r_array      = widthMap(w => Cat(true.B, priv_rw_ok(w) & (entries(w).map(_.sr).asUInt | Mux(io.ptw.status.mxr, entries(w).map(_.sx).asUInt, 0.U))))
  val w_array      = widthMap(w => Cat(true.B, priv_rw_ok(w) & entries(w).map(_.sw).asUInt))
  val x_array      = widthMap(w => Cat(true.B, priv_x_ok(w)  & entries(w).map(_.sx).asUInt))
  val pr_array     = widthMap(w => Cat(Fill(nPhysicalEntries, prot_r(w))   , normal_entries(w).map(_.pr).asUInt) & ~ptw_ae_array(w))
  val pw_array     = widthMap(w => Cat(Fill(nPhysicalEntries, prot_w(w))   , normal_entries(w).map(_.pw).asUInt) & ~ptw_ae_array(w))
  val px_array     = widthMap(w => Cat(Fill(nPhysicalEntries, prot_x(w))   , normal_entries(w).map(_.px).asUInt) & ~ptw_ae_array(w))
  val eff_array    = widthMap(w => Cat(Fill(nPhysicalEntries, prot_eff(w)) , normal_entries(w).map(_.eff).asUInt))
  val c_array      = widthMap(w => Cat(Fill(nPhysicalEntries, cacheable(w)), normal_entries(w).map(_.c).asUInt))
  val paa_array    = widthMap(w => Cat(Fill(nPhysicalEntries, prot_aa(w))  , normal_entries(w).map(_.paa).asUInt))
  val pal_array    = widthMap(w => Cat(Fill(nPhysicalEntries, prot_al(w))  , normal_entries(w).map(_.pal).asUInt))
  val paa_array_if_cached = widthMap(w => paa_array(w) | Mux(usingAtomicsInCache.B, c_array(w), 0.U))
  val pal_array_if_cached = widthMap(w => pal_array(w) | Mux(usingAtomicsInCache.B, c_array(w), 0.U))
  val prefetchable_array  = widthMap(w => Cat((cacheable(w) && homogeneous(w)) << (nPhysicalEntries-1), normal_entries(w).map(_.c).asUInt))

  val misaligned = widthMap(w => (io.req(w).bits.vaddr & (UIntToOH(io.req(w).bits.size) - 1.U)).orR)
  val bad_va = widthMap(w => if (!usingVM || (minPgLevels == pgLevels && vaddrBits == vaddrBitsExtended)) false.B else vm_enabled(w) && {
    val nPgLevelChoices = pgLevels - minPgLevels + 1
    val minVAddrBits = pgIdxBits + minPgLevels * pgLevelBits
    (for (i <- 0 until nPgLevelChoices) yield {
      val mask = ((BigInt(1) << vaddrBitsExtended) - (BigInt(1) << (minVAddrBits + i * pgLevelBits - 1))).U
      val maskedVAddr = io.req(w).bits.vaddr & mask
      io.ptw.ptbr.additionalPgLevels === i.U && !(maskedVAddr === 0.U || maskedVAddr === mask)
    }).orR
  })

  val cmd_lrsc           = widthMap(w => usingAtomics.B && io.req(w).bits.cmd.isOneOf(M_XLR, M_XSC))
  val cmd_amo_logical    = widthMap(w => usingAtomics.B && isAMOLogical(io.req(w).bits.cmd))
  val cmd_amo_arithmetic = widthMap(w => usingAtomics.B && isAMOArithmetic(io.req(w).bits.cmd))
  val cmd_read           = widthMap(w => isRead(io.req(w).bits.cmd))
  val cmd_write          = widthMap(w => isWrite(io.req(w).bits.cmd))
  val cmd_write_perms    = widthMap(w => cmd_write(w) ||
    coreParams.haveCFlush.B && io.req(w).bits.cmd === M_FLUSH_ALL) // not a write, but needs write permissions

  val lrscAllowed = widthMap(w => Mux((usingDataScratchpad || usingAtomicsOnlyForIO).B, 0.U, c_array(w)))
  val ae_array = widthMap(w =>
    Mux(misaligned(w), eff_array(w), 0.U) |
    Mux(cmd_lrsc(w)  , ~lrscAllowed(w), 0.U))
  val ae_valid_array = widthMap(w => Cat(if (special_entry.isEmpty) true.B else Cat(true.B, Fill(special_entry.size, !do_refill)),
    Fill(normal_entries(w).size, true.B)))
  val ae_ld_array = widthMap(w => Mux(cmd_read(w), ae_array(w) | ~pr_array(w), 0.U))
  val ae_st_array = widthMap(w =>
    Mux(cmd_write_perms(w)   , ae_array(w) | ~pw_array(w), 0.U) |
    Mux(cmd_amo_logical(w)   , ~pal_array_if_cached(w), 0.U) |
    Mux(cmd_amo_arithmetic(w), ~paa_array_if_cached(w), 0.U))
  val must_alloc_array = widthMap(w =>
    Mux(cmd_amo_logical(w)   , ~paa_array(w), 0.U) |
    Mux(cmd_amo_arithmetic(w), ~pal_array(w), 0.U) |
    Mux(cmd_lrsc(w)          , ~0.U(pal_array(w).getWidth.W), 0.U))
  val ma_ld_array = widthMap(w => Mux(misaligned(w) && cmd_read(w) , ~eff_array(w), 0.U))
  val ma_st_array = widthMap(w => Mux(misaligned(w) && cmd_write(w), ~eff_array(w), 0.U))
  val pf_ld_array = widthMap(w => Mux(cmd_read(w)       , ~(r_array(w) | ptw_ae_array(w)), 0.U))
  val pf_st_array = widthMap(w => Mux(cmd_write_perms(w), ~(w_array(w) | ptw_ae_array(w)), 0.U))
  val pf_inst_array = widthMap(w => ~(x_array(w) | ptw_ae_array(w)))

  val tlb_hit = widthMap(w => real_hits(w).orR)
  val tlb_miss = widthMap(w => vm_enabled(w) && !bad_va(w) && !tlb_hit(w))

  val sectored_plru = new PseudoLRU(sectored_entries.size)
  val superpage_plru = new PseudoLRU(superpage_entries.size)
  for (w <- 0 until lsuWidth) {
    when (io.req(w).valid && vm_enabled(w)) {
      when (sector_hits(w).orR) { sectored_plru.access(OHToUInt(sector_hits(w))) }
      when (superpage_hits(w).orR) { superpage_plru.access(OHToUInt(superpage_hits(w))) }
    }
  }

  // Superpages create the possibility that two entries in the TLB may match.
  // This corresponds to a software bug, but we can't return complete garbage;
  // we must return either the old translation or the new translation.  This
  // isn't compatible with the Mux1H approach.  So, flush the TLB and report
  // a miss on duplicate entries.
  val multipleHits = widthMap(w => PopCountAtLeast(real_hits(w), 2))

  io.miss_rdy := state === s_ready
  for (w <- 0 until lsuWidth) {
    io.req(w).ready    := true.B
    io.resp(w).gpa     := DontCare
    io.resp(w).gpa_is_pte := DontCare
    io.resp(w).gf.ld   := false.B
    io.resp(w).gf.st   := false.B
    io.resp(w).gf.inst := false.B
    io.resp(w).pf.ld   := (bad_va(w) && cmd_read(w)) || (pf_ld_array(w) & hits(w)).orR
    io.resp(w).pf.st   := (bad_va(w) && cmd_write_perms(w)) || (pf_st_array(w) & hits(w)).orR
    io.resp(w).pf.inst := bad_va(w) || (pf_inst_array(w) & hits(w)).orR
    io.resp(w).ae.ld   := (ae_valid_array(w) & ae_ld_array(w) & hits(w)).orR
    io.resp(w).ae.st   := (ae_valid_array(w) & ae_st_array(w) & hits(w)).orR
    io.resp(w).ae.inst := (ae_valid_array(w) & ~px_array(w)   & hits(w)).orR
    io.resp(w).ma.ld   := (ma_ld_array(w) & hits(w)).orR
    io.resp(w).ma.st   := (ma_st_array(w) & hits(w)).orR
    io.resp(w).ma.inst := false.B // this is up to the pipeline to figure out
    io.resp(w).cacheable    := (c_array(w) & hits(w)).orR
    io.resp(w).must_alloc   := (must_alloc_array(w) & hits(w)).orR
    io.resp(w).prefetchable := (prefetchable_array(w) & hits(w)).orR && edge.manager.managers.forall(m => !m.supportsAcquireB || m.supportsHint).B
    io.resp(w).miss  := do_refill || tlb_miss(w) || multipleHits(w)
    io.resp(w).paddr := Cat(ppn(w), io.req(w).bits.vaddr(pgIdxBits-1, 0))
    io.resp(w).size := io.req(w).bits.size
    io.resp(w).cmd := io.req(w).bits.cmd
  }

  io.ptw.customCSRs := DontCare
  io.ptw.req.valid := state === s_request
  io.ptw.req.bits := DontCare
  io.ptw.req.bits.valid := !io.kill
  io.ptw.req.bits.bits.addr := r_refill_tag

  if (usingVM) {
    val sfence = io.sfence.valid
    for (w <- 0 until lsuWidth) {
      when (io.req(w).fire && tlb_miss(w) && state === s_ready) {
        state := s_request
        r_refill_tag := vpn(w)

        r_superpage_repl_addr := replacementEntry(superpage_entries, superpage_plru.way)
        r_sectored_repl_addr  := replacementEntry(sectored_entries, sectored_plru.way)
        r_sectored_hit_addr   := OHToUInt(sector_hits(w))
        r_sectored_hit        := sector_hits(w).orR
      }
    }
    when (state === s_request) {
      when (sfence) { state := s_ready }
      when (io.ptw.req.ready) { state := Mux(sfence, s_wait_invalidate, s_wait) }
      when (io.kill) { state := s_ready }
    }
    when (state === s_wait && sfence) {
      state := s_wait_invalidate
    }
    when (io.ptw.resp.valid) {
      state := s_ready
    }

    when (sfence) {
      for (w <- 0 until lsuWidth) {
        assert(!io.sfence.bits.rs1 || (io.sfence.bits.addr >> pgIdxBits) === vpn(w))
        for (e <- all_entries) {
          when (io.sfence.bits.rs1) { e.invalidateVPN(vpn(w)) }
          .elsewhen (io.sfence.bits.rs2) { e.invalidateNonGlobal() }
          .otherwise { e.invalidate() }
        }
      }
    }
    when (multipleHits.orR || reset.asBool) {
      all_entries.foreach(_.invalidate())
    }
  }

  def replacementEntry(set: Seq[Entry], alt: UInt) = {
    val valids = set.map(_.valid.orR).asUInt
    Mux(valids.andR, alt, PriorityEncoder(~valids))
  }


}
