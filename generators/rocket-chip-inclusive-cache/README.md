# Rocket Chip SoC Inclusive Cache Generator

This `block` package contains an RTL generator for creating instances of a coherent, last-level, inclusive cache.
The `InclusiveCache` controller enforces coherence among a set of caching clients
using an invalidation-based coherence policy implemetated on top of the the TileLink 1.8.1 coherence messaging protocol.
This policy is implemented using a full-map of directory bits stored with each cache block's metadata tag.

The `InclusiveCache` is a TileLink adapter;
it can be used as a drop-in replacement for Rocket-Chip's `tilelink.BroadcastHub` coherence manager.
It additionally supplies a SW-controlled interface for flusing cache blocks based on physical addresses.

The following parameters of the cache are easily `Config`-urable: 
size, ways, banking and sub-banking factors, external bandwidth, network interface buffering.

Stand-alone unit tests coming soon.

This repository is a replacement for https://github.com/sifive/block-inclusivecache-sifive
