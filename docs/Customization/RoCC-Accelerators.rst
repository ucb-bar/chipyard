.. _rocc-accelerators:

Adding a RoCC Accelerator
-------------------------

A RoCC accelerator is a component that can be added into a particular Rocket or BooM tile.
It receives instructions that match a certain opcode, talks to other parts of the core or SoC (L1, L2, PTW, FPU), and then optionally writes back a value into the register corresponding with the ``rd`` field of the instruction.
RoCC accelerators are instantiated via modules that extend the ``LazyRoCC`` class.
These modules lazily instantiate another module which extends the ``LazyRoCCModule`` class.
This extra layer of indirection is used so that Diplomacy can figure out how to connect the RoCC module to the chip, without needing to instantiate the module ahead of time.
Lazy modules are further explained in the :ref:`Chipyard-Basics/Configs-Parameters-Mixins:Cake Pattern / Mixin` section.
Below is a minimal instantiation of a RoCC accelerator.

.. code-block:: scala

    class CustomAccelerator(opcodes: OpcodeSet)
        (implicit p: Parameters) extends LazyRoCC(opcodes) {
      override lazy val module = new CustomAcceleratorModule(this)
    }

    class CustomAcceleratorModule(outer: CustomAccelerator)
        extends LazyRoCCModuleImp(outer) {
      val cmd = Queue(io.cmd)
      // The parts of the command are as follows
      // inst - the parts of the instruction itself
      //   opcode
      //   rd - destination register number
      //   rs1 - first source register number
      //   rs2 - second source register number
      //   funct
      //   xd - is the destination register being used?
      //   xs1 - is the first source register being used?
      //   xs2 - is the second source register being used?
      // rs1 - the value of source register 1
      // rs2 - the value of source register 2
      ...
    }

The ``opcodes`` parameter for ``LazyRoCC`` is the set of custom opcodes that will map to this accelerator.
More on this in the next subsection.

The ``LazyRoCC`` class contains two TLOutputNode instances, ``atlNode`` and ``tlNode``.
The former connects into a tile-local arbiter along with the backside of the L1 instruction cache.
The latter connects directly to the L1-L2 crossbar.
The corresponding Tilelink ports in the module implementation's IO bundle are ``atl`` and ``tl``, respectively.

The other interfaces available to the accelerator are ``mem``, which provides access to the L1 cache;
``ptw`` which provides access to the page-table walker;
the ``busy`` signal, which indicates when the accelerator is still handling an instruction;
and the ``interrupt`` signal, which can be used to interrupt the CPU.

Look at the examples in ``generators/rocket-chip/src/main/scala/tile/LazyRoCC.scala`` for detailed information on the different IOs.
There is also more information about each of the signals in `the RoCC Documentation written by UCSD <https://docs.google.com/document/d/1CH2ep4YcL_ojsa3BVHEW-uwcKh1FlFTjH_kg5v8bxVw/edit>`_, although it is updated out of tree and may be out of date.


Accessing Memory via L1 Cache
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

A RoCC accelerator can access memory through the L1 Cache of the core it is attached to.
This is a simpler interface for accelerator architects to implement, but will generally have lower achievable throughput than a dedicated TileLink port.

In your ``LazyRoCCModuleImp``, the signal ``io.mem`` is a ``HellaCacheIO``, which is defined in ``generators/rocket-chip/src/main/scala/rocket/HellaCache.scala``.

.. code-block:: scala

    class HellaCacheIO(implicit p: Parameters) extends CoreBundle()(p) {
        val req = Decoupled(new HellaCacheReq)
        val s1_kill = Output(Bool()) // kill previous cycle's req
        val s1_data = Output(new HellaCacheWriteData()) // data for previous cycle's req
        val s2_nack = Input(Bool()) // req from two cycles ago is rejected
        val s2_nack_cause_raw = Input(Bool()) // reason for nack is store-load RAW hazard (performance hint)
        val s2_kill = Output(Bool()) // kill req from two cycles ago
        val s2_uncached = Input(Bool()) // advisory signal that the access is MMIO
        val s2_paddr = Input(UInt(paddrBits.W)) // translated address

        val resp = Flipped(Valid(new HellaCacheResp))
        val replay_next = Input(Bool())
        val s2_xcpt = Input(new HellaCacheExceptions)
        val s2_gpa = Input(UInt(vaddrBitsExtended.W))
        val s2_gpa_is_pte = Input(Bool())
        val uncached_resp = tileParams.dcache.get.separateUncachedResp.option(Flipped(Decoupled(new HellaCacheResp)))
        val ordered = Input(Bool())
        val perf = Input(new HellaCachePerfEvents())

        val keep_clock_enabled = Output(Bool()) // should D$ avoid clock-gating itself?
        val clock_enabled = Input(Bool()) // is D$ currently being clocked?
    }

At a high level, you must tag requests that you send across this interface using the ``io.mem.req.tag``, and the tag will be returned to you when the data is ready.
Responses may come back out of order if you issue multiple requests, so you can use these tags to tell what data came back.
Note that the number of tag bits is controled by ``dcacheReqTagBits``, which is usually set to 6.
Using more than 6 bits will cause errors or hangs.


Adding RoCC accelerator to Config
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

RoCC accelerators can be added to a core by overriding the ``BuildRoCC`` parameter in the configuration.
This takes a sequence of functions producing ``LazyRoCC`` objects, one for each accelerator you wish to add.

For instance, if we wanted to add the previously defined accelerator and route custom0 and custom1 instructions to it, we could do the following.

.. code-block:: scala

    class WithCustomAccelerator extends Config((site, here, up) => {
      case BuildRoCC => Seq((p: Parameters) => LazyModule(
        new CustomAccelerator(OpcodeSet.custom0 | OpcodeSet.custom1)(p)))
    })

    class CustomAcceleratorConfig extends Config(
      new WithCustomAccelerator ++
      new RocketConfig)

To add RoCC instructions in your program, use the RoCC C macros provided in ``tests/rocc.h``. You can find examples in the files ``tests/accum.c`` and ``charcount.c``.
