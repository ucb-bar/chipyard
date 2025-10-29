package testchipip.soc

import chisel3._

import freechips.rocketchip.subsystem._
import org.chipsalliance.cde.config.{Field, Config, Parameters}
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.resources.{DiplomacyUtils}
import freechips.rocketchip.prci.{ClockSinkDomain, ClockSinkParameters}
import scala.collection.immutable.{ListMap}


case class BankedScratchpadParams(
  base: BigInt,
  size: BigInt,
  busWhere: TLBusWrapperLocation = SBUS,
  banks: Int = 4,
  subBanks: Int = 2,
  name: String = "banked-scratchpad",
  disableMonitors: Boolean = false,
  buffer: BufferParams = BufferParams.none,
  outerBuffer: BufferParams = BufferParams.none,
  dtsEnabled: Boolean = false
)

case object BankedScratchpadKey extends Field[Seq[BankedScratchpadParams]](Nil)

class ScratchpadBank(subBanks: Int, address: AddressSet, beatBytes: Int, devOverride: MemoryDevice, buffer: BufferParams)(implicit p: Parameters) extends ClockSinkDomain(ClockSinkParameters())(p) {
  val mask = (subBanks - 1) * p(CacheBlockBytes)
  val xbar = TLXbar()
  (0 until subBanks).map { sb =>
    val ram = LazyModule(new TLRAM(
      address = AddressSet(address.base + sb * p(CacheBlockBytes), address.mask - mask),
      beatBytes = beatBytes,
      devOverride = Some(devOverride)) {
      override lazy val desiredName = s"TLRAM_ScratchpadBank"
    })
    ram.node :=  TLFragmenter(beatBytes, p(CacheBlockBytes), nameSuffix = Some("ScratchpadBank")) := TLBuffer(buffer) := xbar
  }
  override lazy val desiredName = "ScratchpadBank"
}

case object BankedScratchpadInjector extends SubsystemInjector((p, baseSubsystem) => {
  p(BankedScratchpadKey).zipWithIndex.foreach { case (params, si) =>
    val bus = baseSubsystem.locateTLBusWrapper(params.busWhere)

    require (params.subBanks >= 1)

    val name = params.name
    val banks = params.banks
    val bankStripe = p(CacheBlockBytes)*params.subBanks
    val mask = (params.banks-1)*bankStripe
    val device = new MemoryDevice {
      override def describe(resources: ResourceBindings): Description = {
        Description(describeName("memory", resources), ListMap(
          "reg"         -> resources.map.filterKeys(DiplomacyUtils.regFilter).flatMap(_._2).map(_.value).toList,
          "device_type" -> Seq(ResourceString("memory")),
          "status"      -> Seq(ResourceString(if (params.dtsEnabled) "okay" else "disabled"))
        ))
      }
    }

    def genBanks()(implicit p: Parameters) = (0 until banks).map { b =>
      val bank = LazyModule(new ScratchpadBank(
        params.subBanks,
        AddressSet(params.base + bankStripe * b, params.size - 1 - mask),
        bus.beatBytes,
        device,
        params.buffer))
      bank.clockNode := bus.fixedClockNode
      bus.coupleTo(s"$name-$si-$b") { bank.xbar := bus { TLBuffer(params.outerBuffer) } := _ }
    }

    if (params.disableMonitors) DisableMonitors({ implicit p => genBanks()(p) })(p) else genBanks()(p)
  }
})

