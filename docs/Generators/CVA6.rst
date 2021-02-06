CVA6 Core
====================================

`CVA6 <https://github.com/openhwgroup/cva6>`__ (previously called Ariane) is a 6-stage in-order scalar processor core, originally developed at ETH-Zurich by F. Zaruba and L. Benini.
The `CVA6 core` is wrapped in an `CVA6 tile` so it can be used as a component within the `Rocket Chip SoC generator`.
The core by itself exposes an AXI interface, interrupt ports, and other misc. ports that are connected from within the tile to TileLink buses and other parameterization signals.

.. Warning:: Since the core uses an AXI interface to connect to memory, it is highly recommended to use the core in a single-core setup (since AXI is a non-coherent memory interface).

While the core itself is not a generator, we expose the same parameterization that the CVA6 core provides (i.e. change branch prediction parameters).

.. Warning::  This target does not support Verilator simulation at this time. Please use VCS.

For more information, please refer to the `GitHub repository <https://github.com/openhwgroup/cva6>`__.
