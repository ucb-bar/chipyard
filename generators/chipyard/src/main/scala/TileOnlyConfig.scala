package chipyard

import chisel3._
import org.chipsalliance.cde.config.{Parameters, Config, Field}
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.interrupts._
import freechips.rocketchip.tile._
import freechips.rocketchip.subsystem.RocketCrossingParams
import freechips.rocketchip.tilelink._

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
      dontTouch(tlmaster)

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

      val tlslave = dt.outer.tileSlaveIO.head
      dontTouch(tlslave)
      tlslave.a.valid := false.B
      tlslave.a.bits.opcode := TLMessages.Get
      tlslave.a.bits.param := 0.U
      tlslave.a.bits.size := 0.U
      tlslave.a.bits.source := 0.U
      tlslave.a.bits.address := 0.U
      tlslave.a.bits.mask := 0.U
      tlslave.a.bits.data := 0.U
      tlslave.a.bits.corrupt := false.B

      tlslave.b.ready := false.B


      tlslave.c.valid := false.B
      tlslave.c.bits.opcode := 0.U
      tlslave.c.bits.param := 0.U
      tlslave.c.bits.size := 0.U
      tlslave.c.bits.source := 0.U
      tlslave.c.bits.address := 0.U
      tlslave.c.bits.data := 0.U
      tlslave.c.bits.corrupt := false.B

      tlslave.d.ready := false.B

      tlslave.e.valid := false.B
      tlslave.e.bits.sink := 0.U

      dontTouch(dt.outer.intSourceIO(0))
      dontTouch(dt.outer.hartIdSourceIO(0))
      dontTouch(dt.outer.resetVecSourceIO(0))
      dontTouch(dt.outer.nmiSourceIO.head)

      dt.outer.intSourceIO(0).asUInt := 0.U
      dt.outer.hartIdSourceIO(0) := 0.U
      dt.outer.resetVecSourceIO(0) := 0.U

      dt.outer.nmiSourceIO.head.rnmi := false.B
      dt.outer.nmiSourceIO.head.rnmi_interrupt_vector := 0.U
      dt.outer.nmiSourceIO.head.rnmi_exception_vector := 0.U

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
  println(tile.name)

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

  println(tile.masterNode.edges)

  val slaveNode = TLClientNode(Seq(TLMasterPortParameters.v1(Seq(TLClientParameters(
     name = "my-client",
     sourceId = IdRange(0, 4),
     requestFifo = true,
     visibility = Seq(AddressSet(0x10000, 0xffff)))))))

  tile.slaveNode := slaveNode

  val intSinkNode = IntSinkNode(IntSinkPortSimple())
  intSinkNode := tile.intOutwardNode

  val intSourceNode = IntSourceNode(IntSourcePortSimple())
  tile.intInwardNode := intSourceNode

  val haltSinkNode = IntSinkNode(IntSinkPortSimple())
  haltSinkNode := tile.haltNode

  val ceaseSinkNode = IntSinkNode(IntSinkPortSimple())
  ceaseSinkNode := tile.ceaseNode

  val wfiSinkNode = IntSinkNode(IntSinkPortSimple())
  wfiSinkNode := tile.wfiNode

  val hartIdSource = BundleBridgeSource(() => UInt(2.W))
  tile.hartIdNode := hartIdSource

  val resetSource = BundleBridgeSource(() => UInt(18.W))
  tile.resetVectorNode := resetSource

  val nmiSource = BundleBridgeSource(() => new NMI(18))
  tile.nmiNode := nmiSource


  val tileMasterIO     = InModuleBody { println("inmodulebody"); masterNode.makeIOs() }
  val tileSlaveIO      = InModuleBody { slaveNode.makeIOs() }
  val intSinkIO        = InModuleBody { intSinkNode.makeIOs() }
  val intSourceIO      = InModuleBody { intSourceNode.makeIOs() }
  val haltSinkIO       = InModuleBody { haltSinkNode.makeIOs() }
  val ceasesSinkIO     = InModuleBody { ceaseSinkNode.makeIOs() }
  val wfiSinkIO        = InModuleBody { wfiSinkNode.makeIOs() }
  val hartIdSourceIO   = InModuleBody { hartIdSource.makeIOs() }
  val resetVecSourceIO = InModuleBody { resetSource.makeIOs() }
  val nmiSourceIO      = InModuleBody { nmiSource.makeIOs() }

  override lazy val module = new TileOnlyDigitalTopImp(this)
}

class TileOnlyDigitalTopImp(val outer: TileOnlyDigitalTop)(implicit p: Parameters) extends LazyModuleImp(outer) {
  val rocket_tile = outer.tile.module

  val (master, edge) = outer.tile.masterNode.out.head
  println(outer.tile.masterNode.outward)
  println(master)
  println(edge)
}

class WithTileOnlyConfig extends Config((site, here, up) => {
  case BuildSystem => (p: Parameters) => new TileOnlyDigitalTop()(p)
})

class WithRawRocketTileConfig extends Config((site, here, up) => {
  case RocketTileOnly => RocketTileParams(
    beuAddr = Some(BigInt("4000", 16))
  )
  case XLen => 32
  case MaxHartIdBits => 2
})


class TileOnlyRocketConfig extends Config(
  new chipyard.WithRawRocketTileConfig ++
  new chipyard.WithTileOnlyConfig
)
