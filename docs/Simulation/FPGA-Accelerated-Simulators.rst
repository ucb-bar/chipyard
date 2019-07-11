FPGA-Accelerated Simulators
==============================

FireSim
-----------------------

`FireSim <https://fires.im/>`__ is an open-source cycle-accurate FPGA-accelerated full-system hardware simulation platform that runs on cloud FPGAs (Amazon EC2 F1).
FireSim allows RTL-level simulation at orders-of-magnitude faster speeds than software RTL simulators.
FireSim also provides additional device models to allow full-system simulation, including memory models and network models.

FireSim currently supports running only on Amazon EC2 F1 FPGA-enabled virtual instances on the public cloud.
In order to simulate your Chipyard design using FireSim, you should follow the following steps:

Follow the initial EC2 setup instructions as detailed in the `FireSim documentation  <http://docs.fires.im/en/latest/Initial-Setup/index.html>`__.
Then clone your full Chipyard repository onto your Amazon EC2 FireSim manager instance.

Enter the ``sims/FireSim`` directory, and follow the FireSim instructions for `running a simulation <http://docs.fires.im/en/latest/Running-Simulations-Tutorial/index.html>`__.
