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

  val lazyDut = LazyModule(p(BuildSystem)(p)).suggestName("TileOnlyDigitalTop")
  lazy val dut = Module(lazyDut.module)


  dut match {
    case dt :TileOnlyDigitalTopImp =>
      println("Tie off the IOs")


      val tlmaster = dt.outer.tileMasterIO.head


      dontTouch(dt.outer.intSourceIO0(0))
      dontTouch(dt.outer.intSourceIO1(0))
      dontTouch(dt.outer.intSourceIO2(0))
      dontTouch(dt.outer.intSourceIO3(0))
      dontTouch(dt.outer.intSourceIO4(0))
      dontTouch(dt.outer.hartIdSourceIO(0))
      dontTouch(dt.outer.wfiSinkIO(0))
      dontTouch(tlmaster)

      dt.outer.intSourceIO0(0).asUInt := 0.U
      dt.outer.intSourceIO1(0).asUInt := 0.U
      dt.outer.intSourceIO2(0).asUInt := 0.U
      dt.outer.intSourceIO3(0).asUInt := 0.U
      dt.outer.intSourceIO4(0).asUInt := 0.U
      dt.outer.hartIdSourceIO(0) := 0.U


      tlmaster.a.ready := false.B
      tlmaster.b.valid := false.B
      tlmaster.b.bits.opcode := 0.U
      tlmaster.b.bits.param := 0.U
      tlmaster.b.bits.size := 0.U
      tlmaster.b.bits.source := 0.U
      tlmaster.b.bits.address := 0.U
      tlmaster.b.bits.mask := 0.U
      tlmaster.b.bits.data := 0.U
      tlmaster.b.bits.corrupt := false.B
      tlmaster.c.ready := false.B
      tlmaster.d.valid := false.B
      tlmaster.d.bits.opcode := 0.U
      tlmaster.d.bits.param := 0.U
      tlmaster.d.bits.size := 0.U
      tlmaster.d.bits.source := 0.U
      tlmaster.d.bits.sink := 0.U
      tlmaster.d.bits.denied := false.B
      tlmaster.d.bits.data := 0.U
      tlmaster.d.bits.corrupt := false.B
      tlmaster.e.ready := false.B

    case _ =>
  }
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

  val hartIdSource = BundleBridgeSource(() => UInt(2.W))
  tile.hartIdNode := hartIdSource

  val beatBytes = 8
  val masterNode = TLManagerNode(Seq(TLSlavePortParameters.v1(
    managers = Seq(TLManagerParameters(
        address = Seq(AddressSet(0x20000, 0xfff)),
        regionType = RegionType.CACHED,
        executable = true,
        supportsArithmetic = TransferSizes(1, beatBytes),
        supportsLogical = TransferSizes(1, beatBytes),
        supportsGet = TransferSizes(1, 64),
        supportsPutFull = TransferSizes(1, 64),
        supportsPutPartial = TransferSizes(1, 64),
        supportsHint = TransferSizes(1, 64),
        supportsAcquireB = TransferSizes(64, 64),
        supportsAcquireT = TransferSizes(64, 64),
        fifoId = Some(0))),
    beatBytes = beatBytes,
    endSinkId = 1,
    minLatency = 1
  )))

  masterNode :=* tile.masterNode

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
  val resetSource = BundleBridgeSource(() => UInt(18.W))
  val nmiSource = BundleBridgeSource(() => new NMI(18))
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
  case MaxHartIdBits => 2
})

class TileOnlyRocketConfig extends Config(
  new chipyard.WithRawRocketTileConfig ++
  new chipyard.WithTileOnlyConfig
)
