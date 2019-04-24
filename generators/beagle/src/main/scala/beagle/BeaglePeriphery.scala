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
  numClusterClocks: Int = 6,
  unclusterClockSelBits: Int = 2,
  unclusterDividerBits: Int = 8,
  lbwifDividerBits: Int = 8
) {
  def clockSelBits = log2Ceil(numClusterClocks)
}

trait HasBeagleTopBundleContents extends Bundle {
  implicit val p: Parameters
  val clusterResets = Output(Vec(p(NClusters), Bool()))
  val clusterClockSels = Output(Vec(p(NClusters), UInt(p(PeripheryBeagleKey).clockSelBits.W)))
  val switcherSel = Output(Bool())
  val boot = Input(Bool())
  val unclusterClockSel = Output(UInt(p(PeripheryBeagleKey).unclusterClockSelBits.W))
  val unclusterDivider = Output(UInt(p(PeripheryBeagleKey).unclusterDividerBits.W))
  val lbwifDivider = Output(UInt(p(PeripheryBeagleKey).lbwifDividerBits.W))
  val resetAsync = Input(Bool())
}

trait HasBeagleTopModuleContents extends MultiIOModule with HasRegMap {
  val io: HasBeagleTopBundleContents
  implicit val p: Parameters
  def params: BeagleParams
  def c = params

  val clusterResets = Seq.fill(p(NClusters)) {
    withReset(io.resetAsync) { Module(new AsyncResetRegVec(w = 1, init = 1)) }
  }
  val clusterClockSels = Seq.fill(p(NClusters)) {
    withReset(io.resetAsync) { Module(new AsyncResetRegVec(w = p(PeripheryBeagleKey).clockSelBits, init = 2)) }
  }
  val switcherSel = withReset(io.resetAsync) { Module(new AsyncResetRegVec(w = 1, init = 1)) }
  val unclusterClockSel = withReset(io.resetAsync) {
    Module(new AsyncResetRegVec(w = c.unclusterClockSelBits, init = 0))
  }
  val unclusterDivider = withReset(io.resetAsync) {
    Module(new AsyncResetRegVec(w = c.unclusterDividerBits, init = 4))
  }
  val lbwifDivider = withReset(io.resetAsync) {
    Module(new AsyncResetRegVec(w = c.lbwifDividerBits, init = 8))
  }

  io.clusterResets := VecInit(clusterResets.map(_.io.q))
  io.clusterClockSels := VecInit(clusterClockSels.map(_.io.q))
  io.switcherSel := switcherSel.io.q
  io.unclusterClockSel := unclusterClockSel.io.q
  io.unclusterDivider := unclusterDivider.io.q
  io.lbwifDivider := lbwifDivider.io.q

  regmap(
    0x00 -> clusterResets.map(x => RegField.rwReg(1, x.io)),
    0x04 -> clusterClockSels.map(x => RegField.rwReg(c.clockSelBits, x.io)),
    0x08 -> Seq(RegField.r(1, RegReadFn(io.boot))),
    0x0c -> Seq(RegField.rwReg(1, switcherSel.io)),
    0x14 -> Seq(RegField.rwReg(c.unclusterClockSelBits, unclusterClockSel.io)),
    0x18 -> Seq(RegField.rwReg(c.unclusterDividerBits, unclusterDivider.io)),
    0x1c -> Seq(RegField.rwReg(c.lbwifDividerBits, lbwifDivider.io))
  )
}

class TLBeagle(w: Int, c: BeagleParams)(implicit p: Parameters)
  extends TLRegisterRouter(c.scrAddress, "eagle-scr", Seq("ucb-bar,eagle-scr"), interrupts = 0, beatBytes = w)(
    new TLRegBundle(c, _) with HasBeagleTopBundleContents)(
    new TLRegModule(c, _, _) with HasBeagleTopModuleContents)


trait HasPeripheryBeagle {
  this: BaseSubsystem =>
  val scrParams = p(PeripheryBeagleKey)
  val myName = Some("eagle_scr")
  val scr = LazyModule(new TLBeagle(pbus.beatBytes, scrParams)).suggestName(myName)
  pbus.toVariableWidthSlave(myName) { scr.node := TLBuffer()}
  val extMem = p(ExtMem).get
  val extParams = extMem.master
  println(s"Beagle ExtMem:${extParams.base} size: ${extParams.size}")

  val memParams = TLManagerParameters(
    address = Seq(AddressSet(extParams.base, extParams.size-1)),
    resources = (new MemoryDevice).reg,
    regionType = RegionType.UNCACHED, // cacheable
    executable = true,
    fifoId = Some(0),
    supportsGet = TransferSizes(1, p(CacheBlockBytes)),
    supportsPutFull = TransferSizes(1, p(CacheBlockBytes)),
    supportsAcquireT   = TransferSizes(1, p(CacheBlockBytes)),
    supportsAcquireB   = TransferSizes(1, p(CacheBlockBytes)),
    supportsArithmetic = TransferSizes(1, p(CacheBlockBytes)),
    supportsLogical    = TransferSizes(1, p(CacheBlockBytes)),
    supportsPutPartial = TransferSizes(1, p(CacheBlockBytes)),
    supportsHint       = TransferSizes(1, p(CacheBlockBytes)))
  val ctrlParams = TLClientParameters(
    name = "tl_serdes_control",
    sourceId = IdRange(0,128),
    requestFifo = true) //TODO: how many outstanding xacts

  val bootScratchPad = LazyModule(new TLRAM(AddressSet(0x50000000, 0xffff),
    cacheable = false, executable = true, beatBytes = 8))
  val bootScratchPadBuffer = LazyModule( new TLBuffer())
  pbus.toVariableWidthSlave(Some("boot_scratchpad")) { bootScratchPad.node := bootScratchPadBuffer.node := TLBuffer() }

  val lanesPerMemoryChannel = 1 // TODO: Gotten from HBWIF
  val lbwif = LazyModule(new TLSerdesser(
    w=4,
    clientParams=ctrlParams,
    managerParams=memParams,
    beatBytes=extParams.beatBytes,
    endSinkId=p(BeagleSinkIds)*lanesPerMemoryChannel))
  val base = AddressSet(extParams.base, extParams.size-1)
  val filters = (0 until extMem.nMemoryChannels).map { case id =>
    AddressSet(id * p(CacheBlockBytes) * p(CacheBlockStriping), ~((extMem.nMemoryChannels-1) * p(CacheBlockBytes) * p(CacheBlockStriping)))
  }
  val addresses = filters.map{ case filt =>
    base.intersect(filt).get
  }
  val switcher = LazyModule(new TLSwitcher(
    inPortN = extMem.nMemoryChannels,
    outPortN = Seq(extMem.nMemoryChannels, 1),
    address = addresses,
    beatBytes = extParams.beatBytes,
    lineBytes = p(CacheBlockBytes),
    idBits = 7
    //,endSinkId = p(BeagleSinkIds)*lanesPerMemoryChannel
  ))

  switcher.innode :*= mbus.coupleTo("switcherPort") { TLBuffer() :*= _}// toDRAMController(Some("switcherPort")) { TLBuffer() }

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
  val resetAsync: Bool
  val boot: Bool

  def tieoffBoot() {
    boot := true.B
  }
}

trait HasPeripheryBeagleModuleImp extends LazyModuleImp with HasPeripheryBeagleBundle {
  val outer: HasPeripheryBeagle
  val resetAsync = IO(Input(Bool()))
  val boot = IO(Input(Bool()))

  val tl_serial = IO(chiselTypeOf(outer.lbwif.module.io.ser))
  tl_serial <> outer.lbwif.module.io.ser

  val lbwifClockDiv = Module(new testchipip.ClockDivider(outer.scrParams.lbwifDividerBits))
  val lbwifClock = lbwifClockDiv.io.clockOut
  val lbwifReset = ResetCatchAndSync(lbwifClock, reset.toBool)
  lbwifClockDiv.io.divisor := outer.scr.module.io.lbwifDivider
  Seq(outer.lbwif, outer.lbwifCrossingSource, outer.lbwifCrossingSink).foreach { m =>
    m.module.clock := lbwifClock
    m.module.reset := lbwifReset
  }

  val clusterResets     = outer.scr.module.io.clusterResets
  val clusterClockSels  = outer.scr.module.io.clusterClockSels
  val unclusterClockSel = outer.scr.module.io.unclusterClockSel
  val unclusterDivider  = outer.scr.module.io.unclusterDivider

  outer.switcher.module.io.sel := outer.scr.module.io.switcherSel
  outer.scr.module.io.boot := boot
  outer.scr.module.io.resetAsync := resetAsync
}
