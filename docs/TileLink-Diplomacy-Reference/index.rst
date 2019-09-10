TileLink and Diplomacy Reference
================================

TileLink is the cache coherence and memory protocol used by RocketChip and
other Chipyard generators. It is how different modules like caches, memories,
peripherals, and DMA devices communicate with each other.

RocketChip's TileLink implementation is built on top of Diplomacy, a framework
for exchanging configuration information among Chisel generators in a two-phase
elaboration scheme.

A brief overview of how to connect simple TileLink widgets can be found
in the :ref:`Adding-an-Accelerator` section. This section will provide a
detailed reference for the TileLink and Diplomacy functionality provided by
RocketChip.

A detailed specification of the TileLink 1.7 protocol can be found on the
`SiFive website <https://sifive.cdn.prismic.io/sifive%2F57f93ecf-2c42-46f7-9818-bcdd7d39400a_tilelink-spec-1.7.1.pdf>`.


.. toctree::
    :maxdepth: 2
    :caption: Reference

    NodeTypes
    Diplomacy-Connectors
    EdgeFunctions
    Register-Router
    Widgets
