.. _memory-hierarchy:

Memory Hierarchy
===============================

The L1 Caches
--------------

Each CPU tile has an L1 instruction cache and L1 data cache. The size and
associativity of these caches can be configured. The default ``RocketConfig``
uses 16 KiB, 4-way set-associative instruction and data caches. However,
if you use the ``WithNMedCores`` or ``WithNSmallCores`` configurations, you can
configure 4 KiB direct-mapped caches for L1I and L1D.

If you only want to change the size or associativity, there are config
fragments for those too. See :ref:`Customization/Keys-Traits-Configs:Config Fragments` for how to add these to a custom ``Config``.

.. code-block:: scala

         new freechips.rocketchip.subsystem.WithL1ICacheSets(128) ++  // change rocket I$
         new freechips.rocketchip.subsystem.WithL1ICacheWays(2) ++    // change rocket I$
         new freechips.rocketchip.subsystem.WithL1DCacheSets(128) ++  // change rocket D$
         new freechips.rocketchip.subsystem.WithL1DCacheWays(2) ++    // change rocket D$


You can also configure the L1 data cache as an data scratchpad instead.
However, there are some limitations on this. If you are using a data scratchpad,
you can only use a single core and you cannot give the design an external DRAM.
Note that these configurations fully remove the L2 cache and mbus.


.. literalinclude:: ../../generators/chipyard/src/main/scala/config/RocketConfigs.scala
    :language: scala
    :start-after: DOC include start: l1scratchpadrocket
    :end-before: DOC include end: l1scratchpadrocket


This configuration fully removes the L2 cache and memory bus by setting the
number of channels and number of banks to 0.

The System Bus
--------------

The system bus is the TileLink network that sits between the tiles and the L2
agents and MMIO peripherals. Ordinarily, it is a fully-connected crossbar,
but TestChipIP provides a version that uses a ring network instead. This can
be useful when taping out larger systems. To use  the ring network system
bus, simply add the ``WithRingSystemBus`` config fragment to your configuration.

.. literalinclude:: ../../generators/chipyard/src/main/scala/config/RocketConfigs.scala
    :language: scala
    :start-after: DOC include start: RingSystemBusRocket
    :end-before: DOC include end: RingSystemBusRocket

The SiFive L2 Cache
-------------------

The default ``RocketConfig`` provided in the Chipyard example project uses SiFive's
InclusiveCache generator to produce a shared L2 cache. In the default
configuration, the L2 uses a single cache bank with 512 KiB capacity and 8-way
set-associativity. However, you can change these parameters to obtain your
desired cache configuration. The main restriction is that the number of ways
and the number of banks must be powers of 2.

Refer to the ``CacheParameters`` object defined in sifive-cache for
customization options.

The Broadcast Hub
-----------------

If you do not want to use the L2 cache (say, for a resource-limited embedded
design), you can create a configuration without it. Instead of using the L2
cache, you will instead use RocketChip's TileLink broadcast hub.
To make such a configuration, you can just copy the definition of
``RocketConfig`` but omit the ``WithInclusiveCache`` config fragment from the
list of included mixims.

If you want to reduce the resources used even further, you can configure
the Broadcast Hub to use a bufferless design. This config fragment is
``freechips.rocketchip.subsystem.WithBufferlessBroadcastHub``.


The Outer Memory System
-----------------------

The L2 coherence agent (either L2 cache or Broadcast Hub) makes requests to
an outer memory system consisting of an AXI4-compatible DRAM controller.

The default configuration uses a single memory channel, but you can configure
the system to use multiple channels. As with the number of L2 banks, the
number of DRAM channels is restricted to powers of two.

.. code-block:: scala

    new freechips.rocketchip.subsystem.WithNMemoryChannels(2)

In VCS and Verilator simulation, the DRAM is simulated using the
``SimAXIMem`` module, which simply attaches a single-cycle SRAM to each
memory channel.

Instead of connecting to off-chip DRAM, you can instead connect a scratchpad
and remove the off-chip link. This is done by adding a fragment like
``testchipip.WithBackingScratchpad`` to your configuration and removing the
memory port with ``freechips.rocketchip.subsystem.WithNoMemPort``.

.. literalinclude:: ../../generators/chipyard/src/main/scala/config/RocketConfigs.scala
    :language: scala
    :start-after: DOC include start: mbusscratchpadrocket
    :end-before: DOC include end: mbusscratchpadrocket

If you want a more realistic memory simulation, you can use FireSim, which
can simulate the timing of DDR3 controllers. More documentation on FireSim
memory models is available in the `FireSim docs <https://docs.fires.im/en/latest/>`_.
