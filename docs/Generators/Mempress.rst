Mempress
====================================

Mempress is a RoCC accelerator that generates memory requests through TileLink. It sends out requests as hard as it can to stress test the memory hierarchy of the Chipyard/Rocketchip-based SoC.

Mempress can generate multiple **streams** of memory requests. Each stream can be set up to generate read or write requests and configured to generate strided or random access patterns. Furthermore, the memory footprint of each stream is also configurable.

To add the Mempress unit into the SoC, you should add the ``mempress.WithMemPress`` config fragment to the SoC configurations.
