.. _diplomatic_widgets:

Diplomatic Widgets
==================

RocketChip provides a library of diplomatic TileLink and AXI4 widgets.
The most commonly used widgets are documented here. The TileLink widgets
are available from ``freechips.rocketchip.tilelink`` and the AXI4 widgets
from ``freechips.rocketchip.amba.axi4``.

TLBuffer
--------

A widget for buffering TileLink transactions. It simply instantiates queues
for each of the 2 (or 5 for TL-C) decoupled channels. To configure the queue
for each channel, you pass the constructor a
``freechips.rocketchip.diplomacy.BufferParams`` object. The arguments for
this case class are:

 - ``depth: Int`` - The number of entries in the queue
 - ``flow: Boolean`` - If true, combinationally couple the valid signals so
   that an input can be consumed on the same cycle it is enqueued.
 - ``pipe: Boolean`` - If true, combinationally couple the ready signals so
   that single-entry queues can run at full rate.

There is an implicit conversion from ``Int`` available. If you pass an
integer instead of a BufferParams object, the queue will be the depth
given in the integer and ``flow`` and ``pipe`` will both be false.

You can also use one of the predefined BufferParams objects.

 - ``BufferParams.default`` = ``BufferParams(2, false, false)``
 - ``BufferParams.none`` = ``BufferParams(0, false, false)``
 - ``BufferParams.flow`` = ``BufferParams(1, true, false)``
 - ``BufferParams.pipe`` = ``BufferParams(1, false, true)``

**Arguments:**

There are four constructors available with zero, one, two, or five arguments.

The zero-argument constructor uses ``BufferParams.default`` for all of the
channels.

The single-argument constructor takes a ``BufferParams`` object to use for all
channels.

The arguments for the two-argument constructor are:

 - ``ace: BufferParams`` - Parameters to use for the A, C, and E channels.
 - ``bd: BufferParams`` - Parameters to use for the B and D channels

The arguments for the five-argument constructor are

 - ``a: BufferParams`` - Buffer parameters for the A channel
 - ``b: BufferParams`` - Buffer parameters for the B channel
 - ``c: BufferParams`` - Buffer parameters for the C channel
 - ``d: BufferParams`` - Buffer parameters for the D channel
 - ``e: BufferParams`` - Buffer parameters for the E channel

**Example Usage:**

.. code-block:: scala

    // Default settings
    manager0.node := TLBuffer() := client0.node

    // Using implicit conversion to make buffer with 8 queue entries per channel
    manager1.node := TLBuffer(8) := client1.node

    // Use default on A channel but pipe on D channel
    manager2.node := TLBuffer(BufferParams.default, BufferParams.pipe) := client2.node

    // Only add queues for the A and D channel
    manager3.node := TLBuffer(
      BufferParams.default,
      BufferParams.none,
      BufferParams.none,
      BufferParams.default,
      BufferParams.none) := client3.node

AXI4Buffer
----------

Similar to the :ref:`TileLink-Diplomacy-Reference/Widgets:TLBuffer`, but for AXI4. It also takes ``BufferParams`` objects
as arguments.

**Arguments:**

Like TLBuffer, AXI4Buffer has zero, one, two, and five-argument constructors.

The zero-argument constructor uses the default BufferParams for all channels.

The one-argument constructor uses the provided BufferParams for all channels.

The two-argument constructor has the following arguments.

 - ``aw: BufferParams`` - Buffer parameters for the "ar", "aw", and "w" channels.
 - ``br: BufferParams`` - Buffer parameters for the "b", and "r" channels.

The five-argument constructor has the following arguments

 - ``aw: BufferParams`` - Buffer parameters for the "ar" channel
 - ``w: BufferParams`` - Buffer parameters for the "w" channel
 - ``b: BufferParams`` - Buffer parameters for the "b" channel
 - ``ar: BufferParams`` - Buffer parameters for the "ar" channel
 - ``r: BufferParams`` - Buffer parameters for the "r" channel

**Example Usage:**

.. code-block:: scala

    // Default settings
    slave0.node := AXI4Buffer() := master0.node

    // Using implicit conversion to make buffer with 8 queue entries per channel
    slave1.node := AXI4Buffer(8) := master1.node

    // Use default on aw/w/ar channel but pipe on b/r channel
    slave2.node := AXI4Buffer(BufferParams.default, BufferParams.pipe) := master2.node

    // Single-entry queues for aw, b, and ar but two-entry queues for w and r
    slave3.node := AXI4Buffer(1, 2, 1, 1, 2) := master3.node

AXI4UserYanker
--------------

This widget takes an AXI4 port that has a user field and turns it into
one without a user field. The values of the user field from input AR and AW
requests is kept in internal queues associated with the ARID/AWID, which is
then used to associate the correct user field to the responses.

**Arguments:**

 - ``capMaxFlight: Option[Int]`` - (optional) An option which can hold the
   number of requests that can be inflight for each ID. If ``None`` (the default),
   the UserYanker will support the maximum number of inflight requests.

**Example Usage:**

.. code-block:: scala

    nouser.node := AXI4UserYanker(Some(1)) := hasuser.node

AXI4Deinterleaver
-----------------

Multi-beat AXI4 read responses for different IDs can potentially be interleaved.
This widget reorders read responses from the slave so that all of the beats
for a single transaction are consecutive.

**Arguments:**

 - ``maxReadBytes: Int`` - The maximum number of bytes that can be read
   in a single transaction.

**Example Usage:**

.. code-block:: scala

    interleaved.node := AXI4Deinterleaver() := consecutive.node

TLFragmenter
------------

The TLFragmenter widget shrinks the maximum logical transfer size of the
TileLink interface by breaking larger transactions into multiple smaller
transactions.

**Arguments:**

 - ``minSize: Int`` - Minimum size of transfers supported by all outward managers.
 - ``maxSize: Int`` - Maximum size of transfers supported after the Fragmenter is applied.
 - ``alwaysMin: Boolean`` - (optional) Fragment all requests down to minSize (else fragment to maximum supported by manager). (default: false)
 - ``earlyAck: EarlyAck.T`` - (optional) Should a multibeat Put be acknowledged on the first beat or last beat?
   Possible values (default: ``EarlyAck.None``):

    - ``EarlyAck.AllPuts`` - always acknowledge on first beat.
    - ``EarlyAck.PutFulls`` - acknowledge on first beat if PutFull, otherwise acknowledge on last beat.
    - ``EarlyAck.None`` - always acknowledge on last beat.

 - ``holdFirstDeny: Boolean`` - (optional) Allow the Fragmenter to unsafely combine multibeat Gets by taking the first denied for the whole burst. (default: false)

**Example Usage:**

.. code-block:: scala

    val beatBytes = 8
    val blockBytes = 64

    single.node := TLFragmenter(beatBytes, blockBytes) := multi.node

    axi4lite.node := AXI4Fragmenter() := axi4full.node

**Additional Notes**

 - TLFragmenter modifies: PutFull, PutPartial, LogicalData, Get, Hint
 - TLFragmenter passes: ArithmeticData (truncated to minSize if alwaysMin)
 - TLFragmenter cannot modify acquire (could livelock); thus it is unsafe to put caches on both sides

AXI4Fragmenter
--------------

The AXI4Fragmenter is similar to the :ref:`TileLink-Diplomacy-Reference/Widgets:TLFragmenter`.
The AXI4Fragmenter slices all AXI accesses into simple power-of-two sized and aligned transfers
of the largest size supported by the manager. This makes it suitable as a first stage transformation
to apply before an AXI4=>TL bridge. It also makes it suitable for placing after TL=>AXI4 bridge
driving an AXI-lite slave.

**Example Usage:**

.. code-block:: scala

    axi4lite.node := AXI4Fragmenter() := axi4full.node

TLSourceShrinker
----------------

The number of source IDs that a manager sees is usually computed based on the
clients that connect to it. In some cases, you may wish to fix the
number of source IDs. For instance, you might do this if you wish to export
the TileLink port to a Verilog black box. This will pose a problem, however,
if the clients require a larger number of source IDs. In this situation,
you will want to use a TLSourceShrinker.

**Arguments:**

 - ``maxInFlight: Int`` - The maximum number of source IDs that will be sent
   from the TLSourceShrinker to the manager.

**Example Usage:**

.. code-block:: scala

    // client.node may have >16 source IDs
    // manager.node will only see 16
    manager.node := TLSourceShrinker(16) := client.node

AXI4IdIndexer
-------------

The AXI4 equivalent of :ref:`TileLink-Diplomacy-Reference/Widgets:TLSourceShrinker`. This limits the number of
AWID/ARID bits in the slave AXI4 interface. Useful for connecting to external
or black box AXI4 ports.

**Arguments:**

 - ``idBits: Int`` - The number of ID bits on the slave interface.

**Example Usage:**

.. code-block:: scala

    // master.node may have >16 unique IDs
    // slave.node will only see 4 ID bits
    slave.node := AXI4IdIndexer(4) := master.node

**Notes:**

The AXI4IdIndexer will create a ``user`` field on the slave interface, as it
stores the ID of the master requests in this field. If connecting to an AXI4
interface that doesn't have a ``user`` field, you'll need to use the :ref:`TileLink-Diplomacy-Reference/Widgets:AXI4UserYanker`.

TLWidthWidget
-------------

This widget changes the physical width of the TileLink interface. The width
of a TileLink interface is configured by managers, but sometimes you want
the client to see a particular width.

**Arguments:**

 - ``innerBeatBytes: Int`` - The physical width (in bytes) seen by the client

**Example Usage:**

.. code-block:: scala

    // Assume the manager node sets beatBytes to 8
    // With WidthWidget, client sees beatBytes of 4
    manager.node := TLWidthWidget(4) := client.node

TLFIFOFixer
-----------

TileLink managers that declare a FIFO domain must ensure that all requests to
that domain from clients which have requested FIFO ordering see responses in
order. However, they can only control the ordering of their own responses, and
do not have control over how those responses interleave with responses from
other managers in the same FIFO domain. Responsibility for ensuring FIFO order
across managers goes to the TLFIFOFixer.

**Arguments:**

 - ``policy: TLFIFOFixer.Policy`` - (optional) Which managers will the
   TLFIFOFixer enforce ordering on? (default: ``TLFIFOFixer.all``)

The possible values of ``policy`` are:

 - ``TLFIFOFixer.all`` - All managers (including those without a FIFO domain)
   will have ordering guaranteed
 - ``TLFIFOFixer.allFIFO`` - All managers that define a FIFO domain will have
   ordering guaranteed
 - ``TLFIFOFixer.allVolatile`` - All managers that have a RegionType of
   ``VOLATILE``, ``PUT_EFFECTS``, or ``GET_EFFECTS`` will have ordering
   guaranteed (see :ref:`TileLink-Diplomacy-Reference/NodeTypes:Manager Node` for explanation of region types).

TLXbar and AXI4Xbar
-------------------

These are crossbar generators for TileLink and AXI4 which will route requests
from TL client / AXI4 master nodes to TL manager / AXI4 slave nodes based on
the addresses defined in the managers / slaves. Normally, these are constructed
without arguments. However, you can change the arbitration policy, which
determines which client ports get precedent in the arbiters. The default policy
is ``TLArbiter.roundRobin``, but you can change it to ``TLArbiter.lowestIndexFirst``
if you want a fixed arbitration precedence.

**Arguments:**

All arguments are optional.

 - ``arbitrationPolicy: TLArbiter.Policy`` - The arbitration policy to use.
 - ``maxFlightPerId: Int`` - (AXI4 only) The number of transactions with the
   same ID that can be inflight at a time. (default: 7)
 - ``awQueueDepth: Int`` - (AXI4 only) The depth of the write address queue.
   (default: 2)

**Example Usage:**

.. code-block:: scala

    // Instantiate the crossbar lazy module
    val tlBus = LazyModule(new TLXbar)

    // Connect a single input edge
    tlBus.node := tlClient0.node
    // Connect multiple input edges
    tlBus.node :=* tlClient1.node

    // Connect a single output edge
    tlManager0.node := tlBus.node
    // Connect multiple output edges
    tlManager1.node :*= tlBus.node

    // Instantiate a crossbar with lowestIndexFirst arbitration policy
    // Yes, we still use the TLArbiter singleton even though this is AXI4
    val axiBus = LazyModule(new AXI4Xbar(TLArbiter.lowestIndexFirst))

    // The connections work the same as TL
    axiBus.node := axiClient0.node
    axiBus.node :=* axiClient1.node
    axiManager0.node := axiBus.node
    axiManager1.node :*= axiBus.node



TLToAXI4 and AXI4ToTL
---------------------

These are converters between the TileLink and AXI4 protocols. TLToAXI4
takes a TileLink client and connects to an AXI4 slave. AXI4ToTL takes an
AXI4 master and connects to a TileLink manager. Generally you don't want to
override the default arguments of the constructors for these widgets.

**Example Usage:**

.. code-block:: scala

    axi4slave.node :=
        AXI4UserYanker() :=
        AXI4Deinterleaver(64) :=
        TLToAXI4() :=
        tlclient.node

    tlmanager.node :=
        AXI4ToTL() :=
        AXI4UserYanker() :=
        AXI4Fragmenter() :=
        axi4master.node

You will need to add an :ref:`TileLink-Diplomacy-Reference/Widgets:AXI4Deinterleaver` after the TLToAXI4 converter
because it cannot deal with interleaved read responses. The TLToAXI4 converter
also uses the AXI4 user field to store some information, so you will need an
:ref:`TileLink-Diplomacy-Reference/Widgets:AXI4UserYanker` if you want to connect to an AXI4 port without user
fields.

Before you connect an AXI4 port to the AXI4ToTL widget, you will need to
add an :ref:`TileLink-Diplomacy-Reference/Widgets:AXI4Fragmenter` and :ref:`TileLink-Diplomacy-Reference/Widgets:AXI4UserYanker` because the converter cannot
deal with multi-beat transactions or user fields.

TLROM
------

The TLROM widget provides a read-only memory that can be accessed using
TileLink. Note: this widget is in the ``freechips.rocketchip.devices.tilelink``
package, not the ``freechips.rocketchip.tilelink`` package like the others.

**Arguments:**

 - ``base: BigInt`` - The base address of the memory
 - ``size: Int`` - The size of the memory in bytes
 - ``contentsDelayed: => Seq[Byte]`` - A function which, when called generates
   the byte contents of the ROM.
 - ``executable: Boolean`` - (optional) Specify whether the CPU can fetch
   instructions from the ROM (default: ``true``).
 - ``beatBytes: Int`` - (optional) The width of the interface in bytes.
   (default: 4).
 - ``resources: Seq[Resource]`` - (optional) Sequence of resources to add to
   the device tree.

**Example Usage:**

.. code-block:: scala

    val rom = LazyModule(new TLROM(
      base = 0x100A0000,
      size = 64,
      contentsDelayed = Seq.tabulate(64) { i => i.toByte },
      beatBytes = 8))
    rom.node := TLFragmenter(8, 64) := client.node

**Supported Operations:**

The TLROM only supports single-beat reads. If you want to perform multi-beat
reads, you should attach a TLFragmenter in front of the ROM.

TLRAM and AXI4RAM
-----------------

The TLRAM and AXI4RAM widgets provide read-write memories implemented as SRAMs.

**Arguments:**

 - ``address: AddressSet`` - The address range that this RAM will cover.
 - ``cacheable: Boolean`` - (optional) Can the contents of this RAM be cached.
   (default: ``true``)
 - ``executable: Boolean`` - (optional) Can the contents of this RAM be fetched
   as instructions. (default: ``true``)
 - ``beatBytes: Int`` - (optional) Width of the TL/AXI4 interface in bytes.
   (default: 4)
 - ``atomics: Boolean`` - (optional, TileLink only) Does the RAM support
   atomic operations? (default: ``false``)

**Example Usage:**

.. code-block:: scala

    val xbar = LazyModule(new TLXbar)

    val tlram = LazyModule(new TLRAM(
      address = AddressSet(0x1000, 0xfff)))

    val axiram = LazyModule(new AXI4RAM(
      address = AddressSet(0x2000, 0xfff)))

    tlram.node := xbar.node
    axiram := TLToAXI4() := xbar.node

**Supported Operations:**

TLRAM only supports single-beat TL-UL requests. If you set ``atomics`` to true,
it will also support Logical and Arithmetic operations. Use a ``TLFragmenter``
if you want multi-beat reads/writes.

AXI4RAM only supports AXI4-Lite operations, so multi-beat reads/writes and
reads/writes smaller than full-width are not supported. Use an ``AXI4Fragmenter``
if you want to use the full AXI4 protocol.



