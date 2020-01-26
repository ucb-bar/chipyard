.. _memory-hierarchy:

Memory Hierarchy
===============================

The L1 Caches
--------------

Each CPU tile has an L1 instruction cache and L1 data cache. The size and
associativity of these caches can be configured. The default ``RocketConfig``
uses 16 KiB, 4-way set-associative instruction and data caches. However,
if you use the ``NMedCores`` or ``NSmallCores`` configurations, you can
configure 4 KiB direct-mapped caches for L1I and L1D.

.. code-block:: scala

    class SmallRocketConfig extends Config(
        new WithTSI ++
        new WithNoGPIO ++
        new WithBootROM ++
        new WithUART ++
        new freechips.rocketchip.subsystem.WithNoMMIOPort ++
        new freechips.rocketchip.subsystem.WithNoSlavePort ++
        new freechips.rocketchip.subsystem.WithNSmallCores(1) ++ // small rocket cores
        new freechips.rocketchip.system.BaseConfig)

    class MediumRocketConfig extends Config(
        new WithTSI ++
        new WithNoGPIO ++
        new WithBootROM ++
        new WithUART ++
        new freechips.rocketchip.subsystem.WithNoMMIOPort ++
        new freechips.rocketchip.subsystem.WithNoSlavePort ++
        new freechips.rocketchip.subsystem.WithNMedCores(1) ++ // medium rocket cores
        new freechips.rocketchip.system.BaseConfig)

If you only want to change the size or associativity, there are configuration
mixins for those too.

.. code-block:: scala

    import freechips.rocketchip.subsystem.{WithL1ICacheSets, WithL1DCacheSets, WithL1ICacheWays, WithL1DCacheWays}

    class MyL1RocketConfig extends Config(
        new WithTSI ++
        new WithNoGPIO ++
        new WithBootROM ++
        new WithUART ++
        new freechips.rocketchip.subsystem.WithNoMMIOPort ++
        new freechips.rocketchip.subsystem.WithNoSlavePort ++
        new WithL1ICacheSets(128) ++                              // change rocket I$
        new WithL1ICacheWays(2) ++                                // change rocket I$
        new WithL1DCacheSets(128) ++                              // change rocket D$
        new WithL1DCacheWays(2) ++                                // change rocket D$
        new freechips.rocketchip.subsystem.WithNSmallCores(1) ++
        new freechips.rocketchip.system.BaseConfig)

You can also configure the L1 data cache as an data scratchpad instead.
However, there are some limitations on this. If you are using a data scratchpad,
you can only use a single core and you cannot give the design an external DRAM.
Note that these configurations fully remove the L2 cache and mbus.

.. code-block:: scala

    class SmallRocketConfigNoL2 extends Config(
        new WithTSI ++
        new WithNoGPIO ++
        new WithBootROM ++
        new WithUART ++
        new freechips.rocketchip.subsystem.WithNoMMIOPort ++
        new freechips.rocketchip.subsystem.WithNoSlavePort ++
        new freechips.rocketchip.subsystem.WithNSmallCores(1) ++
        new freechips.rocketchip.system.BaseConfig)

    class ScratchpadRocketConfig extends Config(
        new freechips.rocketchip.subsystem.WithNoMemPort ++
        new freechips.rocketchip.subsystem.WithNMemoryChannels(0) ++
        new freechips.rocketchip.subsystem.WithNBanks(0) ++
        new freechips.rocketchip.subsystem.WithScratchpadsOnly ++
        new SmallRocketConfigNoL2)

This configuration fully removes the L2 cache and memory bus by setting the
number of channels and number of banks to 0.

The SiFive L2 Cache
-------------------

The default RocketConfig provided in the Chipyard example project uses SiFive's
InclusiveCache generator to produce a shared L2 cache. In the default
configuration, the L2 uses a single cache bank with 512 KiB capacity and 8-way
set-associativity. However, you can change these parameters to obtain your
desired cache configuration. The main restriction is that the number of ways
and the number of banks must be powers of 2.

.. code-block:: scala

    import freechips.rocketchip.subsystem.WithInclusiveCache

    class MyCacheRocketConfig extends Config(
        new WithTSI ++
        new WithNoGPIO ++
        new WithBootROM ++
        new WithUART ++
        new freechips.rocketchip.subsystem.WithNoMMIOPort ++
        new freechips.rocketchip.subsystem.WithNoSlavePort ++
        new WithInclusiveCache(                                // add 1MB, 4-way, 4-bank cache
            capacityKB = 1024,
            nWays = 4,
            nBanks = 4) ++
        new freechips.rocketchip.subsystem.WithNSmallCores(1) ++
        new freechips.rocketchip.system.BaseConfig)

The Broadcast Hub
-----------------

If you do not want to use the L2 cache (say, for a resource-limited embedded
design), you can create a configuration without it. Instead of using the L2
cache, you will instead use RocketChip's TileLink broadcast hub.
To make such a configuration, you can just copy the definition of
``RocketConfig`` but omit the ``WithInclusiveCache`` mixin from the
list of included mixims.

.. code-block:: scala

    class CachelessRocketConfig extends Config(
        new WithTSI ++
        new WithNoGPIO ++
        new WithBootROM ++
        new WithUART ++
        new freechips.rocketchip.subsystem.WithNoMMIOPort ++
        new freechips.rocketchip.subsystem.WithNoSlavePort ++
        new freechips.rocketchip.subsystem.WithNBigCores(1) ++
        new freechips.rocketchip.system.BaseConfig)

If you want to reduce the resources used even further, you can configure
the Broadcast Hub to use a bufferless design.

.. code-block:: scala

    import freechips.rocketchip.subsystem.WithBufferlessBroadcastHub

    class BufferlessRocketConfig extends Config(
        new WithBufferlessBroadcastHub ++
        new CachelessRocketConfig)

The Outer Memory System
-----------------------

The L2 coherence agent (either L2 cache or Broadcast Hub) makes requests to
an outer memory system consisting of an AXI4-compatible DRAM controller.

The default configuration uses a single memory channel, but you can configure
the system to use multiple channels. As with the number of L2 banks, the
number of DRAM channels is restricted to powers of two.

.. code-block:: scala

    import freechips.rocketchip.subsystem.WithNMemoryChannels

    class DualChannelRocketConfig extends Config(
        new WithTSI ++
        new WithNoGPIO ++
        new WithBootROM ++
        new WithUART ++
        new freechips.rocketchip.subsystem.WithNoMMIOPort ++
        new freechips.rocketchip.subsystem.WithNoSlavePort ++
        new WithNMemoryChannels(2) ++                          // multi-channel outer mem
        new freechips.rocketchip.subsystem.WithNBigCores(1) ++
        new freechips.rocketchip.system.BaseConfig)

In VCS and Verilator simulation, the DRAM is simulated using the
``SimAXIMem`` module, which simply attaches a single-cycle SRAM to each
memory channel.

If you want a more realistic memory simulation, you can use FireSim, which
can simulate the timing of DDR3 controllers. More documentation on FireSim
memory models is available in the `FireSim docs <https://docs.fires.im/en/latest/>`_.
