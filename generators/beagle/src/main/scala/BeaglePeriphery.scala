package beagle

import chisel3._
import chisel3.core.{withReset, dontTouch}
import chisel3.experimental.MultiIOModule
import chisel3.util._

import freechips.rocketchip.config.{Field, Parameters}
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.devices.tilelink._
import freechips.rocketchip.regmapper._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.util._

import hbwif.tilelink.{HbwifNumLanes, BuildHbwif, HbwifTLKey}
import hbwif.{Differential}

import testchipip._

case object PeripheryBeagleKey extends Field[BeagleParams]
case object BeaglePipelineResetDepth extends Field[Int]
case object CacheBlockStriping extends Field[Int]
case object LbwifBitWidth extends Field[Int]

case class BeagleParams(
  scrAddress: Int,
  uncoreClkDivBits: Int = 8,
  bhClkDivBits: Int = 8,
  rsClkDivBits: Int = 8,
  lbwifClkDivBits: Int = 8,
  uncoreClkDivInit: Int = 4,
  bhClkDivInit: Int = 4,
  rsClkDivInit: Int = 4,
  lbwifClkDivInit: Int = 4
)

/**
 * IO out from the MMIO components
 */
trait HasBeagleTopBundleContents extends Bundle
{
  implicit val p: Parameters

  val boot          = Input(Bool())
  val rst_async     = Input(Bool())

  val uncore_clk_divisor = Output(UInt(p(PeripheryBeagleKey).uncoreClkDivBits.W))
  val bh_clk_divisor     = Output(UInt(p(PeripheryBeagleKey).bhClkDivBits.W))
  val rs_clk_divisor     = Output(UInt(p(PeripheryBeagleKey).rsClkDivBits.W))
  val bh_clk_out_divisor = Output(UInt(p(PeripheryBeagleKey).bhClkDivBits.W))
  val rs_clk_out_divisor = Output(UInt(p(PeripheryBeagleKey).rsClkDivBits.W))
  val lbwif_clk_divisor  = Output(UInt(p(PeripheryBeagleKey).lbwifClkDivBits.W))

  val uncore_clk_pass_sel = Output(Bool()) // used to choose the undivided uncore clock or divided

  val hbwif_rsts   = Output(Vec(p(HbwifNumLanes), Bool()))
  val switcher_sel = Output(Bool())
}

/**
 * MMIO controlled components
 */
trait HasBeagleTopModuleContents extends MultiIOModule with HasRegMap
{
  val io: HasBeagleTopBundleContents
  implicit val p: Parameters
  def params: BeagleParams
  def c = params

  // main clock divider regs
  val r_uncore_clk_divisor = withReset(io.rst_async) {
    Module(new AsyncResetRegVec(w = c.uncoreClkDivBits, init = c.uncoreClkDivInit))
  }

  val r_bh_clk_divisor = withReset(io.rst_async) {
    Module(new AsyncResetRegVec(w = c.bhClkDivBits, init = c.bhClkDivInit))
  }

  val r_rs_clk_divisor = withReset(io.rst_async) {
    Module(new AsyncResetRegVec(w = c.rsClkDivBits, init = c.rsClkDivInit))
  }

  // output clock divider regs
  val r_bh_clk_out_divisor = withReset(io.rst_async) {
    Module(new AsyncResetRegVec(w = c.bhClkDivBits, init = c.bhClkDivInit))
  }

  val r_rs_clk_out_divisor = withReset(io.rst_async) {
    Module(new AsyncResetRegVec(w = c.rsClkDivBits, init = c.rsClkDivInit))
  }

  // lbwif clock divider reg
  val r_lbwif_clk_divisor = withReset(io.rst_async) {
    Module(new AsyncResetRegVec(w = c.lbwifClkDivBits, init = c.lbwifClkDivInit))
  }

  // uncore select bit reg
  val r_uncore_clk_pass_sel = withReset(io.rst_async) {
    Module(new AsyncResetRegVec(w = 1, init = 0))
  }

  // hbwif reset signals
  val r_hbwif_rsts = Seq.fill(p(HbwifNumLanes)) {
    withReset(io.rst_async) {
      Module(new AsyncResetRegVec(w = 1, init = 1))
    }
  }

  // connect hbwif or lbwif to memory system
  val r_switcher_sel = withReset(io.rst_async) {
    Module(new AsyncResetRegVec(w = 1, init = 1))
  }

  // connect to io out of scr
  io.uncore_clk_divisor  := r_uncore_clk_divisor.io.q
  io.bh_clk_divisor      := r_bh_clk_divisor.io.q
  io.rs_clk_divisor      := r_rs_clk_divisor.io.q
  io.bh_clk_out_divisor  := r_bh_clk_out_divisor.io.q
  io.rs_clk_out_divisor  := r_rs_clk_out_divisor.io.q
  io.lbwif_clk_divisor   := r_lbwif_clk_divisor.io.q
  io.uncore_clk_pass_sel := r_uncore_clk_pass_sel.io.q
  io.hbwif_rsts          := VecInit(r_hbwif_rsts.map(_.io.q))
  io.switcher_sel        := r_switcher_sel.io.q

  // connect to mmio

  // needed to space out the mmio correctly
  require(c.uncoreClkDivBits == 8)
  require(    c.bhClkDivBits == 8)
  require(    c.rsClkDivBits == 8)
  require( c.lbwifClkDivBits == 8)

  regmap(
    0x08 -> Seq(RegField.r(1, RegReadFn(io.boot))),
    0x0c -> Seq(RegField.rwReg(1, r_switcher_sel.io)),
    0x10 -> r_hbwif_rsts.map(rst => RegField.rwReg(1, rst.io)),
    0x20 -> Seq(RegField.rwReg(c.uncoreClkDivBits,  r_uncore_clk_divisor.io)),
    0x30 -> Seq(RegField.rwReg(    c.bhClkDivBits,      r_bh_clk_divisor.io)),
    0x40 -> Seq(RegField.rwReg(    c.rsClkDivBits,      r_rs_clk_divisor.io)),
    0x50 -> Seq(RegField.rwReg(    c.bhClkDivBits,  r_bh_clk_out_divisor.io)),
    0x60 -> Seq(RegField.rwReg(    c.rsClkDivBits,  r_rs_clk_out_divisor.io)),
    0x70 -> Seq(RegField.rwReg( c.lbwifClkDivBits,   r_lbwif_clk_divisor.io)),
    0x80 -> Seq(RegField.rwReg(                 1, r_uncore_clk_pass_sel.io))
  )
}

/**
 * TL Module used to connect MMIO registers
 */
class TLBeagle(w: Int, c: BeagleParams)(implicit p: Parameters)
  extends TLRegisterRouter(c.scrAddress, "beagle-scr", Seq("ucb-bar,beagle-scr"), interrupts = 0, beatBytes = w)(
    new TLRegBundle(c, _) with HasBeagleTopBundleContents)(
    new TLRegModule(c, _, _) with HasBeagleTopModuleContents)

// ------------------------------------------------------------------------------------------------------------------------------------

trait HasPeripheryBeagle
{
  this: BaseSubsystem =>

  val scrParams = p(PeripheryBeagleKey)

  val scrName = Some("beagle_scr")
  val scr = LazyModule(new TLBeagle(pbus.beatBytes, scrParams)).suggestName(scrName)
  pbus.toVariableWidthSlave(scrName) { scr.node := TLBuffer() }

  // setup boot scratch pad
  val boot_scratchpad = LazyModule(new TLRAM(
    address = AddressSet(0x50000000, 0xffff),
    cacheable = false,
    executable = true,
    beatBytes = 8))
  val boot_scratchpad_buffer = LazyModule(new TLBuffer())
  pbus.toVariableWidthSlave(Some("boot_scratchpad")) { boot_scratchpad.node := boot_scratchpad_buffer.node := TLBuffer() }

  val extMem = p(ExtMem).get
  val extParams = extMem.master

  // setup the hbwif
  val lanesPerMemoryChannel = if (p(HbwifNumLanes)/extMem.nMemoryChannels == 0) 1 else p(HbwifNumLanes)/extMem.nMemoryChannels
  val base = AddressSet.misaligned(extParams.base, extParams.size)
  val filters = (0 until extMem.nMemoryChannels).map { case id =>
    AddressSet(id * p(CacheBlockBytes) * p(CacheBlockStriping), ~((extMem.nMemoryChannels-1) * p(CacheBlockBytes) * p(CacheBlockStriping)))
  }
  val addresses = filters.map{ case filt =>
    base.flatMap(_.intersect(filt))
  }

  // switch between lbwif and hbwif
  val switcher = LazyModule(new TLSwitcher(
    inPortN = extMem.nMemoryChannels,
    outPortN = Seq(extMem.nMemoryChannels, 1),
    address = addresses,
    beatBytes = extParams.beatBytes,
    lineBytes = p(CacheBlockBytes),
    idBits = 13))
  switcher.innode :*= mbus.coupleTo("switcherPort") { TLBuffer() :*= _ }

  val hbwif = LazyModule(p(BuildHbwif)(p))

  val memSplitXbar = (0 until extMem.nMemoryChannels).map{_ => LazyModule(new TLXbar)}
  for (i <- 0 until p(HbwifNumLanes)) {
    pbus.toVariableWidthSlave(Some(s"hbwif_config$i")) { hbwif.configNodes(i) := TLBuffer() := TLBuffer() := TLWidthWidget(pbus.beatBytes) }
    /* Add filters dividing numMemChannels into numHbiwfLanes */
    val lane = i
    val mbusIndex = i/lanesPerMemoryChannel
    val smallerMask = ~BigInt((p(HbwifNumLanes)-1) * p(CacheBlockBytes))
    val smallerOffset = i
    hbwif.managerNode := memSplitXbar(mbusIndex).node
    // Every lanesPerMemoryChannel hbwif lane we also connect the new xbar to the switcher
    if ((i % lanesPerMemoryChannel) == 0) {
      memSplitXbar(mbusIndex).node := TLBuffer() := switcher.outnodes(0)
    }
  }

  // setup the backup serdes (otherwise known as the lbwif)
  val memParams = TLManagerParameters(
    address = AddressSet.misaligned(extParams.base, extParams.size),
    resources = (new MemoryDevice).reg,
    regionType = RegionType.UNCACHED, // cacheable
    executable = true,
    supportsGet        = TransferSizes(1, p(CacheBlockBytes)),
    supportsPutFull    = TransferSizes(1, p(CacheBlockBytes)),
    supportsPutPartial = TransferSizes(1, p(CacheBlockBytes)),
    fifoId = Some(0))
  val ctrlParams = TLClientParameters(
    name = "tl_serdes_control",
    sourceId = IdRange(0, (1 << 4)),
    requestFifo = true)

  val lbwif = LazyModule(new TLSerdesser(
    w=p(LbwifBitWidth),
    clientParams=ctrlParams,
    managerParams=memParams,
    beatBytes=extParams.beatBytes))

  val lbwifCrossingSource = LazyModule(new TLAsyncCrossingSource)
  val lbwifCrossingSink = LazyModule(new TLAsyncCrossingSink)

  (lbwif.managerNode
    := lbwifCrossingSink.node
    := TLAsyncCrossingSource()
    := switcher.outnodes(1))

  (fbus.fromMaster()()
    := TLBuffer()
    := TLWidthWidget(extParams.beatBytes)
    := TLAsyncCrossingSink()
    := lbwifCrossingSource.node
    := lbwif.clientNode)
}

trait HasPeripheryBeagleBundle
{
  val rst_async: Bool
  val boot: Bool

  val hbwif_clks: Vec[Clock]
  val hbwif_tx: Vec[Differential]
  val hbwif_rx: Vec[Differential]

  def tieoffBoot() {
    boot := true.B
  }
}

trait HasPeripheryBeagleModuleImp extends LazyModuleImp with HasPeripheryBeagleBundle
{
  val outer: HasPeripheryBeagle

  // instantiate IOs
  val rst_async = IO(Input(Bool()))
  val boot = IO(Input(Bool()))

  val hbwif_clks = IO(Input(Vec(p(HbwifTLKey).numBanks, Clock()))) // clks for hbwif
  val hbwif_tx = IO(chiselTypeOf(outer.hbwif.module.tx))
  val hbwif_rx = IO(chiselTypeOf(outer.hbwif.module.rx))

  val lbwif_serial = IO(chiselTypeOf(outer.lbwif.module.io.ser))

  // clocks coming from offchip
  val single_clks = IO(Input(Vec(1, Clock())))
  val diff_clks   = IO(Input(Vec(1, Clock())))

  val bh_clk_sel     = IO(Input(Bool()))
  val rs_clk_sel     = IO(Input(Bool()))
  val uncore_clk_sel = IO(Input(Bool()))

  val uncore_clk_out = IO(Output(Clock()))
  val bh_clk_out     = IO(Output(Clock()))
  val rs_clk_out     = IO(Output(Clock()))
  val lbwif_clk_out  = IO(Output(Clock()))


  // ------------------------------------------------

  val scr_mod = outer.scr.module

  // other signals
  scr_mod.io.boot := boot
  scr_mod.io.rst_async := rst_async

  // ------------------------------------------------

  /**
   * Helper function to wrap a clock divider and return the clock
   */
  def clkDiv(clk: Clock, rst: Bool, divBits: Int, divisor: UInt): Clock = {
    val clk_divider = withClockAndReset(clk, rst) {
      Module(new testchipip.ClockDivider(divBits))
    }
    clk_divider.io.divisor := divisor
    clk_divider.io.clockOut
  }

  /**
   * Helper function to wrap a clock mux and return the clock
   */
  def clkMux(clks: Seq[Clock], sel: UInt, rst_async: Bool): Clock = {
    val mux_clk = testchipip.ClockMutexMux(clks)
    mux_clk.io.sel := sel
    mux_clk.io.resetAsync := rst_async
    mux_clk.io.clockOut
  }

  /**
   * Helper function to wrap a clock mux and divider
   */
  def clkMuxThenDiv(clks: Seq[Clock], sel: UInt, rst_async: Bool, rst: Bool, divBits: Int, divisor: UInt): (Clock, Bool) = {
    val mux_clk = clkMux(clks, sel, rst_async)
    dontTouch(mux_clk)
    val mux_rst = ResetCatchAndSync(mux_clk, rst)
    dontTouch(mux_rst)
    val div_clk = clkDiv(mux_clk, mux_rst, divBits, divisor)
    dontTouch(div_clk)
    val div_rst = ResetCatchAndSync(div_clk, mux_rst)
    dontTouch(div_rst)
    (div_clk, div_rst)
  }

  // get the actual clock from the io clocks
  val offchip_clks = single_clks ++ diff_clks
  require(    bh_clk_sel.getWidth >= log2Ceil(offchip_clks.length), "[sys-top] must be able to select all input clocks")
  require(    rs_clk_sel.getWidth >= log2Ceil(offchip_clks.length), "[sys-top] must be able to select all input clocks")
  require(uncore_clk_sel.getWidth >= log2Ceil(offchip_clks.length), "[sys-top] must be able to select all input clocks")

  // setup uncore muxes and divider

  val uncore_mux_clk_out = clkMux(offchip_clks, uncore_clk_sel, rst_async)
  val uncore_mux_clk_rst = ResetCatchAndSync(uncore_mux_clk_out, rst_async)
  dontTouch(uncore_mux_clk_out)
  dontTouch(uncore_mux_clk_rst)

  val uncore_div_clk = clkDiv(uncore_mux_clk_out, uncore_mux_clk_rst, outer.scrParams.uncoreClkDivBits, scr_mod.io.uncore_clk_divisor)
  dontTouch(uncore_div_clk)

  // mux between the divided uncore and the undivided
  val uncore_clks_pre_muxed = Seq(uncore_mux_clk_out, uncore_div_clk)
  require(scr_mod.io.uncore_clk_pass_sel.getWidth >= log2Ceil(uncore_clks_pre_muxed.length), "[sys-top] must be able to select the uncore clocks")
  val uncore_clk = clkMux(uncore_clks_pre_muxed, scr_mod.io.uncore_clk_pass_sel, rst_async)

  // setup tile muxes
  val (bh_clk, bh_rst) = clkMuxThenDiv(offchip_clks, bh_clk_sel, rst_async, rst_async, outer.scrParams.bhClkDivBits, scr_mod.io.bh_clk_divisor)
  val (rs_clk, rs_rst) = clkMuxThenDiv(offchip_clks, rs_clk_sel, rst_async, rst_async, outer.scrParams.rsClkDivBits, scr_mod.io.rs_clk_divisor)

  // setup clock dividers

  val bh_clk_div2_out = clkDiv(bh_clk, bh_rst, outer.scrParams.bhClkDivBits, scr_mod.io.bh_clk_out_divisor)
  val rs_clk_div2_out = clkDiv(rs_clk, rs_rst, outer.scrParams.rsClkDivBits, scr_mod.io.rs_clk_out_divisor)

  val lbwif_clk = clkDiv(clock, reset.asBool, outer.scrParams.lbwifClkDivBits, scr_mod.io.lbwif_clk_divisor)
  val lbwif_rst = ResetCatchAndSync(lbwif_clk, reset.asBool)

  // setup lbwif
  lbwif_serial <> outer.lbwif.module.io.ser
  Seq(outer.lbwif, outer.lbwifCrossingSource, outer.lbwifCrossingSink).foreach { m =>
    m.module.clock := lbwif_clk
    m.module.reset := lbwif_rst
  }

  // pass out clocks
  uncore_clk_out := uncore_clk
  bh_clk_out     := bh_clk_div2_out
  rs_clk_out     := rs_clk_div2_out
  lbwif_clk_out  := lbwif_clk

  // switch from lbwif and hbwif
  outer.switcher.module.io.sel := scr_mod.io.switcher_sel

  // setup hbwif
  val hbwif_mod = outer.hbwif.module
  hbwif_mod.hbwifResets := scr_mod.io.hbwif_rsts
  hbwif_mod.resetAsync := rst_async
  hbwif_mod.hbwifRefClocks := hbwif_clks
  hbwif_tx <> hbwif_mod.tx
  hbwif_rx <> hbwif_mod.rx
}
