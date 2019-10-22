Test Chip IP
============

Chipyard includes a Test Chip IP library which provides various hardware
widgets that may be useful when designing SoCs. This includes a :ref:`Serial Adapter`,
:ref:`Block Device Controller`, :ref:`TileLink SERDES`, and :ref:`TileLink Switcher`.

Serial Adapter
--------------

The serial adapter is used by tethered test chips to communicate with the host
processor. An instance of RISC-V frontend server running on the host CPU 
can send commands to the serial adapter to read and write data from the memory
system. The frontend server uses this functionality to load the test program
into memory and to poll for completion of the program. More information on
this can be found in :ref:`Chipyard Boot Process`.

Block Device Controller
-----------------------

The block device controller provides a generic interface for secondary storage.
This device is primarily used in FireSim to interface with a block device
software simulation model. The default Linux configuration in `firesim-software <https://github.com/firesim/firesim-software>`_

To add a block device to your design, add ``HasPeripheryBlockDevice`` to your
lazy module and ``HasPeripheryBlockDeviceModuleImp`` to the implementation.
Then add the ``WithBlockDevice`` config mixin to your configuration.


TileLink SERDES
---------------

The TileLink SERDES in the Test Chip IP library allow TileLink memory requests
to be serialized so that they can be carried off chip through a serial link.
The five TileLink channels are multiplexed over two SERDES channels, one in
each direction.

There are three different variants provided by the library, ``TLSerdes``
exposes a manager interface to the chip, tunnels A, C, and E channels on
its outbound link, and tunnels B and D channels on its inbound link. ``TLDesser``
exposes a client interface to the chip, tunnels A, C, and E on its inbound link,
and tunnels B and D on its outbound link. Finally, ``TLSerdesser`` exposes
both client and manager interface to the chip and can tunnel all channels in
both directions.

For an example of how to use the SERDES classes, take a look at the
``SerdesTest`` unit test in `the Test Chip IP unit test suite
<https://github.com/ucb-bar/testchipip/blob/master/src/main/scala/Unittests.scala>`_.

TileLink Switcher
-----------------

The TileLink switcher is used when the chip has multiple possible memory
interfaces and you would like to select which channels to map your memory
requests to at boot time. It exposes a client node, multiple manager nodes,
and a select signal. Depending on the setting of the select signal, requests
from the client node will be directed to one of the manager nodes.
The select signal must be set before any TileLink messages are sent and be
kept stable throughout the remainder of operation. It is not safe to change
the select signal once TileLink messages have begun sending.

For an example of how to use the switcher, take a look at the ``SwitcherTest``
unit test in the `Test Chip IP unit tests <https://github.com/ucb-bar/testchipip/blob/master/src/main/scala/Unittests.scala>`_.
