package chipyard

import chisel3._
import org.chipsalliance.cde.config.{Parameters, Config, Field}
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.interrupts._
import freechips.rocketchip.tile._
import freechips.rocketchip.subsystem.RocketCrossingParams
import freechips.rocketchip.tilelink._
import freechips.rocketchip.rocket._

case object RocketTileOnly extends Field[RocketTileParams]


class TileOnlyTestHarness(implicit p: Parameters) extends Module {
  val io = IO(new Bundle {
    val success = Output(Bool())
  })
  println("Elaborating TileOnlyTestHarness")

  io.success := false.B

  val lazyDut = LazyModule(p(BuildSystem)(p)).suggestName("TileOnlyDigitalTop").asInstanceOf[TileOnlyDigitalTop]
  lazy val dut = Module(lazyDut.module)


  val tlmaster = dut.outer.tileMasterIO.head

  dontTouch(dut.outer.intSourceIO0(0))
  dontTouch(dut.outer.intSourceIO1(0))
  dontTouch(dut.outer.intSourceIO2(0))
  dontTouch(dut.outer.intSourceIO3(0))
  dontTouch(dut.outer.intSourceIO4(0))
  dontTouch(dut.outer.hartIdSourceIO(0))
  dontTouch(dut.outer.wfiSinkIO(0))
  dontTouch(tlmaster)

  dut.outer.intSourceIO0(0).asUInt := 0.U
  dut.outer.intSourceIO1(0).asUInt := 0.U
  dut.outer.intSourceIO2(0).asUInt := 0.U
  dut.outer.intSourceIO3(0).asUInt := 0.U
  dut.outer.intSourceIO4(0).asUInt := 0.U
  dut.outer.hartIdSourceIO(0) := 0.U

  val a_size = Wire(UInt(4.W))
  dontTouch(a_size)
  a_size := tlmaster.a.bits.size

  val c_size = Wire(UInt(4.W))
  dontTouch(c_size)
  c_size := tlmaster.c.bits.size

  tlmaster.a.ready := false.B
  tlmaster.b.valid := false.B
  tlmaster.b.bits.opcode := 0.U
  tlmaster.b.bits.param := 0.U
  tlmaster.b.bits.size := 0.U(4.W)
  tlmaster.b.bits.source := 0.U
  tlmaster.b.bits.address := 0.U
  tlmaster.b.bits.mask := 0.U
  tlmaster.b.bits.data := 0.U
  tlmaster.b.bits.corrupt := false.B
  tlmaster.c.ready := false.B
  tlmaster.d.valid := false.B
  tlmaster.d.bits.opcode := 0.U
  tlmaster.d.bits.param := 0.U
  tlmaster.d.bits.size := 0.U(4.W)
  tlmaster.d.bits.source := 0.U
  tlmaster.d.bits.sink := 0.U
  tlmaster.d.bits.denied := false.B
  tlmaster.d.bits.data := 0.U
  tlmaster.d.bits.corrupt := false.B
  tlmaster.e.ready := false.B
}

class TileOnlyDigitalTop()(implicit p: Parameters)
  extends LazyModule 
  with BindingScope
{
  println("Elaborating TileOnlyDigitalTop")
  println(s"p(RocketTileOnly ${p(RocketTileOnly)}")
  val tile = LazyModule(new RocketTile(p(RocketTileOnly), RocketCrossingParams(), PriorityMuxHartIdFromSeq(Seq(p(RocketTileOnly)))))

  ////////////////////////////////////////////////////////////////////////////
  // Diplomatic nodes required to punch out tile IOs
  ////////////////////////////////////////////////////////////////////////////
  val wfiSinkNode = IntSinkNode(IntSinkPortSimple())
  wfiSinkNode := tile.wfiNode

  val hartIdSource = BundleBridgeSource(() => UInt(1.W))
  tile.hartIdNode := hartIdSource


// List(TLEdgeParameters(
// TLMasterPortParameters(List(
// TLMasterParameters(Core 0 DCache, IdRange(0,1), List(), List(AddressSet(0x0, ~0x0)), Set(), false, false, P, TBALGFPH, false),
// TLMasterParameters(Core 0 DCache MMIO, IdRange(1,2), List(), List(AddressSet(0x0, ~0x0)), Set(), false, true, , TBALGFPH, false),
// TLMasterParameters(Core 0 ICache, IdRange(2,3), List(), List(AddressSet(0x0, ~0x0)), Set(), false, false, , TBALGFPH, false)),
// TLChannelBeatBytes(None,None,None,None), 0, List(), List(), List()),

// TLSlavePortParameters(List(
// TLSlaveParameters(error, List(AddressSet(0x3000, 0xfff)), List(Resource(freechips.rocketchip.diplomacy.SimpleDevice@453c008e,reg)), VOLATILE, true, Some(0), ALGFPH, PALGFPH, false, true, true),
// TLSlaveParameters(l2, List(AddressSet(0x2010000, 0xfff)), List(Resource(sifive.blocks.inclusivecache.InclusiveCache$$anon$1@54410d35,reg/control)), GET_EFFECTS, false, Some(0), ALGFP, PALGFPH, false, false, false),
// TLSlaveParameters(subsystem_pbus, List(AddressSet(0x4000, 0xfff)), List(Resource(freechips.rocketchip.diplomacy.SimpleDevice@17028dd0reg/control)), GET_EFFECTS, false, Some(0), ALGFP, PALGFPH, false, false, false),
// TLSlaveParameters(uart_0, List(AddressSet(0x54000000, 0xfff)), List(Resource(freechips.rocketchip.regmapper.RegisterRouter$$anon$1@444a2a34,reg/control)), GET_EFFECTS, false, Some(0), ALGFP, PALGFPH, false, false, false),
// TLSlaveParameters(plic, List(AddressSet(0xc000000, 0x3ffffff)), List(Resource(freechips.rocketchip.devices.tilelink.TLPLIC$$anon$2@61577a43,reg/control)), GET_EFFECTS, false, Some(0), ALGFP, PALGFPH, false, false, false),
// TLSlaveParameters(clint, List(AddressSet(0x2000000, 0xffff)), List(Resource(freechips.rocketchip.devices.tilelink.CLINT$$anon$1@3da3ebf9,reg/control)), GET_EFFECTS, false, Some(0), ALGFP, PALGFPH, false, false, false),
// TLSlaveParameters(dmInner, List(AddressSet(0x0, 0xfff)), List(Resource(freechips.rocketchip.devices.debug.TLDebugModule$$anon$5@3e4c8d90,reg/control)), GET_EFFECTS, true, Some(0), ALGFP, PALGFPH, false, false, false),
// TLSlaveParameters(bootrom, List(AddressSet(0x10000, 0xffff)), List(Resource(freechips.rocketchip.diplomacy.SimpleDevice@2bc20be7,reg/mem)), UNCACHED, true, Some(0), G, PALGFPH, false, false, false),
// TLSlaveParameters(tileClockGater, List(AddressSet(0x100000, 0xfff)), List(Resource(freechips.rocketchip.diplomacy.SimpleDevice@711de9be,reg/control)), GET_EFFECTS, false, Some(0), ALGFP, PALGFPH, false, false, false),
// TLSlaveParameters(tileResetSetter, List(AddressSet(0x110000, 0xfff)), List(Resource(freechips.rocketchip.diplomacy.SimpleDevice@53c33847,reg/control)), GET_EFFECTS, false, Some(0), ALGFP, PALGFPH, false, false, false),
// TLSlaveParameters(system, List(AddressSet(0x80000000, 0xfffffff)), List(Resource(sifive.blocks.inclusivecache.InclusiveCache$$anon$1@54410d35,caches), Resource(freechips.rocketchip.diplomacy.MemoryDevice@9391fa2,reg)), CACHED, true, None, TBALGFPH, PALGFPH, false, true, true),
// TLSlaveParameters(serdesser, List(AddressSet(0x10000000, 0xfff)), List(Resource(sifive.blocks.inclusivecache.InclusiveCache$$anon$1@54410d35,caches), Resource(freechips.rocketchip.diplomacy.SimpleDevice@329fa051,reg)), CACHED, true, None, TBALGFPH, PALGFPH, false, false, false),
// TLSlaveParameters(serdesser, List(AddressSet(0x20000, 0xffff)), List(Resource(sifive.blocks.inclusivecache.InclusiveCache$$anon$1@54410d35,caches), Resource(freechips.rocketchip.diplomacy.SimpleDevice@2c51ffa5,reg)), CACHED, true, None, BGH, PALGFPH, false, false, false)),
// TLChannelBeatBytes(Some(8),Some(8),Some(8),Some(8)), 8, 4, List(), List()), org.chipsalliance.cde.config$ChainParameters@661afb68,SourceLine(TilePRCIDomain.scala,107,52)))


  val beatBytes = p(XLen) / 8
  val masterNode = TLManagerNode(Seq(TLSlavePortParameters.v1(
    managers = Seq(
      TLManagerParameters(
        address = Seq(AddressSet(BigInt("80000000", 16), BigInt("FFFFFFF", 16))),
        regionType = RegionType.CACHED,
        executable = true,
        supportsArithmetic = TransferSizes(1, 32768),
        supportsLogical = TransferSizes(1, 32768),
        supportsGet = TransferSizes(1, 32768),
        supportsPutFull = TransferSizes(1, 32768),
        supportsPutPartial = TransferSizes(1, 32768),
        supportsHint = TransferSizes(1, 32768),
        supportsAcquireB = TransferSizes(64, 32768),
        supportsAcquireT = TransferSizes(64, 32768),
        fifoId = Some(0))
      ),
    beatBytes = beatBytes,
    endSinkId = 7,
    minLatency = 1
  )))

  masterNode := tile.masterNode

// val xbar = TLXbar()

// xbar := tile.masterNode
// masterNode := xbar

// val masterNode2 = TLManagerNode(Seq(TLSlavePortParameters.v1(
// managers = Seq(
// TLManagerParameters(
// address = Seq(AddressSet(0x10000000, BigInt("FFFF", 16))),
// regionType = RegionType.UNCACHED,
// executable = true,
// supportsArithmetic = TransferSizes(1, 65536),
// supportsLogical = TransferSizes(1, 65536),
// supportsGet = TransferSizes(1, 65536),
// supportsPutFull = TransferSizes(1, 65536),
// supportsPutPartial = TransferSizes(1, 65536),
// fifoId = Some(0))
// ),
// beatBytes = beatBytes,
// endSinkId = 0,
// minLatency = 1
// )))

// masterNode2 := xbar


  val intSourceNode0 = IntSourceNode(IntSourcePortSimple())
  val intSourceNode1 = IntSourceNode(IntSourcePortSimple())
  val intSourceNode2 = IntSourceNode(IntSourcePortSimple())
  val intSourceNode3 = IntSourceNode(IntSourcePortSimple())
  val intSourceNode4 = IntSourceNode(IntSourcePortSimple())
  tile.intInwardNode := intSourceNode0
  tile.intInwardNode := intSourceNode1
  tile.intInwardNode := intSourceNode2
  tile.intInwardNode := intSourceNode3
  tile.intInwardNode := intSourceNode4

  ////////////////////////////////////////////////////////////////////////////
  // Placeholder nodes, will be optimized out by the firrtl compiler
  ////////////////////////////////////////////////////////////////////////////
  val slaveNode = TLClientNode(Seq(TLMasterPortParameters.v1(Seq(TLClientParameters(
    name = "my-client",
    sourceId = IdRange(0, 4),
    requestFifo = true,
    visibility = Seq(AddressSet(0x10000, 0xffff)))))))
  tile.slaveNode := slaveNode

  val intSinkNode = IntSinkNode(IntSinkPortSimple())
  intSinkNode := tile.intOutwardNode

  val haltSinkNode = IntSinkNode(IntSinkPortSimple())
  val ceaseSinkNode = IntSinkNode(IntSinkPortSimple())
  val resetSource = BundleBridgeSource(() => UInt(32.W))
  val nmiSource = BundleBridgeSource(() => new NMI(32))
  haltSinkNode := tile.haltNode
  ceaseSinkNode := tile.ceaseNode
  tile.resetVectorNode := resetSource
  tile.nmiNode := nmiSource
  ////////////////////////////////////////////////////////////////////////////

  val intSourceIO0      = InModuleBody { intSourceNode0.makeIOs() }
  val intSourceIO1      = InModuleBody { intSourceNode1.makeIOs() }
  val intSourceIO2      = InModuleBody { intSourceNode2.makeIOs() }
  val intSourceIO3      = InModuleBody { intSourceNode3.makeIOs() }
  val intSourceIO4      = InModuleBody { intSourceNode4.makeIOs() }
  val hartIdSourceIO   = InModuleBody { hartIdSource.makeIOs() }
  val wfiSinkIO        = InModuleBody { wfiSinkNode.makeIOs() }
  val tileMasterIO     = InModuleBody { masterNode.makeIOs() }

  override lazy val module = new TileOnlyDigitalTopImp(this)
}

class TileOnlyDigitalTopImp(val outer: TileOnlyDigitalTop)(implicit p: Parameters)
  extends LazyModuleImp(outer) {

  val rocket_tile = outer.tile.module
}

class WithTileOnlyConfig extends Config((site, here, up) => {
  case BuildSystem => (p: Parameters) => new TileOnlyDigitalTop()(p)
})

class WithRawRocketTileConfig extends Config((site, here, up) => {
  case RocketTileOnly => RocketTileParams(
    // Placeholder, required to complete the diplomatic graph
    beuAddr = Some(BigInt("4000", 16))
  )
  case PgLevels => 3
  case XLen => 64
  case MaxHartIdBits => 1
})

class TileOnlyRocketConfig extends Config(
  new chipyard.WithRawRocketTileConfig ++
  new chipyard.WithTileOnlyConfig
)
