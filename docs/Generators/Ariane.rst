Ariane Core
====================================

`Ariane <https://github.com/pulp-platform/ariane>`__ is a 6-stage in-order scalar processor core, originally developed at ETH-Zurich by F. Zaruba and L. Benini.
The `Ariane core` is wrapped in an `Ariane tile` so it can be used as a component within the `Rocket Chip SoC generator`.
The core by itself exposes an AXI interface, interrupt ports, and other misc. ports that are connected from within the tile to TileLink buses and other parameterization signals.

.. Warning:: Since the core uses an AXI interface to connect to memory, it is highly recommended to use the core in a single-core setup (since AXI is a non-coherent memory interface).

While the core itself is not a generator, we expose the same parameterization that the Ariane core provides (i.e. change branch prediction parameters).

For more information, please refer to the `GitHub repository <https://github.com/pulp-platform/ariane>`__.
