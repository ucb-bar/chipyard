package tlserdes

import chisel3._
import chisel3.util._
import freechips.rocketchip.config.{Parameters, Field}
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.chip.{ExtMem, HasSystemNetworks}
import freechips.rocketchip.coreplex.{BankedL2Config, CacheBlockBytes}
import freechips.rocketchip.tilelink._
import testchipip._

case object TLSerdesWidth extends Field[Int]

class TLSerdesMem(implicit p: Parameters) extends LazyModule {
  val config = p(ExtMem)
  val channels = p(BankedL2Config).nMemoryChannels
  val serdesWidth = p(TLSerdesWidth)
  val blockBytes = p(CacheBlockBytes)

  val desser = LazyModule(new TLDesser(serdesWidth,
    Seq.tabulate(channels) { ch =>
      TLClientParameters(
        name = s"tl-desser$ch", sourceId = IdRange(0, 1 << config.idBits))
    }))

  for (ch <- 0 until channels) {
    val base = AddressSet(config.base, config.size-1)
    val filter = AddressSet(ch * blockBytes, ~((channels-1) * blockBytes))

    val mem = LazyModule(new TLRAM(
      address = base.intersect(filter).get,
      executable = true,
      beatBytes = config.beatBytes))

    mem.node := TLBuffer()(
      TLFragmenter(config.beatBytes, blockBytes)(desser.node))
  }

  lazy val module = new LazyModuleImp(this) {
    val io = IO(new Bundle {
      val ser = Vec(channels, new SerialIO(serdesWidth))
    })

    desser.module.io.ser <> io.ser
  }
}

trait HasPeripheryTLSerdesMemPort extends HasSystemNetworks {
  private val config = p(ExtMem)
  private val channels = p(BankedL2Config).nMemoryChannels
  private val serdesWidth = p(TLSerdesWidth)
  private val blockBytes = p(CacheBlockBytes)
  private val device = new MemoryDevice

  val serdes = LazyModule(new TLSerdes(
    w = serdesWidth,
    params = Seq.tabulate(channels) { ch =>
      val base = AddressSet(config.base, config.size-1)
      val filter = AddressSet(ch * blockBytes, ~((channels-1) * blockBytes))

      TLManagerParameters(
        address = base.intersect(filter).toSeq,
        resources = device.reg,
        regionType = RegionType.UNCACHED,
        executable = true,
        supportsGet = TransferSizes(1, blockBytes),
        supportsPutFull = TransferSizes(1, blockBytes),
        supportsPutPartial = TransferSizes(1, blockBytes),
        fifoId = Some(0))
    },
    beatBytes = config.beatBytes))

  mem.foreach { xbar =>
    serdes.node :=
      TLBuffer()(
      TLAtomicAutomata()(
      TLSourceShrinker(1 << config.idBits)(xbar.node)))
  }
}

trait HasPeripheryTLSerdesMemPortModuleImp extends LazyMultiIOModuleImp {
  private val serdesWidth = p(TLSerdesWidth)
  private val config = p(ExtMem)
  private val channels = p(BankedL2Config).nMemoryChannels

  val outer: HasPeripheryTLSerdesMemPort
  val tlser = IO(Vec(channels, new SerialIO(serdesWidth)))

  tlser <> outer.serdes.module.io.ser

  def connectSerdesMem(dummy: Int = 0) {
    val memser = Module(LazyModule(new TLSerdesMem).module).io.ser
    memser.zip(tlser).foreach { case (mem, tl) => mem.connect(tl) }
  }
}
