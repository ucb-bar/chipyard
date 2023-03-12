.. _rocc-accelerators:

Adding a RoCC Accelerator
----------------------------

RoCC accelerators are lazy modules that extend the ``LazyRoCC`` class.
Their implementation should extends the ``LazyRoCCModule`` class.

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
