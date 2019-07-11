Simulators
=======================

Chipyard supports two classes of simulation:

#. Software RTL simulation using commercial or open-source (Verilator) RTL simulators 
#. FPGA-accelerated full-system simulation using FireSim

Software RTL simulators of Chipyard designs run at O(1 KHz), but compile
quickly and provide full waveforms. Conversly, FPGA-accelerated simulators run
at O(100 MHz), making them appropriate for booting an operating system and
running a complete workload, but have long compile time and poorer debug
visability.

.. toctree::
   :maxdepth: 2
   :caption: Simulators:

   SW-RTL-Simulators
   FPGA-Accelerated-Simulators
