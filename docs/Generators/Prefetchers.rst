Prefetchers
====================================

The BAR-fetchers library is a collection of Chisel-implemented prefetchers, designed for compatibility with Chipyard and Rocket-Chip SoCs.
This package implements a generic prefetcher API, and example implementations of NextLine, Strided, and AMPM prefetchers.

Prefetchers can be instantiated in front of a L1D HellaCache, or as TileLink nodes in front of some TileLink bus.

An example configuration using prefetchers is found in the ``PrefetchingRocketConfig``
