.. _sw-rtl-sim-intro:

Software RTL Simulation
===================================

Verilator (Open-Source)
-----------------------

`Verilator <https://www.veripool.org/wiki/verilator>`__ is an open-source LGPL-Licensed simulator maintained by `Veripool <https://www.veripool.org/>`__.
The Chipyard framework can download, build, and execute simulations using Verilator.


Synopsys VCS (License Required)
--------------------------------

`VCS <https://www.synopsys.com/verification/simulation/vcs.html>`__ is a commercial RTL simulator developed by Synopsys.
It requires commercial licenses.
The Chipyard framework can compile and execute simulations using VCS.
VCS simulation will generally compile faster than Verilator simulations.

To run a VCS simulation, make sure that the VCS simulator is on your ``PATH``.


Choice of Simulator
-------------------------------

First, we will start by entering the Verilator or VCS directory:

For an open-source Verilator simulation, enter the ``sims/verilator`` directory

.. code-block:: shell

    # Enter Verilator directory
    cd sims/verilator

For a proprietry VCS simulation, enter the ``sims/vcs`` directory

.. code-block:: shell

    # Enter VCS directory
    cd sims/vcs


.. _sim-default:

Simulating The Default Example
-------------------------------

To compile the example design, run ``make`` in the selected verilator or VCS directory.
This will elaborate the ``RocketConfig`` in the example project.

An executable called ``simulator-example-RocketConfig`` will be produced.
This executable is a simulator that has been compiled based on the design that was built.
You can then use this executable to run any compatible RV64 code.
For instance, to run one of the riscv-tools assembly tests.

.. code-block:: shell

    ./simulator-example-RocketConfig $RISCV/riscv64-unknown-elf/share/riscv-tests/isa/rv64ui-p-simple

.. Note:: in a VCS simulator, the simulator name will be ``simv-example-RocketConfig`` ``instead of simulator-example-RocketConfig``.

Alternatively, we can run a pre-packaged suite of RISC-V assembly or benchmark tests, by adding the make target ``run-asm-tests`` or ``run-bmark-tests``.
For example:

.. code-block:: shell

    make run-asm-tests
    make run-bmark-tests


.. Note:: Before running the pre-packaged suites, you must run the plain ``make`` command, since the elaboration command generates a Makefile fragment that contains the target for the pre-packaged test suites. Otherwise, you will likely encounter a Makefile target error.


.. _sw-sim-custom:

Simulating A Custom Project
-------------------------------

If you later create your own project, you can use environment variables to build an alternate configuration.

In order to construct the simulator with our custom design, we run the following command within the simulator directory:

.. code-block:: shell

    make SBT_PROJECT=... MODEL=... VLOG_MODEL=... MODEL_PACKAGE=... CONFIG=... CONFIG_PACKAGE=... GENERATOR_PACKAGE=... TB=... TOP=...

Each of these make variables correspond to a particular part of the design/codebase and are needed so that the make system can correctly build and make a RTL simulation.

The ``SBT_PROJECT`` is the ``build.sbt`` project that holds all of the source files and that will be run during the RTL build.

The ``MODEL`` and ``VLOG_MODEL`` are the top-level class names of the design. Normally, these are the same, but in some cases these can differ (if the Chisel class differs than what is emitted in the Verilog).

The ``MODEL_PACKAGE`` is the Scala package (in the Scala code that says ``package ...``) that holds the ``MODEL`` class.

The ``CONFIG`` is the name of the class used for the parameter Config while the ``CONFIG_PACKAGE`` is the Scala package it resides in.

The ``GENERATOR_PACKAGE`` is the Scala package that holds the Generator class that elaborates the design.

The ``TB`` is the name of the Verilog wrapper that connects the ``TestHarness`` to VCS/Verilator for simulation.

Finally, the ``TOP`` variable is used to distinguish between the top-level of the design and the ``TestHarness`` in our system.
For example, in the normal case, the ``MODEL`` variable specifies the ``TestHarness`` as the top-level of the design.
However, the true top-level design, the SoC being simulated, is pointed to by the ``TOP`` variable.
This separation allows the infrastructure to separate files based on the harness or the SoC top level.

Common configurations of all these variables are packaged using a ``SUB_PROJECT`` make variable.
Therefore, in order to simulate a simple Rocket-based example system we can use:


.. code-block:: shell

    make SUB_PROJECT=yourproject
    ./simulator-<yourproject>-<yourconfig> ...


All `Make` targets that can be applied to the default example, can also be applied to custom project using the custom environment variables. For example, the following code example will run the RISC-V assembly benchmark suite on the Hwacha subproject:

.. code-block:: shell

    make SUB_PROJECT=hwacha run-asm-tests


Finally, in the ``generated-src/<...>-<package>-<config>/`` directory resides all of the collateral and Verilog source files for the build/simulation.
Specifically, the SoC top-level (``TOP``) Verilog file is denoted with ``*.top.v`` while the ``TestHarness`` file is denoted with ``*.harness.v``.


Generating Waveforms
-----------------------

If you would like to extract waveforms from the simulation, run the command ``make debug`` instead of just ``make``.


For a Verilator simulation, this will generate a vcd file (vcd is a standard waveform representation file format) that can be loaded to any common waveform viewer.
An open-source vcd-capable waveform viewer is `GTKWave <http://gtkwave.sourceforge.net/>`__.


For a VCS simulation, this will generate a vpd file (this is a proprietary waveform representation format used by Synopsys) that can be loaded to vpd-supported waveform viewers.
If you have Synopsys licenses, we recommend using the DVE waveform viewer.

