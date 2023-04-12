package chipyard

import chisel3._
import chisel3.experimental.{IO, DataMirror}
import chisel3.util._
import org.chipsalliance.cde.config.{Parameters, Config, Field}
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.interrupts._
import chipyard.iobinders._
import freechips.rocketchip.tile._
import freechips.rocketchip.subsystem.RocketCrossingParams
import freechips.rocketchip.tilelink._
import testchipip._

case object RocketTileOnly extends Field[RocketTileParams]



class TileOnlyTestHarness(implicit p: Parameters) extends Module {
  val io = IO(new Bundle {
    val success = Output(Bool())
  })
  println("Elaborating TileOnlyTestHarness")
  val lazyDut = LazyModule(new TileOnlyChipTop).suggestName("tileOnlyChipTop")
  val dut = Module(lazyDut.module)
}




class TileOnlyChipTop(implicit p: Parameters) extends LazyModule with BindingScope {
  println("Elaborating TileOnlyChipTop")
  lazy val lazySystem = LazyModule(p(BuildSystem)(p)).suggestName("TileOnlySystem")
  lazy val module: LazyModuleImpLike = new LazyRawModuleImp(this) { }

  // Do not remove this, this triggers the lazy evaluation of TileOnlyDigitalTop
  println(lazySystem.name)
}



trait CanHaveTLMasterPunchThrough { this: TileOnlyDigitalTop =>
  println("Elaborating CanHaveTLMasterPunchThrough")
  val beatBytes = 8

  val slaveNode = TLManagerNode(Seq(TLSlavePortParameters.v1(Seq(TLManagerParameters(
    address = Seq(AddressSet(0x20000, 0xfff)),
    regionType = RegionType.UNCACHED,
    executable = true,
    supportsArithmetic = TransferSizes(1, beatBytes),
    supportsLogical = TransferSizes(1, beatBytes),
    supportsGet = TransferSizes(1, beatBytes),
    supportsPutFull = TransferSizes(1, beatBytes),
    supportsPutPartial = TransferSizes(1, beatBytes),
    supportsHint = TransferSizes(1, beatBytes),
    fifoId = Some(0))), beatBytes)))

  // Do not remove this, this triggers the lazy evaluation of TileOnlyDigitalTop
  println(this.name)
  println(this.tile.name)

  val node = TLIdentityNode()
  node := this.tile.masterNode.outward
  slaveNode := node

  val tile_ios = InModuleBody {
    slaveNode.makeIOs()
  }
}

class TileOnlyDigitalTop()(implicit p: Parameters)
  extends LazyModule {
  // with CanHaveTLMasterPunchThrough {
  println("Elaborating TileOnlyDigitalTop")
  println(s"p(RocketTileOnly ${p(RocketTileOnly)}")

  val tile = LazyModule(new RocketTile(p(RocketTileOnly), RocketCrossingParams(), PriorityMuxHartIdFromSeq(Seq(p(RocketTileOnly)))))
  println(tile.name)

  val beatBytes = 8
  val slaveNode = TLManagerNode(Seq(TLSlavePortParameters.v1(
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

  slaveNode :=* tile.masterNode

  println(tile.masterNode.edges)

   val masterNode = TLClientNode(Seq(TLMasterPortParameters.v1(Seq(TLClientParameters(
      name = "my-client",
      sourceId = IdRange(0, 4),
      requestFifo = true,
      visibility = Seq(AddressSet(0x10000, 0xffff)))))))

  masterNode :=* tile.slaveNode

  val intSinkNode = IntSinkNode(IntSinkPortSimple())
  val intSourceNode = IntSourceNode(IntSourcePortSimple())
  intSinkNode := tile.intOutwardNode
  tile.intInwardNode := intSourceNode


  val hartIdSource = BundleBridgeSource(() => UInt(8.W))
  tile.hartIdNode := hartIdSource

  val resetSource = BundleBridgeSource(() => UInt(18.W))
  tile.resetVectorNode := resetSource
  // tile.resetVectorNode.inward := resetSource
  // val resetSink = BundleBridgeSink(Some(() => UInt(64.W)))
  // tile.resetVectorSinkNode :*= resetSource
  // resetSink :=* tile.resetVectorSinkNode

  val nmiSource = BundleBridgeSource(() => new NMI(18))
  tile.nmiNode := nmiSource


  InModuleBody {
    slaveNode.makeIOs()
    masterNode.makeIOs()
    intSinkNode.makeIOs()
    hartIdSource.makeIOs()
    // resetSource.makeIOs()
  //   resetSink.makeIOs()
//    intSlaveNode.makeIOs()
  }


  override lazy val module = new TileOnlyDigitalTopImp(this)
}

class TileOnlyDigitalTopImp(outer: TileOnlyDigitalTop)(implicit p: Parameters) extends LazyModuleImp(outer) {
  println("TileOnlyDigitalTopImp")
  val rocket_tile = outer.tile.module

  val (master, edge) = outer.tile.masterNode.out.head
  println(outer.tile.masterNode.outward)
  println(master)
  println(edge)
}


class WithTLMasterPunchThrough extends OverrideLazyIOBinder({
  (system: CanHaveTLMasterPunchThrough) => {
    println("WithTLMasterPunchThrough")
    implicit val p: Parameters = GetSystemParameters(system)

    InModuleBody {
      val ports: Seq[ClockedAndResetIO[TLBundle]] = system.tile_ios.zipWithIndex.map({ case (m, i) =>
        val p = IO(new ClockedAndResetIO(DataMirror.internal.chiselTypeClone[TLBundle](m))).suggestName(s"tl_master_${i}")
        p.bits <> m
        // p.clock := ???
        // p.reset := ???
        p
      }).toSeq
      (ports, Nil)
    }
  }
})


class WithTileOnlyTop extends Config((site, here, up) => {
  case BuildSystem => (p: Parameters) => new TileOnlyDigitalTop()(p)
})

class WithRawRocketTileConfig extends Config((site, here, up) => {
  case RocketTileOnly => RocketTileParams()
  case XLen => 32
  case MaxHartIdBits => 2
})


class TileOnlyRocketConfig extends Config(
//  new chipyard.WithTLMasterPunchThrough ++
  new chipyard.WithRawRocketTileConfig ++
  new chipyard.WithTileOnlyTop
)