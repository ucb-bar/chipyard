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

class TileOnlyChipTop(implicit p: Parameters) extends LazyModule
  with BindingScope 
  with HasIOBinders
{
  println("Elaborating TileOnlyChipTop")
  lazy val lazySystem = LazyModule(p(BuildSystem)(p)).suggestName("TileOnlySystem")
  lazy val module: LazyModuleImpLike = new LazyRawModuleImp(this) { }

  // Do not remove this, this triggers the lazy evaluation of TileOnlyDigitalTop
  println(lazySystem.name)
}

class TileOnlyDigitalTop()(implicit p: Parameters)
  extends LazyModule 
  with HasTileSlavePort
  with HasTileMasterPort
  with HasTileIntSinkPort
  with HasTileIntSourcePort
  with HasTileHaltSinkPort
  with HasTileCeaseSinkPort
  with HasTileWFISinkPort
  with HasTileHartIdSourcePort
  with HasTileResetSourcePort
  with HasTileNMISourcePort 
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

trait HasTileSlavePort { this: TileOnlyDigitalTop =>
  val slave = InModuleBody {
    slaveNode.makeIOs()
  }
}

trait HasTileMasterPort { this :TileOnlyDigitalTop =>
  val master = InModuleBody {
    masterNode.makeIOs()
  }
}

trait HasTileIntSinkPort { this: TileOnlyDigitalTop =>
  val intSink = InModuleBody {
    intSinkNode.makeIOs()
  }
}

trait HasTileIntSourcePort { this: TileOnlyDigitalTop =>
  val intSource = InModuleBody {
    intSourceNode.makeIOs()
  }
}

trait HasTileHaltSinkPort { this: TileOnlyDigitalTop =>
  val halt = InModuleBody {
    haltSinkNode.makeIOs()
  }
}

trait HasTileCeaseSinkPort { this: TileOnlyDigitalTop =>
  val cease = InModuleBody {
    ceaseSinkNode.makeIOs()
  }
}

trait HasTileWFISinkPort { this: TileOnlyDigitalTop =>
  val wfi = InModuleBody {
    wfiSinkNode.makeIOs()
  }
}

trait HasTileHartIdSourcePort { this: TileOnlyDigitalTop =>
  val hart = InModuleBody {
    hartIdSource.makeIOs()
  }
}

trait HasTileResetSourcePort { this: TileOnlyDigitalTop =>
  val resetVec = InModuleBody {
    resetSource.makeIOs()
  }
}

trait HasTileNMISourcePort { this: TileOnlyDigitalTop =>
  val nmi = InModuleBody {
    nmiSource.makeIOs()
  }
}

class WithTileOnlyConfig extends Config((site, here, up) => {
  case BuildTop => (p: Parameters) => new TileOnlyChipTop()(p)
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
  new chipyard.harness.WithTileOnlyHarnessBinders ++
  new chipyard.iobinders.WithTileOnlyIOBinders ++
  new chipyard.WithRawRocketTileConfig ++
  new chipyard.WithTileOnlyConfig
)
