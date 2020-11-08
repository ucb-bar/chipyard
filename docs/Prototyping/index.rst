Prototyping Flow
================

Chipyard supports FPGA prototyping for local FPGAs supported by `fpga-shells <https://github.com/sifive/fpga-shells>`__.
This includes popular FPGAs such as the Xilinx VCU118 and the Xilinx Arty board.
FPGA prototyping allows for orders-of-magnitude faster speeds than software RTL simulators at the cost of slower compile times and less design introspection.

.. Note:: While ``fpga-shells`` also supports Xilinx VC707 and some MicroSemi PolarFire boards, currently only the VCU118 and Arty boards are explicitly supported in Chipyard.
    However, using the VCU118/Arty examples would be useful to see how to implement VC707/PolarFire support.

.. toctree::
   :maxdepth: 2
   :caption: Prototyping Flow:

   General
   VCU118
   Arty
