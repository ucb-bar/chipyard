.. _chip-communication:

Communicating with the Chip/DUT
===============================

What good is a chip if it can't communicate with the outside world? Chipyard designs communicate to the outside world in
one of two ways:

* using the Front-End Server (FESVR)
* using Rocket Chip's JTAG/DTM interface.

Debugging with the Front-End Server (FESVR)
-------------------------------------------

By default, Chipyard simulations are setup to use the Front-End Server (FESVR) and extra infrastructure to bringup the DUT. However, FESVR can also be used to
bringup a DUT/Chip when a tapeout is completed. FESVR is a C++ library that gives a simple API to reset, send messages, and run programs on a DUT.
It can be added used simulators (VCS, Verilator, FireSim) as well as in a bringup sequence for a taped out chip.

In the case of a simulator like VCS/Verilator, FESVR functions are converted into Tethered Serial Interface (TSI) commands.
These TSI commands are simple R/W commands that are able to probe the DUT's memory space. In simulation, these TSI commands connect to
a ``SimSerial`` (located in the ``generators/testchipip`` project) simulation C++ class that is added to simulation. This ``SimSerial``
device sends the TSI command to the DUT which contains a ``SerialAdapter`` (located in the ``generators/testchipip`` project) that converts
the TSI commands to TileLink requests. In simulation, FESVR resets the DUT, and writes into memory the test program. This is currently the fastest
mechanism to simulate the DUT.

In the case of a chip tapeout bringup, FESVR is used as a library ...

to a main C++ that is run to communicate to a physical chip. In this case, FESVR is normally modified to specify the communication
medium (i.e. send message with TSI over pins in a particular protocol).

Debugging with DTM/JTAG
-----------------------

Chipyard is not setup to use the Debug Test Module (DTM) to bringup the core.
This is because the DTM executes a small loop of code to write the test binary byte-wise into memory
while the default ``SimSerial``/``SerialAdapter``/``FESVR`` interface directly writes to memory.
However, if you want to use JTAG, you must do the following steps to setup a DTM enabled system.

Creating a DTM/JTAG Config
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

First, a DTM config must be created for the system that you want to create.
This involves specifying the SoC top-level to add a DTM as well as configuring that DTM to use JTAG.

.. literalinclude:: ../../generators/example/src/main/scala/RocketConfigs.scala
    :language: scala
    :start-after: DOC include start: JtagRocket
    :end-before: DOC include end: JtagRocket

In this example, the ``WithDTMTop`` mixin specifies that the top-level SoC will instantiate a DTM.
The ``WithJtagDTM`` will configure that instantiated DTM to use JTAG as the bringup method (note: this can be removed if you want a DTM-only bringup).
The rest of the mixins specify the rest of the system (cores, accelerators, etc).

Starting the DTM Simulation
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

After creating the config, call the ``make`` command like the following:

.. code-block:: bash

    cd sims/verilator
    # or
    cd sims/vcs

    make CONFIG=DTMBoomConfig TOP=TopWithDTM MODEL=TestHarnessWithDTM

In this example, this will use the config that you previously specified, as well as set the other parameters that are needed to satisfy the build system.
After that point, you should have a JTAG enabled simulation that you can attach to using OpenOCD and GDB!

Debugging with JTAG
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Please refer to the following resources on how to debug with JTAG.

* https://github.com/chipsalliance/rocket-chip#-debugging-with-gdb
* https://github.com/riscv/riscv-isa-sim#debugging-with-gdb
