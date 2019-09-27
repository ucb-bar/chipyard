.. _chip-communication:

Communicating with the Chip/DUT
===============================

What good is a chip/DUT if it can't communicate with the outside world? There are two types of designs that can be
made: tethered or standalone designs. A tethered design is where a host must interact with the DUT (a target) to bringup the design.
A standalone design is a design that can bringup itself (has its own bootrom, loads programs itself, etc). An example of a tethered design
is a Chipyard simulation where the host computer loads the test program into the designs memory. An example of a standalone design is
a design where a program can be loaded from an SDCard by default.

Chipyard designs communicate to the outside world in one of two ways that mainly correspond to a tethered design:

* using the Tethered Serial Interface (TSI) or the Debug Module Interface (DMI) with the Front-End Server (FESVR) to communicate with the design
* using the JTAG interface with OpenOCD and GDB to communicate with the design

Using the Tethered Serial Interface (TSI) or the Debug Module Interface (DMI)
-----------------------------------------------------------------------------

If you are using TSI or the DMI to communicate with the target (DUT/chip), you are using
the Front-End Server (FESVR) to facilitate communication between the host machine and the target.

Primer on the Front-End Server (FESVR)
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

FESVR is a C++ library that manages communication
between a host machine and a RISC-V target. For debugging, it provides a simple API to reset,
send messages, and load/run programs on a DUT but it also provides peripheral device emulation.
It can be added used simulators (VCS, Verilator, FireSim) as well as in a bringup sequence
for a taped out chip.

Specifically, FESVR uses the Host Target Interface (HTIF), a communication bus for the hardware,
to speak with the target. HTIF is a non-standard Berkeley extension that uses a FIFO non-blocking
interface to communicate with the target.

Using the Tethered Serial Interface (TSI)
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

By default, Chipyard uses the Tethered Serial Interface (TSI) to communicate with the target.
TSI is the implementation of the HTIF that is used to send commands to the
RISC-V target. These TSI commands are simple R/W commands
that are able to probe the DUT's memory space. In simulation, these TSI commands connect
to a ``SimSerial`` (located in the ``generators/testchipip`` project) simulation C++
class that is added to simulation. This ``SimSerial`` device sends the TSI command to
the DUT which contains a ``SerialAdapter`` (located in the ``generators/testchipip``
project) that converts the TSI commands to TileLink requests. In simulation, FESVR
resets the DUT, and writes into memory the test program. This is currently the fastest
mechanism to simulate the DUT.

In the case of a chip tapeout bringup, TSI commands can be sent over a modified communication
medium to communicate with the chip. For example, some Berkeley tapeouts have a FPGA
with a RISC-V soft-core that runs FESVR. The FESVR on the soft-core sends TSI commands
to a TSI to TileLink converter living on the FPGA (i.e. ``SerialAdapter``). Then this converter
sends the converted TileLink commands over a serial link to the chip.

Using the Debug Module Interface (DMI)
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Another option to bringup the target is to use the Debug Module Interface (DMI) provided by a Debug Transfer Module (DTM) existing within the target.
Similar to TSI, DMI is an implementation of HTIF.
The DTM is given in the `RISC-V Debug Specification <https://riscv.org/specifications/debug-specification/>`__ and is responsible for managing communication between
the target and whatever lives on the other side of the DMI (in this case FESVR). This is added by default
to the Rocket Chip ``Subsystem`` by having the ``HasPeripheryDebug`` and ``HasPeripheryDebugModuleImp`` mixins.
Chipyard disables the DTM by default so that it can use the TSI interface.
This is because the DTM executes a small loop of code to write the test binary byte-wise into memory
while the default ``SimSerial``/``SerialAdapter``/TSI interface directly writes to memory.

Starting the TSI or DMI Simulation
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Since Chipyard uses TSI by default, you can run a TSI based simulation by running any of the default
configurations. For example:

.. code-block:: bash

   cd sims/verilator
   # or
   cd sims/vcs

   make CONFIG=LargeBoomConfig run-asm-tests

If you would like to build and simulate a DMI system with a Chipyard configuration, the you must create a
top-level system with the DTM as well as a config to use that top-level system.

.. literalinclude:: ../../generators/example/src/main/scala/RocketConfigs.scala
    :language: scala
    :start-after: DOC include start: DmiRocket
    :end-before: DOC include end: DmiRocket

In this example, the ``WithDTMTop`` mixin specifies that the top-level SoC will instantiate a DTM.
The rest of the mixins specify the rest of the system (cores, accelerators, etc).

.. code-block:: bash

    cd sims/verilator
    # or
    cd sims/vcs

    make CONFIG=dmiRocketConfig TOP=TopWithDTM MODEL=TestHarnessWithDTM run-asm-tests

Using the JTAG Interface
------------------------

The main way to use JTAG with a Rocket Chip based system is to instantiate the Debug Transfer Module (DTM)
and configure it to use a JTAG interface (by default the DTM is setup to use the DMI interface mentioned above).
However, if you want to use JTAG, you must do the following steps to setup a DTM+JTAG enabled system.

Creating a DTM+JTAG Config
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

First, a DTM config must be created for the system that you want to create.
This involves specifying the SoC top-level to add a DTM as well as configuring that DTM to use JTAG.

.. literalinclude:: ../../generators/example/src/main/scala/RocketConfigs.scala
    :language: scala
    :start-after: DOC include start: JtagRocket
    :end-before: DOC include end: JtagRocket

In this example, the ``WithDTMTop`` mixin specifies that the top-level SoC will instantiate a DTM.
The ``WithJtagDTM`` will configure that instantiated DTM to use JTAG as the bringup method (note:
this can be removed if you want a DMI-only bringup).
The rest of the mixins specify the rest of the system (cores, accelerators, etc).

Building a DTM+JTAG Simulator
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

After creating the config, call the ``make`` command like the following:

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
