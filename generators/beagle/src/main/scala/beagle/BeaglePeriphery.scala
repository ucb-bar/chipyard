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

import testchipip._

case object PeripheryBeagleKey extends Field[BeagleParams]
case object BeaglePipelineResetDepth extends Field[Int]
case object BeagleSinkIds extends Field[Int]

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
  val divider       = Output(UInt(p(PeripheryBeagleKey).dividerBits.W))
  val lbwif_divider = Output(UInt(p(PeripheryBeagleKey).lbwifDividerBits.W))
}

trait HasBeagleTopModuleContents extends MultiIOModule with HasRegMap {
  val io: HasBeagleTopBundleContents
  implicit val p: Parameters
  def params: BeagleParams
  def c = params

  val clk_sel = withReset(io.rst_async) {
    Module(new AsyncResetRegVec(w = c.clkSelBits, init = 0))
  }
  val divider = withReset(io.rst_async) {
    Module(new AsyncResetRegVec(w = c.dividerBits, init = 4))
  }
  val lbwif_divider = withReset(io.rst_async) {
    Module(new AsyncResetRegVec(w = c.lbwifDividerBits, init = 8))
  }

  io.clk_sel := clk_sel.io.q
  io.divider := divider.io.q
  io.lbwif_divider := lbwif_divider.io.q

  regmap(
    0x08 -> Seq(RegField.r(1, RegReadFn(io.boot))),
    0x14 -> Seq(RegField.rwReg(c.clkSelBits, clk_sel.io)),
    0x18 -> Seq(RegField.rwReg(c.dividerBits, divider.io)),
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

  // setup the backup serdes (otherwise known as the lbwif)
  val extMem = p(ExtMem).get
  val extParams = extMem.master

  val memParams = TLManagerParameters(
    address = Seq(AddressSet(extParams.base, extParams.size-1)),
    resources = (new MemoryDevice).reg,
    regionType = RegionType.UNCACHED, // cacheable
    executable = true,
    fifoId = Some(0),
    supportsGet        = TransferSizes(1, p(CacheBlockBytes)),
    supportsPutFull    = TransferSizes(1, p(CacheBlockBytes)),
    supportsPutPartial = TransferSizes(1, p(CacheBlockBytes)))
  val ctrlParams = TLClientParameters(
    name = "tl_serdes_control",
    sourceId = IdRange(0,128),
    requestFifo = true) //TODO: how many outstanding xacts


  val lbwif = LazyModule(new TLSerdesser(
    w=4,
    clientParams=ctrlParams,
    managerParams=memParams,
    beatBytes=extParams.beatBytes))

  val lbwifCrossingSource = LazyModule(new TLAsyncCrossingSource)
  val lbwifCrossingSink = LazyModule(new TLAsyncCrossingSink)

  (lbwif.managerNode
    := lbwifCrossingSink.node
    := TLAsyncCrossingSource()
    := mbus.coupleTo("lbwif"){ TLBuffer() :*= _ })

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

  def tieoffBoot() {
    boot := true.B
  }
}

trait HasPeripheryBeagleModuleImp extends LazyModuleImp with HasPeripheryBeagleBundle {
  val outer: HasPeripheryBeagle
  val rst_async = IO(Input(Bool()))
  val boot = IO(Input(Bool()))

  val tl_serial = IO(chiselTypeOf(outer.lbwif.module.io.ser))
  tl_serial <> outer.lbwif.module.io.ser

  val lbwifClkDiv = Module(new testchipip.ClockDivider(outer.scrParams.lbwifDividerBits))
  val lbwif_clk = lbwifClkDiv.io.clockOut
  val lbwif_rst = ResetCatchAndSync(lbwif_clk, reset.toBool)
  lbwifClkDiv.io.divisor := outer.scr.module.io.lbwif_divider
  Seq(outer.lbwif, outer.lbwifCrossingSource, outer.lbwifCrossingSink).foreach { m =>
    m.module.clock := lbwif_clk
    m.module.reset := lbwif_rst
  }

  val clk_sel = outer.scr.module.io.clk_sel
  val divider  = outer.scr.module.io.divider

  outer.scr.module.io.boot := boot
  outer.scr.module.io.rst_async := rst_async
}
