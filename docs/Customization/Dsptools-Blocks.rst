.. _dsptools-blocks:

Dsptools Blocks
===============
 
Another way to create a MMIO peripheral is to use the Dsptools library for Chisel. In this method, a chain is created by placing a custom module inside a ``DspBlock`` and sandwiching that block between a ``ReadQueue`` and ``WriteQueue``. Those queues then act as memory mapped interfaces to the Rocket Chip SoCs. This section will again primarily focuso n designing Tilelink-based peripherals. However, through the resources provided in Dsptools, one could also define an AXI4-based peripheral by following similar steps.

For this example, we will show you how to connect a simple FIR filter created using Dsptools as an MMIO peripheral. The full code can be found in ``generators/example/src/main/scala/dsptools/GenericFIR.scala``.

We module ``GenericFIR`` links together a variable number of ``GenericFIRDirectCell`` submodules which work togther to perform the filtering. It is important to note that both modules are type generic, which means that they can be instantiated for any datatype that implements ``Ring`` operations per the specifications on ``T``.

.. literalinclude:: ../../generators/example/src/main/scala/dsptools/GenericFIR.scala
    :language: scala
    :start-after: DOC include start: GenericFIR chisel
    :end-before: DOC include end: GenericFIR chisel

.. literalinclude:: ../../generators/example/src/main/scala/dsptools/GenericFIR.scala
    :language: scala
    :start-after: DOC include start: GenericFIRDirectCell chisel
    :end-before: DOC include end: GenericFIRDirectCell chisel

Creating a DspBlock Extension
-----------------------------

The first step in attaching the FIR filter as a MMIO peripheral is to create an abstract extension of ``DspBlock`` the wraps around the ``GenericFIR`` module. The main steps of this process are as follows.

1. Instantiate a ``GenericFIR`` within ``GenericFIRBlock``.
2. Attach the ready and valid signals from the in and out connections.
3. Cast the module input data to the input type of ``GenericFIR`` (``GenericFIRBundle``) and attach.
4. Cast the output of ``GenericFIR`` to ``UInt`` and attach to the module output.

.. literalinclude:: ../../generators/example/src/main/scala/dsptools/GenericFIR.scala
    :language: scala
    :start-after: DOC include start: GenericFIRBlock chisel
    :end-before: DOC include end: GenericFIRBlock chisel

Connecting by TileLink
----------------------
With these classes implemented, you can begin to construct the chain by extending ``GenericFIRBlock`` while using the ``TLDspBlock`` trait via mixin. 

.. literalinclude:: ../../generators/example/src/main/scala/dsptools/GenericFIR.scala
    :language: scala
    :start-after: DOC include start: TLGenericFIRBlock chisel
    :end-before: DOC include end: TLGenericFIRBlock chisel

We can then construct the final chain by utilizing the ``TLWriteQueue`` and ``TLReadeQueue`` modules found in ``generators/example/src/main/scala/dsptools/DspBlocks.scala``. Inside our chain, we construct an instance of each queue as well as our ``TLGenericFIRBlock``. We then take the ``steamnode`` from each module and wire them all together to link the chain.

.. literalinclude:: ../../generators/example/src/main/scala/dsptools/GenericFIR.scala
    :language: scala
    :start-after: DOC include start: TLGenericFIRChain chisel
    :end-before: DOC include end: TLGenericFIRChain chisel

Top Level Traits
----------------
As in the previous MMIO example, we use a cake pattern to hook up our module to our SoC.

.. literalinclude:: ../../generators/example/src/main/scala/dsptools/GenericFIR.scala
    :language: scala
    :start-after: DOC include start: CanHavePeripheryUIntTestFIR chisel
    :end-before: DOC include end: CanHavePeripheryUIntTestFIR chisel

Note that this is the point at which we decide the datatype for our FIR. It is also possible with some reworking to push the datatype selection out to the top level.

Our module does not need to be connected to concrete IOs or wires, so we do not need to create a concrete trait.

Constructing the Top and Config
-------------------------------

Once again following the path of the previous MMIO example, we now want to mix our traits into the system as a whole. The code is from ``generators/example/src/main/scala/Top.scala``

.. literalinclude:: ../../generators/example/src/main/scala/Top.scala
    :language: scala
    :start-after: DOC include start: Top
    :end-before: DOC include end: Top

Finally, we create the configuration class in ``generators/example/src/main/scala/RocketConfigs.scala`` that uses the ``WithUIntTestFIR`` mixin defined in ``generators/example/src/main/scala/ConfigMixins.scala``.

.. literalinclude:: ../../generators/example/src/main/scala/ConfigMixins.scala
    :language: scala
    :start-after: DOC include start: WithTestFIR
    :end-before: DOC include end: WithTestFIR

.. literalinclude:: ../../generators/example/src/main/scala/RocketConfigs.scala
    :language: scala
    :start-after: DOC include start: FIRRocketConfig
    :end-before: DOC include end: FIRRocketConfig

Testing
-------

We can now test that the FIR is working. The test program is found in ``tests/gcd.c``.

.. literalinclude:: ../../tests/fir.c
    :language: c

The test feed a series of values into the fir and compares the output to a golden model of computation. The base of the module's MMIO write region is at 0x2000 and the base of the read region is at 0x2100 by default.

Compiling this program with ``make`` produces a ``fir.riscv`` executable.

Now we can run our simulation.

.. code-block:: shell

    cd sims/verilator
    make CONFIG=GCDTLRocketConfig BINARY=../../tests/fir.riscv run-binary
