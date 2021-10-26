.. _dsptools-blocks:

Dsptools is a Chisel library that aids in writing custom signal processing accelerators. It does this by:
* Giving types and helpers that allow you to express mathematical operations more directly.
* Typeclasses that let you write polymorphic generators, for example an FIR filter generator that works for both real- and complex-valued filters.
* Structures for packaging DSP blocks and integrating them into a rocketchip-based SoC.
* Test harnesses for testing DSP circuits, as well as VIP-style drivers and monitors for DSP blocks.

The `Dsptools repository <https://github.com/ucb-bar/dsptools/>`_ has more documentation.


Dsptools Blocks
===============
A ``DspBlock`` is the basic unit of signal processing functionality that can be integrated into an SoC.
It has a AXI4-stream interface and an optional memory interface.
The idea is that these ``DspBlocks`` can be easily designed, unit tested, and assembled lego-style to build complex functionality.
A ``DspChain`` is one example of how to assemble ``DspBlocks``, in which case the streaming interfaces are connected serially into a pipeline, and a bus is instatiated and connected to every block with a memory interface.

Chipyard has example designs that integrate a ``DspBlock`` to a rocketchip-based SoC as an MMIO peripheral. The custom ``DspBlock`` has a ``ReadQueue`` before it and a ``WriteQueue`` after it, which allow memory mapped access to the streaming interfaces so the rocket core can interact with the ``DspBlock`` [#]_.  This section will primarily focus on designing Tilelink-based peripherals. However, through the resources provided in Dsptools, one could also define an AXI4-based peripheral by following similar steps. Furthermore, the examples here are simple, but can be extended to implement more complex accelerators, for example an `OFDM baseband <https://github.com/grebe/ofdm>`_ or a `spectrometer <https://github.com/ucb-art/craft2-chip>`_.

.. figure:: ../_static/images/fir-block-diagram.svg
    :align: center
    :alt: Block diagram showing how FIR is integrated with rocket.
    :width: 400px

For this example, we will show you how to connect a simple FIR filter created using Dsptools as an MMIO peripheral as shown in the figure above. The full code can be found in ``generators/chipyard/src/main/scala/example/dsptools/GenericFIR.scala``. That being said, one could substitute any module with a ready valid interface in the place of the FIR and achieve the same results. As long as the read and valid signals of the module are attached to those of a corresponding ``DSPBlock`` wrapper, and that wrapper is placed in a chain with a ``ReadQueue`` and a ``WriteQueue``, following the general outline establised by these steps will allow you to interact with that block as a memory mapped IO.

The module ``GenericFIR`` is the overall wrapper of our FIR module. This module links together a variable number of ``GenericFIRDirectCell`` submodules, each of which performs the computations for one coefficient in a FIR direct form architecture. It is important to note that both modules are type-generic, which means that they can be instantiated for any datatype ``T`` that implements ``Ring`` operations (e.g. addition, multiplication, identities).

.. literalinclude:: ../../generators/chipyard/src/main/scala/example/dsptools/GenericFIR.scala
    :language: scala
    :start-after: DOC include start: GenericFIR chisel
    :end-before: DOC include end: GenericFIR chisel

.. literalinclude:: ../../generators/chipyard/src/main/scala/example/dsptools/GenericFIR.scala
    :language: scala
    :start-after: DOC include start: GenericFIRDirectCell chisel
    :end-before: DOC include end: GenericFIRDirectCell chisel

Creating a DspBlock
-------------------

The first step in attaching the FIR filter as a MMIO peripheral is to create an abstract subclass of ``DspBlock`` the wraps around the ``GenericFIR`` module. Streaming outputs and inputs are packed and unpacked into ``UInt`` s. If there were control signals, this is where they'd go from raw IOs to memory mapped. The main steps of this process are as follows.

1. Instantiate a ``GenericFIR`` within ``GenericFIRBlock``.
2. Attach the ready and valid signals from the in and out connections.
3. Cast the module input data to the input type of ``GenericFIR`` (``GenericFIRBundle``) and attach.
4. Cast the output of ``GenericFIR`` to ``UInt`` and attach to the module output.

.. literalinclude:: ../../generators/chipyard/src/main/scala/example/dsptools/GenericFIR.scala
    :language: scala
    :start-after: DOC include start: GenericFIRBlock chisel
    :end-before: DOC include end: GenericFIRBlock chisel

Note that at this point the ``GenericFIRBlock`` does not have a type of memory interface specified. This abstract class can be used to create different flavors that use AXI-4, TileLink, AHB, or whatever other memory interface you like like.

Connecting DspBlock by TileLink
-------------------------------
With these classes implemented, you can begin to construct the chain by extending ``GenericFIRBlock`` while using the ``TLDspBlock`` trait via mixin.

.. literalinclude:: ../../generators/chipyard/src/main/scala/example/dsptools/GenericFIR.scala
    :language: scala
    :start-after: DOC include start: TLGenericFIRBlock chisel
    :end-before: DOC include end: TLGenericFIRBlock chisel

We can then construct the final chain by utilizing the ``TLWriteQueue`` and ``TLReadeQueue`` modules found in ``generators/chipyard/src/main/scala/example/dsptools/DspBlocks.scala``. The chain is created by passing a list of factory functions to the constructor of ``TLChain``. The constructor then automatically instantiates these ``DspBlocks``, connects their stream nodes in order, creates a bus, and connects any ``DspBlocks`` that have memory interfaces to the bus.

.. literalinclude:: ../../generators/chipyard/src/main/scala/example/dsptools/GenericFIR.scala
    :language: scala
    :start-after: DOC include start: TLGenericFIRChain chisel
    :end-before: DOC include end: TLGenericFIRChain chisel

Top Level Traits
----------------
As in the previous MMIO example, we use a cake pattern to hook up our module to our SoC.

.. literalinclude:: ../../generators/chipyard/src/main/scala/example/dsptools/GenericFIR.scala
    :language: scala
    :start-after: DOC include start: CanHavePeripheryStreamingFIR chisel
    :end-before: DOC include end: CanHavePeripheryStreamingFIR chisel

Note that this is the point at which we decide the datatype for our FIR. You could create different configs that use different types for the FIR, for example a config that instantiates a complex-valued FIR filter.

Constructing the Top and Config
-------------------------------

Once again following the path of the previous MMIO example, we now want to mix our traits into the system as a whole. The code is from ``generators/chipyard/src/main/scala/DigitalTop.scala``

.. literalinclude:: ../../generators/chipyard/src/main/scala/DigitalTop.scala
    :language: scala
    :start-after: DOC include start: DigitalTop
    :end-before: DOC include end: DigitalTop

Finally, we create the configuration class in ``generators/chipyard/src/main/scala/config/RocketConfigs.scala`` that uses the ``WithFIR`` mixin defined in ``generators/chipyard/src/main/scala/example/dsptools/GenericFIR.scala``.

.. literalinclude:: ../../generators/chipyard/src/main/scala/example/dsptools/GenericFIR.scala
    :language: scala
    :start-after: DOC include start: WithStreamingFIR
    :end-before: DOC include end: WithStreamingFIR

.. literalinclude:: ../../generators/chipyard/src/main/scala/config/RocketConfigs.scala
    :language: scala
    :start-after: DOC include start: StreamingFIRRocketConfig
    :end-before: DOC include end: StreamingFIRRocketConfig

FIR Testing
-----------

We can now test that the FIR is working. The test program is found in ``tests/streaming-fir.c``.

.. literalinclude:: ../../tests/streaming-fir.c
    :language: c

The test feed a series of values into the fir and compares the output to a golden model of computation. The base of the module's MMIO write region is at 0x2000 and the base of the read region is at 0x2100 by default.

Compiling this program with ``make`` produces a ``streaming-fir.riscv`` executable.

Now we can run our simulation.

.. code-block:: shell

    cd sims/verilator
    make CONFIG=StreamingFIRRocketConfig BINARY=../../tests/streaming-fir.riscv run-binary

.. [#] ``ReadQueue`` and ``WriteQueue`` are good illustrations of how to write a ``DspBlock`` and how they can be integrated into rocket, but in a real design a DMA engine would be preferred. ``ReadQueue`` will stall the processor if you try to read an empty queue, and ``WriteQueue`` will stall if you try to write to a full queue, which a DMA engine can more elegantly avoid. Furthermore, a DMA engine can do the work of moving data, freeing the processor to do other useful work (or sleep).
