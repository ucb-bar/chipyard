package beagle

import chisel3._
import chisel3.core.withReset
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
case object BeagleSinkIds extends Field[Int]
case object CacheBlockStriping extends Field[Int]
case object LbwifBitWidth extends Field[Int]

case class BeagleParams(
  scrAddress: Int,
  clkSelBits: Int = 1,
  dividerBits: Int = 8,
  lbwifDividerBits: Int = 8
)

trait HasBeagleTopBundleContents extends Bundle {
  implicit val p: Parameters

  val boot          = Input(Bool())
  val rst_async     = Input(Bool())

  val clk_sel       = Output(UInt(p(PeripheryBeagleKey).clkSelBits.W))
  val lbwif_divider = Output(UInt(p(PeripheryBeagleKey).lbwifDividerBits.W))
  val hbwif_rsts    = Output(Vec(p(HbwifNumLanes), Bool()))

  val switcher_sel  = Output(Bool())
}

trait HasBeagleTopModuleContents extends MultiIOModule with HasRegMap {
  val io: HasBeagleTopBundleContents
  implicit val p: Parameters
  def params: BeagleParams
  def c = params

  val clk_sel = withReset(io.rst_async) {
    Module(new AsyncResetRegVec(w = c.clkSelBits, init = 0))
  }
  val lbwif_divider = withReset(io.rst_async) {
    Module(new AsyncResetRegVec(w = c.lbwifDividerBits, init = 2)) // start slow v fast?
  }
  val hbwif_rsts = Seq.fill(p(HbwifNumLanes)) {
    withReset(io.rst_async) {
      Module(new AsyncResetRegVec(w = 1, init = 1))
    }
  }
  val switcher_sel = withReset(io.rst_async) {
    Module(new AsyncResetRegVec(w = 1, init = 1))
  }

  io.clk_sel := clk_sel.io.q
  io.lbwif_divider := lbwif_divider.io.q
  io.hbwif_rsts := VecInit(hbwif_rsts.map(_.io.q))
  io.switcher_sel := switcher_sel.io.q

  regmap(
    0x08 -> Seq(RegField.r(1, RegReadFn(io.boot))),
    0x0c -> Seq(RegField.rwReg(1, switcher_sel.io)),
    0x10 -> hbwif_rsts.map(rst => RegField.rwReg(1, rst.io)),
    0x14 -> Seq(RegField.rwReg(c.clkSelBits, clk_sel.io)),
    0x1c -> Seq(RegField.rwReg(c.lbwifDividerBits, lbwif_divider.io))
  )
}

class TLBeagle(w: Int, c: BeagleParams)(implicit p: Parameters)
  extends TLRegisterRouter(c.scrAddress, "beagle-scr", Seq("ucb-bar,beagle-scr"), interrupts = 0, beatBytes = w)(
    new TLRegBundle(c, _) with HasBeagleTopBundleContents)(
    new TLRegModule(c, _, _) with HasBeagleTopModuleContents)

trait HasPeripheryBeagle {
  this: BaseSubsystem =>

  val scrParams = p(PeripheryBeagleKey)

  val myName = Some("beagle_scr")
  val scr = LazyModule(new TLBeagle(pbus.beatBytes, scrParams)).suggestName(myName)
  pbus.toVariableWidthSlave(myName) { scr.node := TLBuffer() }

  // setup boot scratch pad
  val bootScratchPad = LazyModule(new TLRAM(
    address = AddressSet(0x50000000, 0xffff),
    cacheable = false,
    executable = true,
    beatBytes = 8))
  val bootScratchPadBuffer = LazyModule(new TLBuffer())
  pbus.toVariableWidthSlave(Some("boot_scratchpad")) { bootScratchPad.node := bootScratchPadBuffer.node := TLBuffer() }

  val extMem = p(ExtMem).get
  val extParams = extMem.master
  //println(f"DEBUG: Beagle ExtMem: Base:${extParams.base}%X Sz:${extParams.size}%X")

  // setup the hbwif
  val lanesPerMemoryChannel = if (p(HbwifNumLanes)/extMem.nMemoryChannels == 0) 1 else p(HbwifNumLanes)/extMem.nMemoryChannels
  val base = AddressSet.misaligned(extParams.base, extParams.size)
  val filters = (0 until extMem.nMemoryChannels).map { case id =>
    AddressSet(id * p(CacheBlockBytes) * p(CacheBlockStriping), ~((extMem.nMemoryChannels-1) * p(CacheBlockBytes) * p(CacheBlockStriping)))
  }
  val addresses = filters.map{ case filt =>
    base.flatMap(_.intersect(filt))
  }
  //println(s"DEBUG: Addresses:$addresses Length:${addresses.length}")
  //println(s"DEBUG: NumMemChannels:${extMem.nMemoryChannels}")

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

  println("ONCHIP")
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

trait HasPeripheryBeagleBundle {
  val rst_async: Bool
  val boot: Bool

  val hbwif_clks: Vec[Clock]
  val hbwif_tx: Vec[Differential]
  val hbwif_rx: Vec[Differential]

  def tieoffBoot() {
    boot := true.B
  }
}

trait HasPeripheryBeagleModuleImp extends LazyModuleImp with HasPeripheryBeagleBundle {
  val outer: HasPeripheryBeagle

  val rst_async = IO(Input(Bool()))
  val boot = IO(Input(Bool()))

  val hbwif_clks = IO(Input(Vec(p(HbwifTLKey).numBanks, Clock()))) // clks for hbwif
  val hbwif_tx = IO(chiselTypeOf(outer.hbwif.module.tx))
  val hbwif_rx = IO(chiselTypeOf(outer.hbwif.module.rx))

  // setup lbwif
  val lbwif_serial = IO(chiselTypeOf(outer.lbwif.module.io.ser))
  lbwif_serial <> outer.lbwif.module.io.ser

  val lbwifClkDiv = Module(new testchipip.ClockDivider(outer.scrParams.lbwifDividerBits))
  val lbwif_clk = lbwifClkDiv.io.clockOut
  val lbwif_rst = ResetCatchAndSync(lbwif_clk, reset.toBool)
  lbwifClkDiv.io.divisor := outer.scr.module.io.lbwif_divider
  Seq(outer.lbwif, outer.lbwifCrossingSource, outer.lbwifCrossingSink).foreach { m =>
    m.module.clock := lbwif_clk
    m.module.reset := lbwif_rst
  }

  // switch from lbwif and hbwif
  outer.switcher.module.io.sel := outer.scr.module.io.switcher_sel

  // setup hbwif
  outer.hbwif.module.hbwifResets := outer.scr.module.io.hbwif_rsts
  outer.hbwif.module.resetAsync := rst_async
  outer.hbwif.module.hbwifRefClocks := hbwif_clks
  hbwif_tx <> outer.hbwif.module.tx
  hbwif_rx <> outer.hbwif.module.rx

  // other
  val clk_sel = outer.scr.module.io.clk_sel

  outer.scr.module.io.boot := boot
  outer.scr.module.io.rst_async := rst_async
}
