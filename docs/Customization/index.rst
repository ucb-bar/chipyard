Customization
================================

These guides will walk you through customization of your system-on-chip:

- Constructing heterogenous systems-on-chip using the existing Chipyard generators and configuration system.

- Constructing SoCs with a NoC (network-on-chip) based interconnect using Constellation

- How to include your custom Chisel sources in the Chipyard build system

- Adding custom core

- Adding custom RoCC accelerators to an existing Chipyard core (BOOM or Rocket)

- Adding custom MMIO widgets to the Chipyard memory system by Tilelink or AXI4, with custom Top-level IOs

- Adding custom Dsptools based blocks as MMIO widgets.

- Standard practices for using Keys, Traits, and Configs to parameterize your design

- Customizing the memory hierarchy

- Connect widgets which act as TileLink masters

- Adding custom blackboxed Verilog to a Chipyard design

We also provide information on:

- The boot process for Chipyard SoCs

- Examples of FIRRTL transforms used in Chipyard, and where they are specified

We recommend reading all these pages in order. Hit next to get started!

.. toctree::
   :maxdepth: 2
   :caption: Customization:

   Heterogeneous-SoCs
   NoC-SoCs
   Custom-Chisel
   Custom-Core
   RoCC-or-MMIO
   RoCC-Accelerators
   MMIO-Peripherals
   Dsptools-Blocks
   Keys-Traits-Configs
   DMA-Devices
   Incorporating-Verilog-Blocks
   Memory-Hierarchy
   Boot-Process
   Firrtl-Transforms
   IOBinders
