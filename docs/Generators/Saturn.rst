Saturn
========

Saturn is a parameterized RISC-V Vector Unit generator currently supporting integration with the Rocket and Shuttle cores.
Saturn implements a compact short-vector-length vector microarchitecture suitable for deployment in a DSP-optimized core or area-efficient general-purpose core.

More documentation on Saturn will be released in the future.
A partial listing of supported Saturn configurations is in ``generators/chipyard/src/main/scala/config/SaturnConfigs.scala``.

For now, the recommended Saturn configuration is ``GENV256D128ShuttleConfig``, which builds a dual-issue core with 256-bit VLEN, 128-bit wide SIMD datapath, and separate floating-point and integer vector issue units.


 * Full support for ``V`` application-profile RVV 1.0
 * Precise traps with virtual memory
 * Indexed/strided/segmented loads and stores
 * Mask operations
 * Register-gather + reductions
 * ``Zvfh`` support for vector half-precision floating-point (FP16)
 * ``Zvbb`` support for vector basic bit-manipulation instructions
 * ``Zve64d`` support for vector FP32 and FP64
 * Configurable vector length, from ``Zvl64b`` up (tested to ``Zvl4096b``)
 * Configurable datapath width, from 64b up (tested to 512b)

