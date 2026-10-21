Prototyping Flow
================

Chipyard supports FPGA prototyping for local FPGAs supported by `fpga-shells <https://github.com/sifive/fpga-shells>`__.
This includes popular FPGAs such as the AMD/Xilinx VCU118 and ZCU104, and the Digilent Arty A7-35T/A7-100T, Genesys 2, and Nexys Video boards.

.. Note:: While ``fpga-shells`` provides harnesses for other FPGA development boards such as the AMD/Xilinx VC707 and some MicroSemi PolarFire, only harnesses for the AMD/Xilinx VCU118 and ZCU104, and Digilent Arty A7-35T/A7-100T, Genesys 2, and Nexys Video boards are currently supported in Chipyard.
    However, the available examples demonstrate how a user may implement support for other harnesses provided by fpga-shells.

.. toctree::
   :maxdepth: 2
   :caption: Prototyping Flow:

   General
   VCU118
   ZCU104
   Arty
   Genesys2
   NexysVideo
