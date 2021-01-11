.. _node_types:

TileLink Node Types
===================

Diplomacy represents the different components of an SoC as nodes of a
directed acyclic graph. TileLink nodes can come in several different types.

Client Node
-----------

TileLink clients are modules that initiate TileLink transactions by sending
requests on the A channel and receive responses on the D channel. If the
client implements TL-C, it will receive probes on the B channel, send releases
on the C channel, and send grant acknowledgements on the E channel.

The L1 caches and DMA devices in RocketChip/Chipyard have client nodes.

You can add a TileLink client node to your LazyModule using the TLHelper
object from testchipip like so:

.. literalinclude:: ../../generators/chipyard/src/main/scala/example/NodeTypes.scala
    :language: scala
    :start-after: DOC include start: MyClient
    :end-before: DOC include end: MyClient

The ``name`` argument identifies the node in the Diplomacy graph. It is the
only required argument for TLClientParameters.

The ``sourceId`` argument specifies the range of source identifiers that this
client will use. Since we have set the range to [0, 4) here, this client will
be able to send up to four requests in flight at a time. Each request will
have a distinct value in its source field. The default value for this field
is ``IdRange(0, 1)``, which means it would only be able to send a single
request inflight.

The ``requestFifo`` argument is a boolean option which defaults to false.
If it is set to true, the client will request that downstream managers that
support it send responses in FIFO order (that is, in the same order the
corresponding requests were sent).

The ``visibility`` argument specifies the address ranges that the client will
access. By default it is set to include all addresses. In this example, we set
it to contain a single address range ``AddressSet(0x10000, 0xffff)``, which
means that the client will only be able to access addresses from 0x10000 to
0x1ffff.  normally do not specify this, but it can help downstream crossbar
generators optimize the hardware by not arbitrating the client to managers with
address ranges that don't overlap with its visibility.

Inside your lazy module implementation, you can call ``node.out`` to get a
list of bundle/edge pairs. If you used the TLHelper, you only specified a
single client edge, so this list will only have one pair.

The ``tl`` bundle is a Chisel hardware bundle that connects to the IO of this
module. It contains two (in the case of TL-UL and TL-UH) or five (in the case
of TL-C) decoupled bundles corresponding to the TileLink channels. This is
what you should connect your hardware logic to in order to actually send/receive
TileLink messages.

The ``edge`` object represents the edge of the Diplomacy graph. It contains
some useful helper functions which will be documented in
:ref:`TileLink-Diplomacy-Reference/EdgeFunctions:TileLink Edge Object Methods`.

Manager Node
------------

TileLink managers take requests from clients on the A channel and send
responses back on the D channel. You can create a manager node using the
TLHelper like so:

.. literalinclude:: ../../generators/chipyard/src/main/scala/example/NodeTypes.scala
    :language: scala
    :start-after: DOC include start: MyManager
    :end-before: DOC include end: MyManager

The ``makeManagerNode`` method takes two arguments. The first is ``beatBytes``,
which is the physical width of the TileLink interface in bytes. The second
is a TLManagerParameters object.

The only required argument for ``TLManagerParameters`` is the ``address``,
which is the set of address ranges that this manager will serve.
This information is used to route requests from the clients. In this example,
the manager will only take requests for addresses from 0x20000 to 0x20fff.
The second argument in ``AddressSet`` is a mask, not a size.
You should generally set it to be one less than a power of two. Otherwise,
the addressing behavior may not be what you expect.

The second argument is ``resources``, which is usually retrieved from a
``Device`` object. In this case, we use a ``SimpleDevice`` object.
This argument is necessary if you want to add an entry to the DeviceTree in
the BootROM so that it can be read by a Linux driver. The two arguments to
``SimpleDevice`` are the name and compatibility list for the device tree
entry. For this manager, then, the device tree entry would look like

.. code-block:: text

    L12: my-device@20000 {
        compatible = "tutorial,my-device0";
        reg = <0x20000 0x1000>;
    };

The next argument is ``regionType``, which gives some information about
the caching behavior of the manager. There are seven region types, listed below:

1. ``CACHED``      - An intermediate agent may have cached a copy of the region for you.
2. ``TRACKED``     - The region may have been cached by another master, but coherence is being provided.
3. ``UNCACHED``    - The region has not been cached yet, but should be cached when possible.
4. ``IDEMPOTENT``  - Gets return most recently put content, but content should not be cached.
5. ``VOLATILE``    - Content may change without a put, but puts and gets have no side effects.
6. ``PUT_EFFECTS`` - Puts produce side effects and so must not be combined/delayed.
7. ``GET_EFFECTS`` - Gets produce side effects and so must not be issued speculatively.

Next is the ``executable`` argument, which determines if the CPU is allowed to
fetch instructions from this manager. By default it is false, which is what
most MMIO peripherals should set it to.

The next six arguments start with ``support`` and determine the different
A channel message types that the manager can accept. The definitions of the
message types are explained in :ref:`TileLink-Diplomacy-Reference/EdgeFunctions:TileLink Edge Object Methods`.
The ``TransferSizes`` case class specifies the range of logical sizes (in bytes)
that the manager can accept for the particular message type. This is an inclusive
range and all logical sizes must be powers of two. So in this case, the manager
can accept requests with sizes of 1, 2, 4, or 8 bytes.

The final argument shown here is the ``fifoId`` setting, which determines
which FIFO domain (if any) the manager is in. If this argument is set to ``None``
(the default), the manager will not guarantee any ordering of the responses.
If the ``fifoId`` is set, it will share a FIFO domain with all other managers
that specify the same ``fifoId``. This means that client requests sent to
that FIFO domain will see responses in the same order.

Register Node
-------------

While you can directly specify a manager node and write all of the logic
to handle TileLink requests, it is usually much easier to use a register node.
This type of node provides a ``regmap`` method that allows you to specify
control/status registers and automatically generates the logic to handle the
TileLink protocol. More information about how to use register nodes can be
found in :ref:`TileLink-Diplomacy-Reference/Register-Router:Register Router`.

Identity Node
-------------

Unlike the previous node types, which had only inputs or only outputs, the
identity node has both. As its name suggests, it simply connects the inputs
to the outputs unchanged. This node is mainly used to combine multiple
nodes into a single node with multiple edges. For instance, say we have two
client lazy modules, each with their own client node.

.. literalinclude:: ../../generators/chipyard/src/main/scala/example/NodeTypes.scala
    :language: scala
    :start-after: DOC include start: MyClient1+MyClient2
    :end-before: DOC include end: MyClient1+MyClient2

Now we instantiate these two clients in another lazy module and expose their
nodes as a single node.

.. literalinclude:: ../../generators/chipyard/src/main/scala/example/NodeTypes.scala
    :language: scala
    :start-after: DOC include start: MyClientGroup
    :end-before: DOC include end: MyClientGroup

We can also do the same for managers.

.. literalinclude:: ../../generators/chipyard/src/main/scala/example/NodeTypes.scala
    :language: scala
    :start-after: DOC include start: MyManagerGroup
    :end-before: DOC include end: MyManagerGroup

If we want to connect the client and manager groups together, we can now do this.

.. literalinclude:: ../../generators/chipyard/src/main/scala/example/NodeTypes.scala
    :language: scala
    :start-after: DOC include start: MyClientManagerComplex
    :end-before: DOC include end: MyClientManagerComplex

The meaning of the ``:=*`` operator is explained in more detail in the
:ref:`TileLink-Diplomacy-Reference/Diplomacy-Connectors:Diplomacy Connectors` section. In summary, it connects two nodes together
using multiple edges. The edges in the identity node are assigned in order,
so in this case ``client1.node`` will eventually connect to ``manager1.node``
and ``client2.node`` will connect to ``manager2.node``.

The number of inputs to an identity node should match the number of outputs.
A mismatch will cause an elaboration error.

Adapter Node
------------

Like the identity node, the adapter node takes some number of inputs and
produces the same number of outputs. However, unlike the identity node, the
adapter node does not simply pass the connections through unchanged.
It can change the logical and physical interfaces between input and output and
rewrite messages going through. RocketChip provides a library of adapters,
which are catalogued in :ref:`TileLink-Diplomacy-Reference/Widgets:Diplomatic Widgets`.

You will rarely need to create an adapter node yourself, but the invocation is
as follows.

.. code-block:: scala

    val node = TLAdapterNode(
      clientFn = { cp =>
        // ..
      },
      managerFn = { mp =>
        // ..
      })

The ``clientFn`` is a function that takes the ``TLClientPortParameters`` of
the input as an argument and returns the corresponding parameters for the
output. The ``managerFn`` takes the ``TLManagerPortParameters`` of the output
as an argument and returns the corresponding parameters for the input.

Nexus Node
----------

The nexus node is similar to the adapter node in that it has a different
output interface than input interface. But it can also have a different
number of inputs than it does outputs. This node type is mainly used by
the ``TLXbar`` widget, which provides a TileLink crossbar generator. You will
also likely not need to define this node type manually, but its invocation is
as follows.

.. code-block:: scala

    val node = TLNexusNode(
      clientFn = { seq =>
        // ..
      },
      managerFn = { seq =>
        // ..
      })

This has similar arguments as the adapter node's constructor, but instead of
taking single parameters objects as arguments and returning single objects
as results, the functions take and return sequences of parameters. And as you
might expect, the size of the returned sequence need not be the same size as
the input sequence.
