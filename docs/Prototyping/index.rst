Prototyping Flow
================

Chipyard supports FPGA prototyping for local FPGAs supported by `fpga-shells <https://github.com/sifive/fpga-shells>`__.
This includes popular FPGAs such as the Xilinx VCU118 and the Digilent Arty A7-35T/A7-100T board.

.. Note:: While ``fpga-shells`` provides harnesses for other FPGA development boards such as the Xilinx VC707 and some MicroSemi PolarFire, only harnesses for the Xilinx VCU118 and Digilent Arty A7-35T/A7-100T boards are currently supported in Chipyard.
    However, the VCU118 and Arty A7-35T/A7-100T examples demonstrate how a user may implement support for other harnesses provided by fpga-shells.

.. toctree::
   :maxdepth: 2
   :caption: Prototyping Flow:

   General
   VCU118
   Arty
   NexysVideo
