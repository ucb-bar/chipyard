Test Chip IP
============

Chipyard includes a Test Chip IP library which provides various hardware
widgets that may be useful when designing SoCs. This includes a :ref:`Generators/TestChipIP:SimTSI`,
:ref:`Generators/TestChipIP:Block Device Controller`, :ref:`Generators/TestChipIP:TileLink SERDES`, :ref:`Generators/TestChipIP:TileLink Switcher`,
:ref:`Generators/TestChipIP:TileLink Ring Network`, and :ref:`Generators/TestChipIP:UART Adapter`.

SimTSI
--------------

The SimTSI and TSIToTileLink are used by tethered test chips to communicate with the host
processor. An instance of RISC-V frontend server running on the host CPU
can send commands to the TSIToTileLink to read and write data from the memory
system. The frontend server uses this functionality to load the test program
into memory and to poll for completion of the program. More information on
this can be found in :ref:`Customization/Boot-Process:Chipyard Boot Process`.

Block Device Controller
-----------------------

The block device controller provides a generic interface for secondary storage.
This device is primarily used in FireSim to interface with a block device
software simulation model. The default Linux configuration in `firesim-software <https://github.com/firesim/firesim-software>`_

To add a block device to your design, add the ``WithBlockDevice`` config fragment to your configuration.


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

TileLink Ring Network
---------------------

TestChipIP provides a TLRingNetwork generator that has a similar interface
to the TLXbar provided by RocketChip, but uses ring networks internally rather
than crossbars. This can be useful for chips with very wide TileLink networks
(many cores and L2 banks) that can sacrifice cross-section bandwidth to relieve
wire routing congestion. Documentation on how to use the ring network can be
found in :ref:`Customization/Memory-Hierarchy:The System Bus`. The implementation itself can be found
`here <https://github.com/ucb-bar/testchipip/blob/master/src/main/scala/Ring.scala>`_,
and may serve as an example of how to implement your own TileLink network with
a different topology.

UART Adapter
------------

The UART Adapter is a device that lives in the TestHarness and connects to the
UART port of the DUT to simulate communication over UART (ex. printing out to UART
during Linux boot). In addition to working with ``stdin/stdout`` of the host, it is able to
output a UART log to a particular file using ``+uartlog=<NAME_OF_FILE>`` during simulation.

By default, this UART Adapter is added to all systems within Chipyard by adding the
``WithUART`` and ``WithUARTAdapter`` configs.

SPI Flash Model
---------------

The SPI flash model is a device that models a simple SPI flash device. It currently
only supports single read, quad read, single write, and quad write instructions. The
memory is backed by a file which is provided using ``+spiflash#=<NAME_OF_FILE>``,
where ``#`` is the SPI flash ID (usually ``0``).
