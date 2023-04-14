package chipyard

import freechips.rocketchip.tile._
import org.chipsalliance.cde.config._
import freechips.rocketchip.devices.tilelink._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.interrupts._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.rocket._
import freechips.rocketchip.subsystem.TileCrossingParamsLike
import freechips.rocketchip.util._
import freechips.rocketchip.prci.{ClockSinkParameters}

case class DummyTileAttachParams(
  tileParams: DummyTileParams
) extends CanAttachTile {
  type TileType = DummyTile
  val lookup = PriorityMuxHartIdFromSeq(Seq(tileParams))
  val crossingParams = RocketCrossingParams()
}

case class DummyTileParams(
  core: RocketCoreParams = RocketCoreParams(),
  icache: Option[ICacheParams] = Some(ICacheParams()),
  dcache: Option[DCacheParams] = Some(DCacheParams()),
  btb: Option[BTBParams] = Some(BTBParams()),
  dataScratchpadBytes: Int = 0,
  name: Option[String] = Some("tile"),
  hartId: Int = 0,
  beuAddr: Option[BigInt] = None,
  blockerCtrlAddr: Option[BigInt] = None,
  clockSinkParams: ClockSinkParameters = ClockSinkParameters(),
  boundaryBuffers: Boolean = false // if synthesized with hierarchical PnR, cut feed-throughs?
) extends InstantiableTileParams[DummyTile] 
{
  def instantiate(crossing: TileCrossingParamsLike, lookup: LookupByHartIdImpl)(implicit p: Parameters): DummyTile = {
    new DummyTile(this, crossing, lookup)
  }
}


class DummyTile (val dummyParams: DummyTileParams,
                 crossing: ClockCrossingType,
                 lookup: LookupByHartIdImpl,
                 q: Parameters)
  extends BaseTile(dummyParams, crossing, lookup, q)
  with SinksExternalInterrupts
  with SourcesExternalNotifications
{

  def this(params: DummyTileParams, crossing: TileCrossingParamsLike, lookup: LookupByHartIdImpl)(implicit p: Parameters) =
    this(params, crossing.crossingType, lookup, p)

  val intOutwardNode = IntIdentityNode()
  val slaveNode = TLIdentityNode()
  val masterNode = visibilityNode

  val bus_error_unit_device = new SimpleDevice("bus-error-unit", Seq("sifive,buserror0"))
  val bus_error_unit_intNode = IntSourceNode(IntSourcePortSimple(resources = bus_error_unit_device.int))
  val bus_error_unit_node = TLRegisterNode(
    address = Seq(AddressSet(dummyParams.beuAddr.get, 4096 - 1)),
    device = bus_error_unit_device,
    beatBytes = p(XLen) / 8)
  intOutwardNode := bus_error_unit_intNode
  connectTLSlave(bus_error_unit_node, xBytes)


  val tile_master_blocker_device = new SimpleDevice("basic-bus-blocker", Seq("sifive,basic-bus-blocker0"))
  val tmb_params = BasicBusBlockerParams(dummyParams.blockerCtrlAddr.get, xBytes, masterPortBeatBytes, deadlock = true)
  val tile_master_blocker_controlNode = TLRegisterNode(
    address = Seq(AddressSet(tmb_params.controlAddress, tmb_params.controlSize - 1)),
    device = tile_master_blocker_device,
    beatBytes = tmb_params.controlBeatBytes)
  connectTLSlave(tile_master_blocker_controlNode, xBytes)

  // TODO : Add some random master node here & call makeIOs()?
  val placeholderMasterNode = TLClientNode(Seq(TLMasterPortParameters.v1(Seq(TLClientParameters(
     name = "my-client",
     sourceId = IdRange(0, 4),
     requestFifo = true,
     visibility = Seq(AddressSet(0x10000, 0xffff)))))))
  tlOtherMastersNode := placeholderMasterNode
  masterNode :=* tlOtherMastersNode


  // Required entry of CPU device in the device tree for interrupt purpose
  val cpuDevice: SimpleDevice = new SimpleDevice("cpu", Seq("ucb-bar,spike", "riscv")) {
    override def parent = Some(ResourceAnchors.cpus)

    override def describe(resources: ResourceBindings): Description = {
      val Description(name, mapping) = super.describe(resources)
      Description(name, mapping ++
        cpuProperties ++
        nextLevelCacheProperty ++
        tileProperties)
    }
  }

  ResourceBinding {
    Resource(cpuDevice, "reg").bind(ResourceAddress(hartId))
  }

  override lazy val module = new DummyTileModuleImp(outer = this)
}

class DummyTileModuleImp(outer: DummyTile) extends BaseTileModuleImp(outer)
{

}


class WithDummyTile()
