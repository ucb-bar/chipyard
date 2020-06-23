.. _custom_core:

Adding a custom core
====================

You may want to add a custom RISC-V core to Chipyard generator. If the top module of your core is not in Chisel, 
you will first need to create a Verilog blackbox for it. See ::ref:`_incorporating-verilog-blocks` for instructions.
Once you have a top module in Chisel, you are ready to create integrate it with Chipyard. 

.. note:: 

    RoCC is not supported by custom core currently. Please use Rocket or Boom if you need to use RoCC.

Parameter Case Classes
----------------------

Chipyard will generate a core for every ``TileParams`` object it discovered in the current config.
``TileParams`` is a trait containing the information needed to create a tile, and every custom core must implement
their own version of ``TileParams``, as well as ``CoreParams`` which is passed as a field in ``TileParams``.

``TileParams`` holds the parameters that are the same for every generated core, while ``CoreParams`` contains those
that can vary from cores to cores. They must be implemented as case classes with fields that can be overridden by 
other config fragments as the constructor parameters. See the appendix at the bottom of the page for a list of 
variable to be implemented. You can also add custom fields to them, but standard fields should always be preferred. 

Now you have your parameter classes, you will need config keys to hold them. There are two required keys:

.. code-block:: scala

    case object MyTilesKey extends Field[Seq[MyTileParams]](Nil)
    case object MyCrossingKey extends Field[Seq[RocketCrossingParams]](List(RocketCrossingParams())) 

``MyCrossingKey`` here is used to store information about the clock-crossing behavior of the core, and it is normally
set to its default values. 

``TileParams`` and ``CoreParams`` contains the following fields (you may ignore any fields marked "Rocket specific" and 
use their default values, although it is recommended to use them if you need a custom field with similar purposes) :

.. code-block:: scala

    trait TileParams {
      val core: CoreParams                  // Core parameters (see below)
      val icache: Option[ICacheParams]      // Rocket specific: I1 cache option
      val dcache: Option[DCacheParams]      // Rocket specific: D1 cache option
      val btb: Option[BTBParams]            // Rocket specific: BTB / branch predictor option
      val hartId: Int                       // Hart ID: Must be unique within a design config
      val beuAddr: Option[BigInt]           // Rocket specific: Bus Error Unit for Rocket Core
      val blockerCtrlAddr: Option[BigInt]   // Rocket specific: Bus Blocker for Rocket Core
      val name: Option[String]              // Name of the core
    }

    trait CoreParams {
      val bootFreqHz: BigInt              // Frequency
      val useVM: Boolean                  // Support virtual memory
      val useUser: Boolean                // Support user mode
      val useSupervisor: Boolean          // Support supervisor mode
      val useDebug: Boolean               // Support RISC-V debug specs
      val useAtomics: Boolean             // Support A extension
      val useAtomicsOnlyForIO: Boolean    // Support A extension for memory-mapped IO (may be true even if useAtomics is false)
      val useCompressed: Boolean          // Support C extension
      val useVector: Boolean = false      // Support V extension
      val useSCIE: Boolean                // Support custom instructions (in custom-0 and custom-1)
      val useRVE: Boolean                 // Use E base ISA
      val mulDiv: Option[MulDivParams]    // *Rocket specific: M extension related setting (Use Some(MulDivParams()) to indicate M extension supported)
      val fpu: Option[FPUParams]          // F and D extensions and related setting (see below)
      val fetchWidth: Int                 // Max # of insts fetched every cycle
      val decodeWidth: Int                // Max # of insts decoded every cycle
      val retireWidth: Int                // Max # of insts retired every cycle
      val instBits: Int                   // Instruction bits (if 32 bit and 64 bit are both supported, use 64)
      val nLocalInterrupts: Int           // # of local interrupts (see SiFive interrupt cookbook)
      val nPMPs: Int                      // # of Physical Memory Protection units
      val pmpGranularity: Int             // Size of the smallest unit of region for PMP unit (must be power of 2)
      val nBreakpoints: Int               // # of hardware breakpoints supported (in RISC-V debug specs)
      val useBPWatch: Boolean             // Support hardware breakpoints
      val nPerfCounters: Int              // # of supported performance counters
      val haveBasicCounters: Boolean      // Support basic counters defined in the RISC-V counter extension 
      val haveFSDirty: Boolean            // If true, the core will set FS field in mstatus CSR to dirty when appropriate
      val misaWritable: Boolean           // Support writable misa CSR (like variable instruction bits)
      val haveCFlush: Boolean             // Rocket specific: enables Rocket's custom instruction extension to flush the cache
      val nL2TLBEntries: Int              // # of L2 TLB entries
      val mtvecInit: Option[BigInt]       // mtvec CSR (of V extension) initial value
      val mtvecWritable: Boolean          // If mtvec CSR is writable

      // Normally, you don't need to change these values (except lrscCycles) 
      def customCSRs(implicit p: Parameters): CustomCSRs = new CustomCSRs

      def hasSupervisorMode: Boolean = useSupervisor || useVM
      def instBytes: Int = instBits / 8
      def fetchBytes: Int = fetchWidth * instBytes
      // Rocket specific: Longest possible latency of Rocket core D1 cache. Simply set it to the default value 80.
      def lrscCycles: Int

      def dcacheReqTagBits: Int = 6

      def minFLen: Int = 32
      def vLen: Int = 0
      def sLen: Int = 0
      def eLen(xLen: Int, fLen: Int): Int = xLen max fLen
      def vMemDataBits: Int = 0
    }

    case class FPUParams(
      minFLen: Int = 32,          // Minimum floating point length (no need to change) 
      fLen: Int = 64,             // Maximum floating point length, use 32 if only single precision is supported
      divSqrt: Boolean = true,    // Div/Sqrt operation supported
      sfmaLatency: Int = 3,       // Rocket specific: Fused multiply-add pipeline latency (single precision)
      dfmaLatency: Int = 4        // Rocket specific: Fused multiply-add pipeline latency (double precision)
    )

Most of the fields here are originally designed for Rocket core and contains some architecture-specific details, but 
many of them are general enough to be useful for other cores. It is strongly recommended to use these fields instead
of creating your own custom fields when applicable.

Tile Class
----------

In Chipyard, all connections with other components on SoC are defined a core's `Tile` class, while the implementation 
of the actual hardware are in the implementation class. This structure allows Chipyard to use the Diplomacy framework
to resolve paramters and connections before elaboration. 

All tile classes implement ``BaseTile`` and will normally implement ``SinksExternalInterrupts`` and ``SourcesExternalNotifications``,
which allow the tile to accept external interrupt. A typical tile has the following form:

.. code-block:: scala

    class MyTile(
      val myParams: MyTileParams,
      crossing: ClockCrossingType,
      lookup: LookupByHartIdImpl,
      q: Parameters,
      logicalTreeNode: LogicalTreeNode)
      extends BaseTile(myParams, crossing, lookup, q)
      with SinksExternalInterrupts
      with SourcesExternalNotifications
    {

      // Private constructor ensures altered LazyModule.p is used implicitly
      def this(params: MyTileParams, crossing: RocketCrossingParams, lookup: LookupByHartIdImpl, logicalTreeNode: LogicalTreeNode)(implicit p: Parameters) =
        this(params, crossing.crossingType, lookup, p, logicalTreeNode)

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
    }

TileLink Connection
-------------------

Chipyard use TileLink as its onboard bus protocol, and if your core doesn't use TileLink, you will need to convert them
in the tile class. Below is an example of how to connect a core using AXI4 to the TileLink bus: 

.. code-block:: scala

    val memoryTap = TLIdentityNode() // Every bus connection should have their own tap node
    (tlMasterXbar.node  // tlMasterXbar is the bus crossbar to be used when this core / tile is acting as a master; otherwise, use tlSlaveXBar
      := memoryTap
      := TLBuffer()
      := TLFIFOFixer(TLFIFOFixer.all) // fix FIFO ordering
      := TLWidthWidget(beatBytes) // reduce size of TL
      := AXI4ToTL() // convert to TL
      := AXI4UserYanker(Some(2)) // remove user field on AXI interface. need but in reality user intf. not needed
      := AXI4Fragmenter() // deal with multi-beat xacts
      := memAXI4Node) // The custom node, see below

Remember, you may not need all of these intermediate widgets. See :::ref:`Diplomatic-Widgets` for the meaning of each intermediate
widget. If you are using TileLink, then you only need the tap node and the TileLink node used by your components. Also, Chipyard
support AHB, APB and AXIS, and most of the AXI4 widgets has equivalent widget for these bus protocol. See the reference page for
more info. 

``memAXI4Node`` is an AXI4 master node and is defined as following in our example:

.. code-block:: scala

    val memAXI4Node = AXI4MasterNode(
    Seq(AXI4MasterPortParameters(
      masters = Seq(AXI4MasterParameters(
        name = portName,
        id = IdRange(0, 1 << idBits))))))

where ``portName`` and ``idBits`` (number of bits to represent a port ID) are the parameter provides by the tile.
Make sure to read :::ref:`node-tyoes` to check out what type of nodes Chipyard supports and their parameters!

Also, by default, there are boundary buffers for both master and slave connections to the bus when they are leaving the tile, and you
can override the following two functions to control how to buffer the bus requests/responses:

.. code-block:: scala

    protected def makeMasterBoundaryBuffers(implicit p: Parameters): TLBuffer
    protected def makeSlaveBoundaryBuffers(implicit p: Parameters): TLBuffer

You can find more information on ``TLBuffer`` in :::ref:`Diplomatic-Widgets`.

Interrupt
---------

Chipyard allows a tile to either receive interrupts from other devices or initiate interrupts to notify other cores/devices. 
In the tile that inherited ``SinksExternalInterrupts``, one can create a ``TileInterrupts`` object (a Chisel bundle) and 
call ``decodeCoreInterrupts`` with the object as the argument. You can then read the interrupt bits from the object.
The definition of ``TileInterrupts`` is 

.. code-block:: scala

    class TileInterrupts(implicit p: Parameters) extends CoreBundle()(p) {
      val debug = Bool() // debug interrupt
      val mtip = Bool() // Machine level timer interrupt
      val msip = Bool() // Machine level software interrupt
      val meip = Bool() // Machine level external interrupt 
      val seip = usingSupervisor.option(Bool()) // Valid only if supervisor mode is supported
      val lip = Vec(coreParams.nLocalInterrupts, Bool())  // Local interrupts
    }

This function should be in the implementation class since it involves hardware generation. 
Also, the tile can also notify other cores or devices for some events by calling following functions (in implementation class):

.. code-block:: scala

    def reportHalt(could_halt: Option[Bool]) // Triggered when there is an unrecoverable hardware error (halt the machine)
    def reportHalt(errors: Seq[CanHaveErrors]) // Varient for standard error bundle (used only by cache when there's an ECC error)
    reportCease(could_cease: Option[Bool], quiescenceCycles: Int = 8) // Triggered when the core stop retiring instructions (like clock gating)
    reportWFI(could_wfi: Option[Bool]) // Triggered when a WFI instruction is executed

Trace (Optional)
----------------

Chipyard provides a set of ports for instruction trace that conforms with related RISC-V standard.
If you are using FireSim, it is recommended to implement these trace ports to enable FireSim to read trace. 

There are one inbound node ``traceAuxSinkNode.bundle: TraceAux`` and two outbound nodes ``traceCoreSourceNode.bundle: TraceCoreInterface``
and ``bpwatchSourceNode.bundle: Vec[BPWatch]``. Note that the length of ``bpwatchSourceNode`` is equal to the max number of 
breakpoints (set by ``nBreakpoints`` in ``CoreParams``). Below is the definition of these types:

.. code-block:: scala

    // Control signal from the external tracer
    class TraceAux extends Bundle {
      val enable = Bool()   // Enable trace output
      val stall = Bool()    // If true, the core should stall
    }
    // Check RISC-V Processor Trace spec V1.0 for more information of this interface
    class TraceCoreInterface (val params: TraceCoreParams) extends Bundle {
      val group = Vec(params.nGroups, new TraceCoreGroup(params))
      val priv = UInt(4.W)
      val tval = UInt(params.xlen.W)
      val cause = UInt(params.xlen.W)
    }
    // Address Breakpoint and watchpoint info (n is the retire width)
    class BPWatch (val n: Int) extends Bundle() {
      val valid = Vec(n, Bool())    // Valid bit of the output
      val rvalid = Vec(n, Bool())   // Break on read
      val wvalid = Vec(n, Bool())   // Break on write
      val ivalid = Vec(n, Bool())   // Break on execute
      val action = UInt(3.W)        // Exception code (3 usually) 
    }

Implementation Class
--------------------

The implementation class is of the following form:

.. code-block:: scala

    class MyTileModuleImp(outer: MyTile) extends BaseTileModuleImp(outer){
      // annotate the parameters
      Annotated.params(this, outer.tileParams)

      // TODO: Create the top module of the core and connect it with the ports in "outer"
    }

In the body of this class, you can look up any parameters by calling ``p({key})``, where ``{key}`` is the config key of 
the value you want to look up. For a list of available keys, see the appendix below.

If you create an AXI4 node (or equivalents), you will need to connect them to your core. You can connect a port like this:

.. code-block:: scala

    outer.myAXI4Node.out foreach { case (out, edgeOut) =>
      // Connect your module IO port to "out"
      // The type of "out" here is AXI4Bundle, which is defined in generators/rocket-chip/src/main/scala/amba/axi4/Bundles.scala
      // Please refer to this file for the definition of the ports.
      // If you are using APB, check APBBundle in generators/rocket-chip/src/main/scala/amba/apb/Bundles.scala
      // If you are using AHB, check AHBSlaveBundle or AHBMasterBundle in generators/rocket-chip/src/main/scala/amba/ahb/Bundles.scala
      // (choose one depends on the type of AHB node you create)
      // If you are using AXIS, check AXISBundle and AXISBundleBits in generators/rocket-chip/src/main/scala/amba/axis/Bundles.scala
    }

Integrate the Core
------------------

To use your core in a set of config, you would need a config fragment that would create a ``TileParams`` object of your core in
the current config. An example of such config will be like this:

.. code-block:: scala

    class WithNMyCores(n: Int) extends Config(
      new RegisterCore(new CoreEntry[MyTileParams, MyTile]("MyCore", MyTilesKey, MyCrossingKey)) ++
      new Config((site, here, up) => {
        case MyTilesKey => {
          List.tabulate(n)(i => MyTileParams(hartId = i))
        }
      })
    )

Where ``RegisterCore`` will register the core with chipyard so that it can be recognized by generic config. This is required for 
all custom cores. You can also create other config fragments to change other parameters. 

Now you have finished all the steps to prepare your cores for Chipyard! To generate the custom core, simply follow the instructions
in :::ref:`_custom_chisel` to add your project to the build system, then create a config by following the steps in :::ref:`_hetero_socs_`.
You can now run any desired workflow for the new config just as you do for the built-in cores. 

Appendix: Common Config Keys
----------------------------

Chipyard provide a set of keys to store standard parameters. Below are some of the most common key used in core integration. 
(Note that internal fields are hidden)

.. code-block:: scala

    // keys
    // Parameters exposed to the top-level design, set based on external requirements, etc. See RISC-V debug specs for more info.
    case object DebugModuleKey extends Field[Option[DebugModuleParams]](Some(DebugModuleParams()))
    case object BootROMParams extends Field[BootROMParams]                  // See chipyard boot process tutorial
    case object CLINTKey extends Field[Option[CLINTParams]](None)           // Core Local Interrupter setting (See SiFive Interrupt Cookbook) 
    case object PLICKey extends Field[Option[PLICParams]](None)             // Platform Level Interrupt Controller setting (See SiFive Interrupt Cookbook) 
    case object CacheBlockBytes extends Field[Int](64)                      // # of bytes in a cache block
    case object BroadcastKey extends Field(BroadcastParams())               // L2 Cache broadcast setting
    case object BankedL2Key extends Field(BankedL2Params())                 // L2 Cache memory setting
    case object PgLevels extends Field[Int](2)                              // Page Level of virtual memory
    case object ASIdBits extends Field[Int](0)                              // Max # of bits for Address Space Identifer (See specs)
    case object ExtMem extends Field[Option[MemoryPortParams]](None)        // External DRAM setting
    case object ExtBus extends Field[Option[MasterPortParams]](None)        // External (off-chip) output bus setting
    case object ExtIn extends Field[Option[SlavePortParams]](None)          // External (off-chip) input bus setting
    case object MaxHartIdBits extends Field[Int]                            // Max # of bits used to represent a Hart ID
    case object XLen extends Field[Int]                                     // Instruction bits (32 or 64)
    case object BuildRoCC extends Field[Seq[Parameters => LazyRoCC]](Nil)   // See custom ROCC tutorial

    // Values
    case class DebugModuleParams (
      nDMIAddrSize  : Int = 7,                  // Size of the Debug Bus Address
      nProgramBufferWords: Int = 16,            // Number of 32-bit words for Program Buffer
      nAbstractDataWords : Int = 4,             // Number of 32-bit words for Abstract Commands
      nScratch : Int = 1,                       // Number of scratch memories used
      hasBusMaster : Boolean = false,           // Whether or not a bus master should be included
      clockGate : Boolean = true,               // Use clock gating
      maxSupportedSBAccess : Int = 32,          // Maximum transaction size supported by System Bus Access logic.
      supportQuickAccess : Boolean = false,     // Whether or not to support the quick access command.
      supportHartArray   : Boolean = true,      // Whether or not to implement the hart array register (if >1 hart).
      nHaltGroups        : Int = 1,             // Number of halt groups (group of harts that are halted together)
      nExtTriggers       : Int = 0,             // Number of extra triggers
      hasHartResets      : Boolean = false,     // Whether harts can be reseted with debugging system
      hasImplicitEbreak  : Boolean = false,     // There is an additional RO program buffer word containing an ebreak
      hasAuthentication  : Boolean = false,     // Has authentication (to prevent unauthorized users to use debugging system)
      crossingHasSafeReset : Boolean = true     // Include "safe" logic in Async Crossings so that only one side needs to be reset.
    )
    case class CLINTParams(
      baseAddress: BigInt = 0x02000000,     // Default interrupt handler base address for CLINT
      intStages: Int = 0                    // # of cycles (stages) interrupts are delayed 
    )
    case class PLICParams(
      baseAddress: BigInt = 0xC000000,          // Default interrupt handler base address for PLIC
      maxPriorities: Int = 7,                   // Maximum allowed interrupt priority (cannot be over 7)
      intStages: Int = 0,                       // # of cycles (stages) interrupts are delayed
      maxHarts: Int = PLICConsts.maxMaxHarts    // Maximum number or hart / core connected to it
    )
    case class BroadcastParams(
      nTrackers:  Int     = 4,        // # of broadcast tracker 
      bufferless: Boolean = false     // Bufferless broadcast
    )
    case class BankedL2Params(
      nBanks: Int = 1      // Number of banks in L2 cache
    )
    case class MasterPortParams(
      base: BigInt,                   // Base memory address for this port
      size: BigInt,                   // Size of this external memory
      beatBytes: Int,                 // Interface width in bytes
      idBits: Int,                    // # of bits in the port ID
      maxXferBytes: Int = 256,        // Maximum bytes in one transfer transaction
      executable: Boolean = true      // If the data from this port can be executed as instruciton 
    )
    /** Specifies the width of external slave ports */
    case class SlavePortParams(
      beatBytes: Int,     // Interface width in bytes
      idBits: Int,        // # of bits in the port ID
      sourceBits: Int     // # of bits in the source address
    )
    case class MemoryPortParams(
      master: MasterPortParams, // The memory port setting
      nMemoryChannels: Int      // Number of memory channel
    )
