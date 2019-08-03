Dsptools
===============================

`Dsptools <https://github.com/ucb-bar/dsptools/>`__ is a Chisel library for writing custom signal processing hardware.
Additionally, dsptools is useful for integrating custom signal processing hardware into an SoC (especially a Rocket-based SoC).

Some features:

* Complex type
* Typeclasses for writing polymorphic hardware generators
  * For example, write one FIR filter generator that works for real or complex inputs
* Extensions to Chisel testers for fixed point and floating point types
* A diplomatic implementation of AXI4-Stream
* Models for verifying APB, AXI-4, and TileLink interfaces with chisel-testers
* DSP building blocks
