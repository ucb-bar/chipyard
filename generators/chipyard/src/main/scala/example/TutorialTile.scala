package chipyard.example

import chisel3._
import chisel3.util._

import freechips.rocketchip.config._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.devices.tilelink._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.diplomaticobjectmodel.logicaltree.{LogicalTreeNode}
import freechips.rocketchip.rocket._
import freechips.rocketchip.subsystem.{RocketCrossingParams}
import freechips.rocketchip.tilelink._
import freechips.rocketchip.interrupts._
import freechips.rocketchip.util._
import freechips.rocketchip.tile._
import freechips.rocketchip.amba.axi4._

// Example parameter class copied from Ariane, not included in documentation but for compile check only
// If you are here for documentation, DO NOT copy MyCoreParams and MyTileParams directly - always figure
// out what parameters you need before you write the parameter class
case class MyCoreParams(
  bootFreqHz: BigInt = BigInt(1700000000),
  rasEntries: Int = 4,
  btbEntries: Int = 16,
  bhtEntries: Int = 16,
  enableToFromHostCaching: Boolean = false,
) extends CoreParams {
  val useVM: Boolean = true
  val useUser: Boolean = true
  val useSupervisor: Boolean = false
  val useDebug: Boolean = true
  val useAtomics: Boolean = true
  val useAtomicsOnlyForIO: Boolean = false // copied from Rocket
  val useCompressed: Boolean = true
  override val useVector: Boolean = false
  val useSCIE: Boolean = false
  val useRVE: Boolean = false
  val mulDiv: Option[MulDivParams] = Some(MulDivParams()) // copied from Rocket
  val fpu: Option[FPUParams] = Some(FPUParams()) // copied fma latencies from Rocket
  val nLocalInterrupts: Int = 0
  val nPMPs: Int = 0 // TODO: Check
  val pmpGranularity: Int = 4 // copied from Rocket
  val nBreakpoints: Int = 0 // TODO: Check
  val useBPWatch: Boolean = false
  val nPerfCounters: Int = 29
  val haveBasicCounters: Boolean = true
  val haveFSDirty: Boolean = false
  val misaWritable: Boolean = false
  val haveCFlush: Boolean = false
  val nL2TLBEntries: Int = 512 // copied from Rocket
  val mtvecInit: Option[BigInt] = Some(BigInt(0)) // copied from Rocket
  val mtvecWritable: Boolean = true // copied from Rocket
  val instBits: Int = if (useCompressed) 16 else 32
  val lrscCycles: Int = 80 // copied from Rocket
  val decodeWidth: Int = 1 // TODO: Check
  val fetchWidth: Int = 1 // TODO: Check
  val retireWidth: Int = 2
}

case class MyTileAttachParams(
  tileParams: MyTileParams,
  crossingParams: RocketCrossingParams
) extends CanAttachTile {
  type TileType = MyTile
  val lookup = PriorityMuxHartIdFromSeq(Seq(tileParams))
}

case class MyTileParams(
  name: Option[String] = Some("my_tile"),
  hartId: Int = 0,
  trace: Boolean = false,
  val core: MyCoreParams = MyCoreParams()
) extends InstantiableTileParams[MyTile]
{
  val beuAddr: Option[BigInt] = None
  val blockerCtrlAddr: Option[BigInt] = None
  val btb: Option[BTBParams] = Some(BTBParams())
  val boundaryBuffers: Boolean = false
  val dcache: Option[DCacheParams] = Some(DCacheParams())
  val icache: Option[ICacheParams] = Some(ICacheParams())
  def instantiate(crossing: TileCrossingParamsLike, lookup: LookupByHartIdImpl)(implicit p: Parameters): MyTile = {
    new MyTile(this, crossing, lookup)
  }
}

class MyTile(
  val myParams: MyTileParams,
  crossing: ClockCrossingType,
  lookup: LookupByHartIdImpl,
  q: Parameters)
  extends BaseTile(myParams, crossing, lookup, q)
  with SinksExternalInterrupts
  with SourcesExternalNotifications
{

  // Private constructor ensures altered LazyModule.p is used implicitly
  def this(params: MyTileParams, crossing: TileCrossingParamsLike, lookup: LookupByHartIdImpl)(implicit p: Parameters) =
    this(params, crossing.crossingType, lookup, p)

  // Require TileLink nodes
  val intOutwardNode = IntIdentityNode()
  val masterNode = visibilityNode
  val slaveNode = TLIdentityNode()

  // Implementation class (See below)
  override lazy val module = new MyTileModuleImp(this)

  // Required entry of CPU device in the device tree for interrupt purpose
  val cpuDevice: SimpleDevice = new SimpleDevice("cpu", Seq("my-organization,my-cpu", "riscv")) {
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

  // (Connection to bus, interrupt, etc.)
  // # of bits used in TileLink ID for master node. 4 bits can support 16 master nodes, but you can have a longer ID if you need more. 
  val idBits = 4
  val memAXI4Node = AXI4MasterNode(
    Seq(AXI4MasterPortParameters(
      masters = Seq(AXI4MasterParameters(
        name = "myPortName",
        id = IdRange(0, 1 << idBits))))))
  val memoryTap = TLIdentityNode() // Every bus connection should have their own tap node
  (tlMasterXbar.node  // tlMasterXbar is the bus crossbar to be used when this core / tile is acting as a master; otherwise, use tlSlaveXBar
    := memoryTap
    := TLBuffer()
    := TLFIFOFixer(TLFIFOFixer.all) // fix FIFO ordering
    := TLWidthWidget(masterPortBeatBytes) // reduce size of TL
    := AXI4ToTL() // convert to TL
    := AXI4UserYanker(Some(2)) // remove user field on AXI interface. need but in reality user intf. not needed
    := AXI4Fragmenter() // deal with multi-beat xacts
    := memAXI4Node) // The custom node, see below
}

class MyTileModuleImp(outer: MyTile) extends BaseTileModuleImp(outer){
  // annotate the parameters
  Annotated.params(this, outer.myParams)

  // TODO: Create the top module of the core and connect it with the ports in "outer"

  outer.memAXI4Node.out foreach { case (out, edgeOut) =>
    // Connect your module IO port to "out"
    // The type of "out" here is AXI4Bundle, which is defined in generators/rocket-chip/src/main/scala/amba/axi4/Bundles.scala
    // Please refer to this file for the definition of the ports.
    // If you are using APB, check APBBundle in generators/rocket-chip/src/main/scala/amba/apb/Bundles.scala
    // If you are using AHB, check AHBSlaveBundle or AHBMasterBundle in generators/rocket-chip/src/main/scala/amba/ahb/Bundles.scala
    // (choose one depends on the type of AHB node you create)
    // If you are using AXIS, check AXISBundle and AXISBundleBits in generators/rocket-chip/src/main/scala/amba/axis/Bundles.scala
  }
}

class WithNMyCores(n: Int = 1, overrideIdOffset: Option[Int] = None) extends Config((site, here, up) => {
  case TilesLocated(InSubsystem) => {
    // Calculate the next available hart ID (since hart ID cannot be duplicated)
    val prev = up(TilesLocated(InSubsystem), site)
    val idOffset = overrideIdOffset.getOrElse(prev.size)
    // Create TileAttachParams for every core to be instantiated
    (0 until n).map { i =>
      MyTileAttachParams(
        tileParams = MyTileParams(hartId = i + idOffset),
        crossingParams = RocketCrossingParams()
      )
    } ++ prev
  }
  // Configurate # of bytes in one memory / IO transaction. For RV64, one load/store instruction can transfer 8 bytes at most.
  case SystemBusKey => up(SystemBusKey, site).copy(beatBytes = 8)
  // The # of instruction bits. Use maximum # of bits if your core supports both 32 and 64 bits.
  case XLen => 64
})
