Running A Simulation
========================================================

Chipyard provides support and integration for multiple simulation flows, for various user levels and requirements.
In the majority of cases during a digital design development process, simple software RTL simulation is needed.
When more advanced full-system evaluation is required, with long running workloads, FPGA-accelerated simulation will then become a preferable solution.

Software RTL Simulation
------------------------
The Chipyard framework provides wrappers for two common software RTL simulators:
the open-source Verilator simulator and the proprietary VCS simulator.
For more information on either of these simulators, please refer to :ref:`Verilator (Open-Source)` or :ref:`Synopsys VCS (License Required)`.
The following instructions assume at least one of these simulators is installed.

Verilator/VCS Flows
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
Verilator is an open-source RTL simulator.
We run Verilator simulations from within the ``sims/verilator`` directory which provides the necessary ``Makefile`` to both install and run Verilator simulations.
On the other hand, VCS is a proprietary RTL simulator.
We run VCS simulations from within the ``sims/vcs`` directory.
Assuming VCS is already installed on the machine running simulations (and is found on our ``PATH``), then this guide is the same for both Verilator and VCS.

First, we will start by entering the Verilator or VCS directory:

.. code-block:: shell

    # Enter Verilator directory
    cd sims/verilator

    # OR

    # Enter VCS directory
    cd sims/vcs

In order to construct the simulator with our custom design, we run the following command within the simulator directory:

.. code-block:: shell

    make SBT_PROJECT=... MODEL=... VLOG_MODEL=... MODEL_PACKAGE=... CONFIG=... CONFIG_PACKAGE=... GENERATOR_PACKAGE=... TB=... TOP=...

Each of these make variables correspond to a particular part of the design/codebase and are needed so that the make system can correctly build and make a RTL simulation.

The ``SBT_PROJECT`` is the ``build.sbt`` project that holds all of the source files and that will be run during the RTL build.

The ``MODEL`` and ``VLOG_MODEL`` are the top-level class names of the design.

Normally, these are the same, but in some cases these can differ (if the Chisel class differs than what is emitted in the Verilog).

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

    make SUB_PROJECT=example

Once the simulator has been constructed, we would like to run RISC-V programs on it.
In the simulation directory, we will find an executable file called ``<...>-<package>-<config>``.
We run this executable with our target RISC-V program as a command line argument in one of two ways.
One, we can directly call the simulator binary or use make to run the binary for us with extra simulation flags.
For example:

.. code-block:: shell

    # directly calling the simulation binary
    ./<...>-<package>-<config> my_program_binary

    # using make to do it
    make SUB_PROJECT=example BINARY=my_program_binary run-binary

Alternatively, we can run a pre-packaged suite of RISC-V assembly or benchmark tests, by adding the make target ``run-asm-tests`` or ``run-bmark-tests``.
For example:

.. code-block:: shell

    make SUB_PROJECT=example run-asm-tests
    make SUB_PROJECT=example run-bmark-tests

Note: You need to specify all the make variables once again to match what the build gave to run the assembly tests or the benchmarks or the binaries if you are using the make option.

Finally, in the ``generated-src/<...>-<package>-<config>/`` directory resides all of the collateral and Verilog source files for the build/simulation.
Specifically, the SoC top-level (``TOP``) Verilog file is denoted with ``*.top.v`` while the ``TestHarness`` file is denoted with ``*.harness.v``.

FPGA Accelerated Simulation
---------------------------
FireSim enables simulations at 1000x-100000x the speed of standard software simulation.
This is enabled using FPGA-acceleration on F1 instances of the AWS (Amazon Web Services) public cloud.
Therefore FireSim simulation requires to be set-up on the AWS public cloud rather than on our local development machine.

To run an FPGA-accelerated simulation using FireSim, a we need to clone the Chipyard repository (or our fork of the Chipyard repository) to an AWS EC2, and follow the setup instructions specified in the FireSim Initial Setup documentation page.

After setting up the FireSim environment, we now need to generate a FireSim simulation around our selected digital design.
We will work from within the ``sims/firesim`` directory.

TODO: Continue from here

