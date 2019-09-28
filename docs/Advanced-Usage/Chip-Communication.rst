.. _chip-communication:

Communicating with the DUT
===============================

There are two types of DUTs that can be made: `tethered` or `standalone` DUTs.
A `tethered` DUT is where a host computer (or just host) must send transactions to the DUT to bringup a program.
This differs from a `standalone` DUT that can bringup itself (has its own bootrom, loads programs itself, etc).
An example of a tethered DUT is a Chipyard simulation where the host loads the test program into the DUTs memory and signals to the DUT that the program is ready to run.
An example of a standalone DUT is a Chipyard simulation where a program can be loaded from an SDCard by default.
In this section, we mainly describe how to communicate to tethered DUTs.

There are two ways the host (otherwise known as the outside world) can communicate with a tethered Chipyard DUT:

* Using the Tethered Serial Interface (TSI) or the Debug Module Interface (DMI) with the Front-End Server (FESVR) to communicate with the DUT
* Using the JTAG interface with OpenOCD and GDB to communicate with the DUT

The following picture shows a block diagram view of all the supported communication mechanisms
split between the host and the simulation.

.. image:: ../_static/images/chip-communication.png

Using the Tethered Serial Interface (TSI) or the Debug Module Interface (DMI)
-----------------------------------------------------------------------------

If you are using TSI or DMI to communicate with the DUT, you are using
the Front-End Server (FESVR) to facilitate communication between the host and the DUT.

Primer on the Front-End Server (FESVR)
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

FESVR is a C++ library that manages communication
between a host machine and a RISC-V DUT. For debugging, it provides a simple API to reset,
send messages, and load/run programs on a DUT. It also emulates peripheral devices.
It can be incorporated with simulators (VCS, Verilator, FireSim), or used in a bringup sequence
for a taped out chip.

Specifically, FESVR uses the Host Target Interface (HTIF), a communication protocol,
to speak with the DUT. HTIF is a non-standard Berkeley protocol that uses a FIFO non-blocking
interface to communicate with the DUT. It defines a protocol where you can read/write memory,
load/start/stop the program, and more. Both TSI and DMI implement this HTIF protocol differently
in order to communicate with the DUT.

Using the Tethered Serial Interface (TSI)
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

By default, Chipyard uses the Tethered Serial Interface (TSI) to communicate with the DUT.
TSI protocol is an implementation of HTIF that is used to send commands to the
RISC-V DUT. These TSI commands are simple R/W commands
that are able to probe the DUT's memory space. During simulation, the host sends TSI commands to a
simulation stub called ``SimSerial`` (C++ class) that resides in a ``SimSerial`` verilog module
(both are located in the ``generators/testchipip`` project). This ``SimSerial`` verilog module then
sends the TSI command recieved by the simulation stub into the DUT which then converts the TSI
command into a TileLink request. This conversion is done by the ``SerialAdapter`` module
(located in the ``generators/testchipip`` project). In simulation, FESVR
resets the DUT, writes into memory the test program, and indicates to the DUT to start the program
through an interrupt (see :ref:`Chipyard Boot Process`). Using TSI is currently the fastest
mechanism to communicate with the DUT in simulation.

In the case of a chip tapeout bringup, TSI commands can be sent over a custom communication
medium to communicate with the chip. For example, some Berkeley tapeouts have a FPGA
with a RISC-V soft-core that runs FESVR. The FESVR on the soft-core sends TSI commands
to a TSI-to-TileLink converter living on the FPGA (i.e. ``SerialAdapter``). Then this converter
sends the converted TileLink commands over a serial link to the chip. The following image shows this flow:

.. image:: ../_static/images/chip-bringup.png

Using the Debug Module Interface (DMI)
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Another option to interface with the DUT is to use the Debug Module Interface (DMI).
Similar to TSI, the DMI protocol is an implementation of HTIF.
In order to communicate with the DUT with the DMI protocol, the DUT needs to contain a Debug Transfer Module (DTM).
The DTM is given in the `RISC-V Debug Specification <https://riscv.org/specifications/debug-specification/>`__
and is responsible for managing communication between the DUT and whatever lives on the other side of the DMI (in this case FESVR).
This is implemented in the Rocket Chip ``Subsystem`` by having the ``HasPeripheryDebug`` and ``HasPeripheryDebugModuleImp`` mixins.
 During simulation, the host sends DMI commands to a
simulation stub called ``SimDTM`` (C++ class) that resides in a ``SimDTM`` verilog module
(both are located in the ``generators/rocket-chip`` project). This ``SimDTM`` verilog module then
sends the DMI command recieved by the simulation stub into the DUT which then converts the DMI
command into a TileLink request. This conversion is done by the DTM named ``DebugModule`` in the ``generators/rocket-chip`` project.
When the DTM receives the program to load, it starts to write the binary byte-wise into memory.
This is considerably slower than the TSI protocol communication pipeline (i.e. ``SimSerial``/``SerialAdapter``/TileLink)
which directly writes the program binary to memory.
Thus, Chipyard removes the DTM by default in favor of the TSI protocol for DUT communication.

Starting the TSI or DMI Simulation
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

All default Chipyard configurations use TSI to communicate between the simulation and the simulated SoC/DUT. Hence, when running a
software RTL simulation, as is indicated in the :ref:`Software RTL Simulation` section, you are in-fact using TSI to communicate with the DUT. As a
reminder, to run a software RTL simulation, run:

.. code-block:: bash

   cd sims/verilator
   # or
   cd sims/vcs

   make CONFIG=LargeBoomConfig run-asm-tests

If you would like to build and simulate a Chipyard configuration with a DTM configured for DMI communication, then you must create a
top-level system with the DTM (``TopWithDTM``), a test-harness to connect to the DTM (``TestHarnessWithDTM``), as well as a config to use that top-level system.

.. literalinclude:: ../../generators/example/src/main/scala/RocketConfigs.scala
    :language: scala
    :start-after: DOC include start: DmiRocket
    :end-before: DOC include end: DmiRocket

In this example, the ``WithDTMTop`` mixin specifies that the top-level SoC will instantiate a DTM (that by default is setup to use DMI).
The rest of the mixins specify the rest of the system (cores, accelerators, etc).
Then you can run simulations with the new DMI-enabled top-level and test-harness.

.. code-block:: bash

    cd sims/verilator
    # or
    cd sims/vcs

    make CONFIG=dmiRocketConfig TOP=TopWithDTM MODEL=TestHarnessWithDTM run-asm-tests

Using the JTAG Interface
------------------------

The main way to use JTAG with a Rocket Chip based system is to instantiate the Debug Transfer Module (DTM)
and configure it to use a JTAG interface (by default the DTM is setup to use the DMI interface mentioned above).

Creating a DTM+JTAG Config
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

First, a DTM config must be created for the system that you want to create.
This step is similar to the DMI simulation section within the :ref:`Starting the TSI or DMI Simulation` section.
First, you must make a top-level system (``TopWithDTM``) and test-harness (``TestHarnessWithDTM``) that instantiates
and connects the DTM correctly. The configuration is very similar to a DMI-based configuration. The main difference
is the addition of the ``WithJtagDTM`` mixin that configures the instantiated DTM to use the JTAG protocol as the
bringup method.

.. literalinclude:: ../../generators/example/src/main/scala/RocketConfigs.scala
    :language: scala
    :start-after: DOC include start: JtagRocket
    :end-before: DOC include end: JtagRocket

Building a DTM+JTAG Simulator
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

After creating the config, call the ``make`` command like the following to build a simulator for your RTL:

.. code-block:: bash

    cd sims/verilator
    # or
    cd sims/vcs

    make CONFIG=jtagRocketConfig TOP=TopWithDTM MODEL=TestHarnessWithDTM

In this example, this will use the config that you previously specified, as well as set
the other parameters that are needed to satisfy the build system. After that point, you
should have a JTAG enabled simulator that you can attach to using OpenOCD and GDB!

Debugging with JTAG
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Please refer to the following resources on how to debug with JTAG.

* https://github.com/chipsalliance/rocket-chip#-debugging-with-gdb
* https://github.com/riscv/riscv-isa-sim#debugging-with-gdb
