package chipyard

import chisel3._
import chisel3.util._
import chisel3.experimental.{Analog, BaseModule, DataMirror, Direction}
import freechips.rocketchip.tile._
import org.chipsalliance.cde.config._
import freechips.rocketchip.devices.tilelink._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.interrupts._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.rocket._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.util._
import freechips.rocketchip.prci._
import freechips.rocketchip.devices.debug._
import freechips.rocketchip.jtag.{JTAGIO}
import testchipip.{SerialTLKey, SerialAdapter, UARTAdapter, SimDRAM}
import chipyard.iobinders._
import chipyard.clocking._
import barstools.iocell.chisel._
import chipyard.{BuildTop}


case class DummyTileAttachParams(
  tileParams: DummyTileParams,
  crossingParams: RocketCrossingParams
) extends CanAttachTile {
  type TileType = DummyTile
// val lookup = PriorityMuxHartIdFromSeq(Seq(tileParams))
// val crossingParams = RocketCrossingParams()
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

class TileNodeWrapperModule(dummyParams: DummyTileParams, xBytes: Int, masterPortBeatBytes: Int)(implicit p: Parameters) extends LazyModule {
  val placeholderMasterNode = TLClientNode(Seq(TLMasterPortParameters.v1(Seq(TLClientParameters(
    name = "placeholder-master-node",
    sourceId = IdRange(0, 3),
    supportsProbe = TransferSizes(64, 64)
  )))))


  override lazy val module = new TileNodeWrapperModuleImp(this)

  val masterPunchThroughIO = InModuleBody { placeholderMasterNode.makeIOs() }
}

class TileNodeWrapperModuleImp(outer: TileNodeWrapperModule) extends LazyModuleImp(outer) {
  dontTouch(outer.masterPunchThroughIO.head)
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

  val nodeWrapper = LazyModule(new TileNodeWrapperModule(dummyParams, xBytes, masterPortBeatBytes))

//  intOutwardNode := nodeWrapper.bus_error_unit_intNode

  tlOtherMastersNode := nodeWrapper.placeholderMasterNode
  masterNode :=* tlOtherMastersNode
  DisableMonitors { implicit p => tlSlaveXbar.node :*= slaveNode }

  // Required entry of CPU device in the device tree for interrupt purpose
  val cpuDevice: SimpleDevice = new SimpleDevice("cpu", Seq("ucb-bar,dummy", "riscv")) {
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

  InModuleBody {
    dontTouch(intSinkNode.in(0)._1)
  }
}

class DummyTileModuleImp(outer: DummyTile) extends BaseTileModuleImp(outer)
{
  // TODO : instantiate bridges here

  val int_bundle = Wire(new TileInterrupts)
  outer.decodeCoreInterrupts(int_bundle)

  val bridge_emulator_blackbox = Module(new BridgeEmulatorBlackBox)
  bridge_emulator_blackbox.io.clock := clock
  bridge_emulator_blackbox.io.reset := reset.asBool

  val master_a = outer.nodeWrapper.masterPunchThroughIO.head.a
  master_a.valid := bridge_emulator_blackbox.io.masterPunchThroughIO_0_a_valid
  master_a.bits.opcode := bridge_emulator_blackbox.io.masterPunchThroughIO_0_a_bits_opcode
  master_a.bits.param := bridge_emulator_blackbox.io.masterPunchThroughIO_0_a_bits_param
  master_a.bits.size := bridge_emulator_blackbox.io.masterPunchThroughIO_0_a_bits_size
  master_a.bits.source := bridge_emulator_blackbox.io.masterPunchThroughIO_0_a_bits_source
  master_a.bits.address := bridge_emulator_blackbox.io.masterPunchThroughIO_0_a_bits_address
  master_a.bits.mask := bridge_emulator_blackbox.io.masterPunchThroughIO_0_a_bits_mask
  master_a.bits.data := bridge_emulator_blackbox.io.masterPunchThroughIO_0_a_bits_data
  master_a.bits.corrupt := bridge_emulator_blackbox.io.masterPunchThroughIO_0_a_bits_corrupt

  val master_d = outer.nodeWrapper.masterPunchThroughIO.head.d
  master_d.ready := bridge_emulator_blackbox.io.masterPunchThroughIO_0_d_ready

  bridge_emulator_blackbox.io.masterPunchThroughIO_0_a_ready := master_a.ready
  bridge_emulator_blackbox.io.masterPunchThroughIO_0_d_valid := master_d.valid
  bridge_emulator_blackbox.io.masterPunchThroughIO_0_d_bits_opcode := master_d.bits.opcode
  bridge_emulator_blackbox.io.masterPunchThroughIO_0_d_bits_param := master_d.bits.param
  bridge_emulator_blackbox.io.masterPunchThroughIO_0_d_bits_size := master_d.bits.size
  bridge_emulator_blackbox.io.masterPunchThroughIO_0_d_bits_source := master_d.bits.source
  bridge_emulator_blackbox.io.masterPunchThroughIO_0_d_bits_sink := master_d.bits.sink
  bridge_emulator_blackbox.io.masterPunchThroughIO_0_d_bits_denied := master_d.bits.denied
  bridge_emulator_blackbox.io.masterPunchThroughIO_0_d_bits_data := master_d.bits.data
  bridge_emulator_blackbox.io.masterPunchThroughIO_0_d_bits_corrupt := master_d.bits.corrupt


  bridge_emulator_blackbox.io.hartid := outer.hartIdSinkNode.bundle

  val (wfi, _) = outer.wfiNode.out(0)
  wfi(0) := RegNext(bridge_emulator_blackbox.io.wfi)

  int_bundle.debug := bridge_emulator_blackbox.io.debug
  int_bundle.mtip  := bridge_emulator_blackbox.io.mtip
  int_bundle.msip  := bridge_emulator_blackbox.io.msip
  int_bundle.meip  := bridge_emulator_blackbox.io.meip
  int_bundle.seip.get  := bridge_emulator_blackbox.io.seip // HACK : Assume that seip is defined in the default config
  dontTouch(int_bundle)
}


class BridgeEmulatorBlackBox extends BlackBox with HasBlackBoxResource {
  val io = IO(new Bundle {
    val clock = Input(Clock())
    val reset = Input(Bool())
    val masterPunchThroughIO_0_a_valid = Output(Bool())
    val masterPunchThroughIO_0_a_bits_opcode = Output(UInt(3.W))
    val masterPunchThroughIO_0_a_bits_param = Output(UInt(3.W))
    val masterPunchThroughIO_0_a_bits_size = Output(UInt(4.W))
    val masterPunchThroughIO_0_a_bits_source = Output(UInt(2.W))
    val masterPunchThroughIO_0_a_bits_address = Output(UInt(32.W))
    val masterPunchThroughIO_0_a_bits_mask = Output(UInt(8.W))
    val masterPunchThroughIO_0_a_bits_data = Output(UInt(64.W))
    val masterPunchThroughIO_0_a_bits_corrupt = Output(Bool())
    val masterPunchThroughIO_0_d_ready = Output(Bool())
    val beuIntSlavePunchThroughIO_0_0 = Output(Bool())
    val masterPunchThroughIO_0_a_ready = Input(Bool())
    val masterPunchThroughIO_0_d_valid = Input(Bool())
    val masterPunchThroughIO_0_d_bits_opcode = Input(UInt(3.W))
    val masterPunchThroughIO_0_d_bits_param = Input(UInt(2.W))
    val masterPunchThroughIO_0_d_bits_size = Input(UInt(4.W))
    val masterPunchThroughIO_0_d_bits_source = Input(UInt(2.W))
    val masterPunchThroughIO_0_d_bits_sink = Input(UInt(3.W))
    val masterPunchThroughIO_0_d_bits_denied = Input(Bool())
    val masterPunchThroughIO_0_d_bits_data = Input(UInt(64.W))
    val masterPunchThroughIO_0_d_bits_corrupt = Input(Bool())
    val hartid = Input(UInt(2.W))
    val wfi = Output(Bool())
    val debug = Output(Bool())
    val mtip = Output(Bool())
    val msip = Output(Bool())
    val meip = Output(Bool())
    val seip = Output(Bool())
  })

  addResource("/vsrc/BridgeEmulatorBlackBox.v")
}




///////////////////////////////////////////////////////////////////////////////






class WithDummyTile(n: Int = 1, tileParams: DummyTileParams = DummyTileParams(),
  overrideIdOffset: Option[Int] = None) extends Config((site, here, up) => {
  case TilesLocated(InSubsystem) => {
    val prev = up(TilesLocated(InSubsystem), site)
    val idOffset = overrideIdOffset.getOrElse(prev.size)
    (0 until n).map { i =>
      DummyTileAttachParams(
        tileParams = tileParams.copy(
          hartId = i + idOffset
        ),
        crossingParams = RocketCrossingParams()
      )
    } ++ prev
  }
})

class DummyTileConfig extends Config(
  new chipyard.WithDummyTile ++
// new chipyard.config.WithSerialTLBackingMemory ++
  new chipyard.config.AbstractConfig
)
