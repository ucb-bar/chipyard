.. _generator-index:

Included RTL Generators
============================

A Generator can be thought of as a generalized RTL design, written using a mix of meta-programming and standard RTL.
This type of meta-programming is enabled by the Chisel hardware description language (see :ref:`Tools/Chisel:Chisel`).
A standard RTL design is essentially just a single instance of a design coming from a generator.
However, by using meta-programming and parameter systems, generators can allow for integration of complex hardware designs in automated ways.
The following pages introduce the generators integrated with the Chipyard framework.

Chipyard bundles the source code for the generators, under the ``generators/`` directory.
It builds them from source each time (although the build system will cache results if they have not changed),
so changes to the generators themselves will automatically be used when building with Chipyard and propagate to software simulation, FPGA-accelerated simulation, and VLSI flows.


.. toctree::
   :maxdepth: 2
   :caption: Generators:

   Rocket-Chip
   Rocket
   BOOM
   Hwacha
   Gemmini
   IceNet
   TestChipIP
   SiFive-Generators
   SHA3
   CVA6
   Ibex
   fft
   NVDLA
   Sodor

