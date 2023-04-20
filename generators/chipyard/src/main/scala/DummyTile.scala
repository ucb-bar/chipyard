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


case object IsFireChip extends Field[Boolean](false)


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


  if (p(IsFireChip)) {
    val tile_bridge = Module(new TileCoreSideBridge())
    tile_bridge.io.clock := clock
    tile_bridge.io.reset := reset.asBool

    tile_bridge.io.debug := int_bundle.debug
    tile_bridge.io.mtip := int_bundle.mtip
    tile_bridge.io.msip := int_bundle.msip
    tile_bridge.io.meip := int_bundle.meip
    tile_bridge.io.seip := int_bundle.seip.get
    dontTouch(int_bundle)

    tile_bridge.io.hartid := outer.hartIdSinkNode.bundle

    val tlmaster = outer.nodeWrapper.masterPunchThroughIO.head
    tile_bridge.io.in.tlmaster_a_ready := tlmaster.a.ready
    tile_bridge.io.in.tlmaster_b_valid := tlmaster.b.valid
    tile_bridge.io.in.tlmaster_b_bits_opcode := tlmaster.b.bits.opcode
    tile_bridge.io.in.tlmaster_b_bits_param := tlmaster.b.bits.param
    tile_bridge.io.in.tlmaster_b_bits_size := tlmaster.b.bits.size
    tile_bridge.io.in.tlmaster_b_bits_source := tlmaster.b.bits.source
    tile_bridge.io.in.tlmaster_b_bits_address := tlmaster.b.bits.address
    tile_bridge.io.in.tlmaster_b_bits_data := tlmaster.b.bits.data
    tile_bridge.io.in.tlmaster_b_bits_mask := tlmaster.b.bits.mask
    tile_bridge.io.in.tlmaster_b_bits_corrupt := tlmaster.b.bits.corrupt
    tile_bridge.io.in.tlmaster_c_ready := tlmaster.c.ready
    tile_bridge.io.in.tlmaster_d_valid := tlmaster.d.valid
    tile_bridge.io.in.tlmaster_d_bits_opcode := tlmaster.d.bits.opcode
    tile_bridge.io.in.tlmaster_d_bits_param := tlmaster.d.bits.param
    tile_bridge.io.in.tlmaster_d_bits_size := tlmaster.d.bits.size
    tile_bridge.io.in.tlmaster_d_bits_source := tlmaster.d.bits.source
    tile_bridge.io.in.tlmaster_d_bits_sink := tlmaster.d.bits.sink
    tile_bridge.io.in.tlmaster_d_bits_denied := tlmaster.d.bits.denied
    tile_bridge.io.in.tlmaster_d_bits_data := tlmaster.d.bits.data
    tile_bridge.io.in.tlmaster_d_bits_corrupt := tlmaster.d.bits.corrupt
    tile_bridge.io.in.tlmaster_e_ready := tlmaster.e.ready

    val (wfi, _) = outer.wfiNode.out(0)
    wfi(0) := RegNext(tile_bridge.io.wfi)
    tlmaster.a.valid := tile_bridge.io.out.tlmaster_a_valid
    tlmaster.a.bits.opcode := tile_bridge.io.out.tlmaster_a_bits_opcode
    tlmaster.a.bits.param := tile_bridge.io.out.tlmaster_a_bits_param
    tlmaster.a.bits.size := tile_bridge.io.out.tlmaster_a_bits_size
    tlmaster.a.bits.source := tile_bridge.io.out.tlmaster_a_bits_source
    tlmaster.a.bits.address := tile_bridge.io.out.tlmaster_a_bits_address
    tlmaster.a.bits.mask := tile_bridge.io.out.tlmaster_a_bits_mask
    tlmaster.a.bits.data := tile_bridge.io.out.tlmaster_a_bits_data
    tlmaster.a.bits.corrupt := tile_bridge.io.out.tlmaster_a_bits_corrupt
    tlmaster.b.ready := tile_bridge.io.out.tlmaster_b_ready
    tlmaster.c.valid := tile_bridge.io.out.tlmaster_c_valid
    tlmaster.c.bits.opcode := tile_bridge.io.out.tlmaster_c_bits_opcode
    tlmaster.c.bits.param := tile_bridge.io.out.tlmaster_c_bits_param
    tlmaster.c.bits.size := tile_bridge.io.out.tlmaster_c_bits_size
    tlmaster.c.bits.source := tile_bridge.io.out.tlmaster_c_bits_source
    tlmaster.c.bits.address := tile_bridge.io.out.tlmaster_c_bits_address
    tlmaster.c.bits.data := tile_bridge.io.out.tlmaster_c_bits_data
    tlmaster.c.bits.corrupt := tile_bridge.io.out.tlmaster_c_bits_corrupt
    tlmaster.d.ready := tile_bridge.io.out.tlmaster_d_ready
    tlmaster.e.valid := tile_bridge.io.out.tlmaster_e_valid
    tlmaster.e.bits.sink := tile_bridge.io.out.tlmaster_e_bits_sink
  } else {
    val bridge_emulator_blackbox = Module(new BridgeEmulatorBlackBox)
    bridge_emulator_blackbox.io.clock := clock
    bridge_emulator_blackbox.io.reset := reset.asBool
  }
}

class TileBoundaryBundleIn extends Bundle {
  val debug = Input(Bool())
  val mtip = Input(Bool())
  val msip = Input(Bool())
  val meip = Input(Bool())
  val seip = Input(Bool())
  val hartid = Input(UInt(2.W))
  val tlmaster_a_ready = Input(Bool())
  val tlmaster_b_valid = Input(Bool())
  val tlmaster_b_bits_opcode = Input(UInt(3.W))
  val tlmaster_b_bits_param = Input(UInt(2.W))
  val tlmaster_b_bits_size = Input(UInt(4.W))
  val tlmaster_b_bits_source = Input(UInt(2.W))
  val tlmaster_b_bits_address = Input(UInt(32.W))
  val tlmaster_b_bits_data = Input(UInt(64.W))
  val tlmaster_b_bits_mask = Input(UInt(8.W))
  val tlmaster_b_bits_corrupt = Input(Bool())
  val tlmaster_c_ready = Input(Bool())
  val tlmaster_d_valid = Input(Bool())
  val tlmaster_d_bits_opcode = Input(UInt(3.W))
  val tlmaster_d_bits_param = Input(UInt(2.W))
  val tlmaster_d_bits_size = Input(UInt(4.W))
  val tlmaster_d_bits_source = Input(UInt(2.W))
  val tlmaster_d_bits_sink = Input(UInt(3.W))
  val tlmaster_d_bits_denied = Input(Bool())
  val tlmaster_d_bits_data = Input(UInt(64.W))
  val tlmaster_d_bits_corrupt = Input(Bool())
  val tlmaster_e_ready = Input(Bool())
}

class TileBoundaryBundleOut extends Bundle {
  val wfi = Output(Bool())
  val tlmaster_a_valid = Output(Bool())
  val tlmaster_a_bits_opcode = Output(UInt(3.W))
  val tlmaster_a_bits_param = Output(UInt(3.W))
  val tlmaster_a_bits_size = Output(UInt(4.W))
  val tlmaster_a_bits_source = Output(UInt(2.W))
  val tlmaster_a_bits_address = Output(UInt(32.W))
  val tlmaster_a_bits_mask = Output(UInt(8.W))
  val tlmaster_a_bits_data = Output(UInt(64.W))
  val tlmaster_a_bits_corrupt = Output(Bool())
  val tlmaster_b_ready = Output(Bool())
  val tlmaster_c_valid = Output(Bool())
  val tlmaster_c_bits_opcode = Output(UInt(3.W))
  val tlmaster_c_bits_param = Output(UInt(3.W))
  val tlmaster_c_bits_size = Output(UInt(4.W))
  val tlmaster_c_bits_source = Output(UInt(2.W))
  val tlmaster_c_bits_address = Output(UInt(32.W))
  val tlmaster_c_bits_data = Output(UInt(64.W))
  val tlmaster_c_bits_corrupt = Output(Bool())
  val tlmaster_d_ready = Output(Bool())
  val tlmaster_e_valid = Output(Bool())
  val tlmaster_e_bits_sink = Output(UInt(3.W))
}

class TileBoundaryBundle extends Bundle {
  val in  = new TileBoundaryBundleIn()
  val out = new TileBoundaryBundleOut()
}


class BridgeEmulatorBlackBox extends BlackBox with HasBlackBoxResource {
  val io = IO(new Bundle {
    val clock = Input(Clock())
    val reset = Input(Bool())
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
  new chipyard.config.AbstractConfig
)
