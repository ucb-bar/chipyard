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
import freechips.rocketchip.prci.ClockSinkParameters

// Example parameter class copied from CVA6, not included in documentation but for compile check only
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
  val useHypervisor: Boolean = false
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
  val useNMI: Boolean = false
  val nPTECacheEntries: Int = 0 // TODO: Check
  val nPMPs: Int = 0 // TODO: Check
  val pmpGranularity: Int = 4 // copied from Rocket
  val nBreakpoints: Int = 0 // TODO: Check
  val useBPWatch: Boolean = false
  val mcontextWidth: Int = 0
  val scontextWidth: Int = 0
  val nPerfCounters: Int = 29
  val haveBasicCounters: Boolean = true
  val haveFSDirty: Boolean = false
  val misaWritable: Boolean = false
  val haveCFlush: Boolean = false
  val nL2TLBEntries: Int = 512 // copied from Rocket
  val nL2TLBWays: Int = 1
  val mtvecInit: Option[BigInt] = Some(BigInt(0)) // copied from Rocket
  val mtvecWritable: Boolean = true // copied from Rocket
  val instBits: Int = if (useCompressed) 16 else 32
  val lrscCycles: Int = 80 // copied from Rocket
  val decodeWidth: Int = 1 // TODO: Check
  val fetchWidth: Int = 1 // TODO: Check
  val retireWidth: Int = 2
}

// DOC include start: CanAttachTile
case class MyTileAttachParams(
  tileParams: MyTileParams,
  crossingParams: RocketCrossingParams
) extends CanAttachTile {
  type TileType = MyTile
  val lookup = PriorityMuxHartIdFromSeq(Seq(tileParams))
}
// DOC include end: CanAttachTile

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
  val clockSinkParams: ClockSinkParameters = ClockSinkParameters()
  def instantiate(crossing: TileCrossingParamsLike, lookup: LookupByHartIdImpl)(implicit p: Parameters): MyTile = {
    new MyTile(this, crossing, lookup)
  }
}

// DOC include start: Tile class
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

  // TODO: Create TileLink nodes and connections here.
  // DOC include end: Tile class

  // DOC include start: AXI4 node
  // # of bits used in TileLink ID for master node. 4 bits can support 16 master nodes, but you can have a longer ID if you need more.
  val idBits = 4
  val memAXI4Node = AXI4MasterNode(
    Seq(AXI4MasterPortParameters(
      masters = Seq(AXI4MasterParameters(
        name = "myPortName",
        id = IdRange(0, 1 << idBits))))))
  val memoryTap = TLIdentityNode() // Every bus connection should have their own tap node
  // DOC include end: AXI4 node

  // DOC include start: AXI4 convert
  (tlMasterXbar.node  // tlMasterXbar is the bus crossbar to be used when this core / tile is acting as a master; otherwise, use tlSlaveXBar
    := memoryTap
    := TLBuffer()
    := TLFIFOFixer(TLFIFOFixer.all) // fix FIFO ordering
    := TLWidthWidget(masterPortBeatBytes) // reduce size of TL
    := AXI4ToTL() // convert to TL
    := AXI4UserYanker(Some(2)) // remove user field on AXI interface. need but in reality user intf. not needed
    := AXI4Fragmenter() // deal with multi-beat xacts
    := memAXI4Node) // The custom node, see below
  // DOC include end: AXI4 convert

}

// DOC include start: Implementation class
class MyTileModuleImp(outer: MyTile) extends BaseTileModuleImp(outer){
  // annotate the parameters
  Annotated.params(this, outer.myParams)

  // TODO: Create the top module of the core and connect it with the ports in "outer"

  // If your core is in Verilog (assume your blackbox is called "MyCoreBlackbox"), instantiate it here like
  //   val core = Module(new MyCoreBlackbox(params...))
  // (as described in the blackbox tutorial) and connect appropriate signals. See the blackbox tutorial
  // (link on the top of the page) for more info.
  // You can look at https://github.com/ucb-bar/cva6-wrapper/blob/master/src/main/scala/CVA6Tile.scala
  // for a Verilog example.

  // If your core is in Chisel, you can simply instantiate the top module here like other Chisel module
  // and connect appropriate signal. You can even implement this class as your top module.
  // See https://github.com/riscv-boom/riscv-boom/blob/master/src/main/scala/common/tile.scala and
  // https://github.com/chipsalliance/rocket-chip/blob/master/src/main/scala/tile/RocketTile.scala for
  // Chisel example.

  // DOC include end: Implementation class

  // DOC include start: connect interrupt
  // For example, our core support debug interrupt and machine-level interrupt, and suppose the following two signals
  // are the interrupt inputs to the core. (DO NOT COPY this code - if your core treat each type of interrupt differently,
  // you need to connect them to different interrupt ports of your core)
  val debug_i = Wire(Bool())
  val mtip_i = Wire(Bool())
  // We create a bundle here and decode the interrupt.
  val int_bundle = new TileInterrupts()
  outer.decodeCoreInterrupts(int_bundle)
  debug_i := int_bundle.debug
  mtip_i := int_bundle.meip & int_bundle.msip & int_bundle.mtip
  // DOC include end: connect interrupt

  // DOC include start: raise interrupt
  // This is a demo. You should call these function according to your core
  // Suppose that the following signal is from the decoder indicating a WFI instruction is received.
  val wfi_o = Wire(Bool())
  outer.reportWFI(Some(wfi_o))
  // Suppose that the following signal indicate an unreconverable hardware error.
  val halt_o = Wire(Bool())
  outer.reportHalt(Some(halt_o))
  // Suppose that our core never stall for a long time / stop retiring. Use None to indicate that this interrupt never fires.
  outer.reportCease(None)
  // DOC include end: raise interrupt

  // DOC include start: AXI4 connect
  outer.memAXI4Node.out foreach { case (out, edgeOut) =>
    // Connect your module IO port to "out"
    // The type of "out" here is AXI4Bundle, which is defined in generators/rocket-chip/src/main/scala/amba/axi4/Bundles.scala
    // Please refer to this file for the definition of the ports.
    // If you are using APB, check APBBundle in generators/rocket-chip/src/main/scala/amba/apb/Bundles.scala
    // If you are using AHB, check AHBSlaveBundle or AHBMasterBundle in generators/rocket-chip/src/main/scala/amba/ahb/Bundles.scala
    // (choose one depends on the type of AHB node you create)
    // If you are using AXIS, check AXISBundle and AXISBundleBits in generators/rocket-chip/src/main/scala/amba/axis/Bundles.scala
  }
  // DOC include end: AXI4 connect

}

// DOC include start: Config fragment
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
// DOC include end: Config fragment
