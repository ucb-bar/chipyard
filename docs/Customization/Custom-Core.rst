.. _custom_core:

Adding a custom core
====================

You may want to integrate a custom RISC-V core into the Chipyard framework. This documentation page provides step-by-step
instructions on how to achieve this.

.. note::

    RoCC is currently not supported by cores other than Rocket and BOOM. Please use Rocket or BOOM as the RoCC base core if you need to use RoCC.

.. note::

    This page contains links to the files that contains important definitions in the Rocket chip repository, which is maintained separately
    from Chipyard. If you find any discrepancy between the code on this page and the code in the source file, please report it through
    GitHub issues!

Wrap Verilog Module with Blackbox (Optional)
--------------------------------------------

Since Chipyard uses Scala and Chisel, if the top module of your core is not in Chisel, you will first need to create a Verilog
blackbox for it so that it can be processed by Chipyard. See :ref:`incorporating-verilog-blocks` for instructions.

Create Parameter Case Classes
-----------------------------

Chipyard will generate a core for every ``InstantiableTileParams`` object it discovered in the ``TilesLocated(InSubsystem)`` key.
This object is derived from``TileParams``, a trait containing the information needed to create a tile. All cores must have
their own implementation of ``InstantiableTileParams``, as well as ``CoreParams`` which is passed as a field in ``TileParams``.

``TileParams`` holds the parameters for the tile, which include parameters for all components in the tile (e.g.
core, cache, MMU, etc.), while ``CoreParams`` contains parameters specific to the core on the tile.
They must be implemented as case classes with fields that can be overridden by
other config fragments as the constructor parameters. See the appendix at the bottom of the page for a list of
variable to be implemented. You can also add custom fields to them, but standard fields should always be preferred.

``InstantiableTileParams[TileType]`` holds the constructor of ``TileType`` on top of the fields of ``TileParams``,
where ``TileType`` is the tile class (see the next section).
All custom cores will also need to implement ``instantiate()`` in their tile parameter class to return a new instance
of the tile class ``TileType``.

``TileParams`` (in the file `BaseTile.scala <https://github.com/chipsalliance/rocket-chip/blob/master/src/main/scala/tile/BaseTile.scala>`_) ,
``InstantiableTileParams`` (in the file `BaseTile.scala <https://github.com/chipsalliance/rocket-chip/blob/master/src/main/scala/tile/BaseTile.scala>`_),
``CoreParams`` (in the file `Core.scala <https://github.com/chipsalliance/rocket-chip/blob/master/src/main/scala/tile/Core.scala>`_),
and ``FPUParams`` (in the file `FPU.scala <https://github.com/chipsalliance/rocket-chip/blob/master/src/main/scala/tile/FPU.scala>`_)
contains the following fields:

.. code-block:: scala

    trait TileParams {
      val core: CoreParams                  // Core parameters (see below)
      val icache: Option[ICacheParams]      // Rocket specific: I1 cache option
      val dcache: Option[DCacheParams]      // Rocket specific: D1 cache option
      val btb: Option[BTBParams]            // Rocket specific: BTB / branch predictor option
      val hartId: Int                       // Hart ID: Must be unique within a design config (This MUST be a case class parameter)
      val beuAddr: Option[BigInt]           // Rocket specific: Bus Error Unit for Rocket Core
      val blockerCtrlAddr: Option[BigInt]   // Rocket specific: Bus Blocker for Rocket Core
      val name: Option[String]              // Name of the core
    }

    abstract class InstantiableTileParams[TileType <: BaseTile] extends TileParams {
      def instantiate(crossing: TileCrossingParamsLike, lookup: LookupByHartIdImpl)
                    (implicit p: Parameters): TileType
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
      // Rocket specific: Longest possible latency of Rocket core D1 cache. Simply set it to the default value 80 if you don't use it.
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

Most of the fields here (marked "Rocket spcific") are originally designed for the Rocket core and thus contain some
implementation-specific details, but many of them are general enough to be useful for other cores. You may ignore
any fields marked "Rocket specific" and use their default values; however, if you need to store additional information
with meaning or usage similar to these "Rocket specific" fields, it is recommended to use these fields instead of
creating your own custom fields.

You will also need a ``CanAttachTile`` class to add the tile config into the config system, with the following format:

.. literalinclude:: ../../generators/chipyard/src/main/scala/example/TutorialTile.scala
    :language: scala
    :start-after: DOC include start: CanAttachTile
    :end-before: DOC include end: CanAttachTile

During elaboration, Chipyard will look for subclasses of ``CanAttachTile`` in the config system and instantiate a tile
from the parameters in this class for every such class it found.

.. note::

    Implementations may choose to ignore some fields here or use them in a non-standard way, but using an inaccurate
    value may break Chipyard components that rely on them (e.g. an inaccurate indication of supported ISA extension will
    result in an incorrect test suite being generated) as well as any custom modules that use them. ALWAYS document any
    fields you ignore or with altered usage in your core implementation, and if you are implementing other devices that
    would look up these config values, also document them. "Rocket specific" values are generally safe to ignore, but
    you should document them if you use them.

Create Tile Class
-----------------

In Chipyard, all Tiles are diplomatically instantiated. In the first phase, diplomatic nodes which specify Tile-to-System
interconnects are evaluated, while in the second "Module Implementation" phase, hardware is elaborated.
See :ref:`tilelink_and_diplomacy` for more details. In this step, you will need to implement a tile class for your core,
which specifies the constraints on the core's parameters and the connections with other diplomatic nodes. This class
usually contains Diplomacy/TileLink code only, and Chisel RTL code should not go here.

All tile classes implement ``BaseTile`` and will normally implement ``SinksExternalInterrupts`` and ``SourcesExternalNotifications``,
which allow the tile to accept external interrupt. A typical tile has the following form:

.. literalinclude:: ../../generators/chipyard/src/main/scala/example/TutorialTile.scala
    :language: scala
    :start-after: DOC include start: Tile class
    :end-before: DOC include end: Tile class

Connect TileLink Buses
----------------------

Chipyard uses TileLink as its onboard bus protocol. If your core doesn't use TileLink, you will need to insert converters
between the core's memory protocol and TileLink within the Tile module.
in the tile class. Below is an example of how to connect a core using AXI4 to the TileLink bus with converters provided by
Rocket chip:

.. literalinclude:: ../../generators/chipyard/src/main/scala/example/TutorialTile.scala
    :language: scala
    :start-after: DOC include start: AXI4 convert
    :end-before: DOC include end: AXI4 convert

Remember, you may not need all of these intermediate widgets. See :ref:`diplomatic_widgets` for the meaning of each intermediate
widget. If you are using TileLink, then you only need the tap node and the TileLink node used by your components. Chipyard also
provides converters for AHB, APB and AXIS, and most of the AXI4 widgets has equivalent widget for these bus protocol; see the
source files in ``generators/rocket-chip/src/main/scala/amba`` for more info.

If you are using some other bus protocol, you may implement your own converters, using the files in ``generators/rocket-chip/src/main/scala/amba``
as the template, but it is not recommended unless you are familiar with TileLink.

``memAXI4Node`` is an AXI4 master node and is defined as following in our example:

.. literalinclude:: ../../generators/chipyard/src/main/scala/example/TutorialTile.scala
    :language: scala
    :start-after: DOC include start: AXI4 node
    :end-before: DOC include end: AXI4 node

where ``portName`` and ``idBits`` (number of bits to represent a port ID) are the parameter provides by the tile.
Make sure to read :ref:`node_types` to check out what type of nodes Chipyard supports and their parameters!

Also, by default, there are boundary buffers for both master and slave connections to the bus when they are leaving the tile, and you
can override the following two functions to control how to buffer the bus requests/responses:
(You can find the definition of these two functions in the class ``BaseTile`` in the file
`BaseTile.scala <https://github.com/chipsalliance/rocket-chip/blob/master/src/main/scala/tile/BaseTile.scala>`_)

.. code-block:: scala

    // By default, their value is "TLBuffer(BufferParams.none)".
    protected def makeMasterBoundaryBuffers(implicit p: Parameters): TLBuffer
    protected def makeSlaveBoundaryBuffers(implicit p: Parameters): TLBuffer

You can find more information on ``TLBuffer`` in :ref:`diplomatic_widgets`.

Create Implementation Class
---------------------------

The implementation class contains the parameterized, actual hardware that depends on the values resolved by the Diplomacy
framework according to the info provided in the Tile class. This class will normally contains Chisel RTL code. If your
core is in Verilog, you will need to instantiate the black box class that wraps your Verilog implementation and connect it with the buses
and other components. No Diplomacy/TileLink code should be in this class; you should only connect the IO signals in TileLink
interfaces or other diplomatically defined components, which are located in the tile class.

The implementation class for your core is of the following form:

.. literalinclude:: ../../generators/chipyard/src/main/scala/example/TutorialTile.scala
    :language: scala
    :start-after: DOC include start: Implementation class
    :end-before: DOC include end: Implementation class

If you create an AXI4 node (or equivalents), you will need to connect them to your core. You can connect a port like this:

.. literalinclude:: ../../generators/chipyard/src/main/scala/example/TutorialTile.scala
    :language: scala
    :start-after: DOC include start: AXI4 connect
    :end-before: DOC include end: AXI4 connect

Connect Interrupt
-----------------

Chipyard allows a tile to either receive interrupts from other devices or initiate interrupts to notify other cores/devices.
In the tile that inherited ``SinksExternalInterrupts``, one can create a ``TileInterrupts`` object (a Chisel bundle) and
call ``decodeCoreInterrupts()`` with the object as the argument. Note that you should call this function in the implementation
class since it returns a Chisel bundle used by RTL code. You can then read the interrupt bits from the ``TileInterrupts`` bundle
we create above. The definition of ``TileInterrupts``
(in the file `Interrupts.scala <https://github.com/chipsalliance/rocket-chip/blob/master/src/main/scala/tile/Interrupts.scala>`_) is

.. code-block:: scala

    class TileInterrupts(implicit p: Parameters) extends CoreBundle()(p) {
      val debug = Bool() // debug interrupt
      val mtip = Bool() // Machine level timer interrupt
      val msip = Bool() // Machine level software interrupt
      val meip = Bool() // Machine level external interrupt
      val seip = usingSupervisor.option(Bool()) // Valid only if supervisor mode is supported
      val lip = Vec(coreParams.nLocalInterrupts, Bool())  // Local interrupts
    }

Here is an example on how to connect these signals in the implementation class:

.. literalinclude:: ../../generators/chipyard/src/main/scala/example/TutorialTile.scala
    :language: scala
    :start-after: DOC include start: connect interrupt
    :end-before: DOC include end: connect interrupt

Also, the tile can also notify other cores or devices for some events by calling following functions in ``SourcesExternalNotifications``
from the implementation class:
(These functions can be found in in the trait ``SourcesExternalNotifications`` in the file
`Interrupts.scala <https://github.com/chipsalliance/rocket-chip/blob/master/src/main/scala/tile/Interrupts.scala>`_)

.. code-block:: scala

    def reportHalt(could_halt: Option[Bool]) // Triggered when there is an unrecoverable hardware error (halt the machine)
    def reportHalt(errors: Seq[CanHaveErrors]) // Varient for standard error bundle (Rocket specific: used only by cache when there's an ECC error)
    def reportCease(could_cease: Option[Bool], quiescenceCycles: Int = 8) // Triggered when the core stop retiring instructions (like clock gating)
    def reportWFI(could_wfi: Option[Bool]) // Triggered when a WFI instruction is executed

Here is an example on how to use these functions to raise interrupt.

.. literalinclude:: ../../generators/chipyard/src/main/scala/example/TutorialTile.scala
    :language: scala
    :start-after: DOC include start: raise interrupt
    :end-before: DOC include end: raise interrupt

Create Config Fragments to Integrate the Core
---------------------------------------------

To use your core in a Chipyard config, you will need a config fragment that will create a ``TileParams`` object of your core in
the current config. An example of such config will be like this:

.. literalinclude:: ../../generators/chipyard/src/main/scala/example/TutorialTile.scala
    :language: scala
    :start-after: DOC include start: Config fragment
    :end-before: DOC include end: Config fragment

Chipyard looks up the tile parameters in the field ``TilesLocated(InSubsystem)``, whose type is a list of ``InstantiableTileParams``.
This config fragment simply appends new tile parameters to the end of this list.

Now you have finished all the steps to prepare your cores for Chipyard! To generate the custom core, simply follow the instructions
in :ref:`custom_chisel` to add your project to the build system, then create a config by following the steps in :ref:`hetero_socs_`.
You can now run most desired workflows for the new config just as you would for the built-in cores (depending on the functionality your core supports).

If you would like to see an example of a complete third-party Verilog core integrated into Chipyard, ``generators/ariane/src/main/scala/CVA6Tile.scala``
provides a concrete example of the CVA6 core. Note that this particular example includes additional nuances with respect to the interaction of the AXI
interface with the memory coherency system.
