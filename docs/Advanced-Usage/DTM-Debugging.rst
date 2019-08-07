Debugging with DTM/JTAG
===============================

By default, Chipyard is not setup to use the Debug Test Module (DTM) to bringup the core.
Instead, Chipyard uses TSI commands to bringup the core (which normally results in a faster simulation).
TSI simulations use the SimSerial interface to directly write the test binary into memory, while the DTM 
executes a small loop of code to write the test binary byte-wise into memory.
However, if you want to use JTAG, you must do the following steps to setup a DTM enabled system.

Creating a DTM/JTAG Config
-------------------------------------------

First, a DTM config must be created for the system that you want to create.
This involves specifying the SoC top-level to add a DTM as well as configuring that DTM to use JTAG.

.. code-block:: scala

    class DTMBoomConfig extends Config(
      new WithDTMBoomRocketTop ++
      new WithBootROM ++
      new WithJtagDTM ++
      new boom.common.SmallBoomConfig)

In this example, the ``WithDTMBoomRocketTop`` mixin specifies that the top-level SoC will instantiate a DTM.
The ``WithJtagDTM`` will configure that instantiated DTM to use JTAG as the bringup method (note: this can be removed if you want a DTM-only bringup).
The rest of the mixins specify the rest of the system (cores, accelerators, etc).

Starting the DTM Simulation
-------------------------------------------

After creating the config, call the ``make`` command like the following:

.. code-block:: bash

    cd sims/verilator
    # or
    cd sims/vcs

    make CONFIG=DTMBoomConfig TOP=BoomRocketTopWithDTM MODEL=TestHarnessWithDTM

In this example, this will use the config that you previously specified, as well as set the other parameters that are needed to satisfy the build system.
After that point, you should have a JTAG enabled simulation that you can attach to using OpenOCD and GDB!

Debugging with JTAG
-------------------------------------------------------

Please refer to the following resources on how to debug with JTAG.

* https://github.com/chipsalliance/rocket-chip#-debugging-with-gdb
* https://github.com/riscv/riscv-isa-sim#debugging-with-gdb
