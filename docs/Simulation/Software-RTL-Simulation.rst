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

.. _sw-sim-help:

Simulating The Default Example
-------------------------------

To compile the example design, run ``make`` in the selected verilator or VCS directory.
This will elaborate the ``RocketConfig`` in the example project.

.. Note:: The elaboration of ``RocketConfig`` requires about 6.5 GB of main memory. Otherwise the process will fail with ``make: *** [firrtl_temp] Error 137`` which is most likely related to limited resources. Other configurations might require even more main memory.

An executable called ``simulator-chipyard-RocketConfig`` will be produced.
This executable is a simulator that has been compiled based on the design that was built.
You can then use this executable to run any compatible RV64 code.
For instance, to run one of the riscv-tools assembly tests.

.. code-block:: shell

    ./simulator-chipyard-RocketConfig $RISCV/riscv64-unknown-elf/share/riscv-tests/isa/rv64ui-p-simple

.. Note:: In a VCS simulator, the simulator name will be ``simv-chipyard-RocketConfig`` instead of ``simulator-chipyard-RocketConfig``.

The makefiles have a ``run-binary`` rule that simplifies running the simulation executable. It adds many of the common command line options for you and redirects the output to a file.

.. code-block:: shell

    make run-binary BINARY=$RISCV/riscv64-unknown-elf/share/riscv-tests/isa/rv64ui-p-simple

Alternatively, we can run a pre-packaged suite of RISC-V assembly or benchmark tests, by adding the make target ``run-asm-tests`` or ``run-bmark-tests``.
For example:

.. code-block:: shell

    make run-asm-tests
    make run-bmark-tests


.. Note:: Before running the pre-packaged suites, you must run the plain ``make`` command, since the elaboration command generates a ``Makefile`` fragment that contains the target for the pre-packaged test suites. Otherwise, you will likely encounter a ``Makefile`` target error.


.. _sw-sim-custom:

Makefile Variables and Commands
-------------------------------
You can get a list of useful Makefile variables and commands available from the Verilator or VCS directories. simply run ``make help``:

.. code-block:: shell

    # Enter Verilator directory
    cd sims/verilator
    make help

    # Enter VCS directory
    cd sims/vcs
    make help

.. _sim-default:

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

The ``CONFIG`` is the name of the class used for the parameter config while the ``CONFIG_PACKAGE`` is the Scala package it resides in.

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


All ``make`` targets that can be applied to the default example, can also be applied to custom project using the custom environment variables. For example, the following code example will run the RISC-V assembly benchmark suite on the Hwacha subproject:

.. code-block:: shell

    make SUB_PROJECT=hwacha run-asm-tests


Finally, in the ``generated-src/<...>-<package>-<config>/`` directory resides all of the collateral while the generated Verilog source files resides in ``generated-src/<...>-<package>-<config>/gen-collateral`` for the build/simulation.
Specifically, for ``CONFIG=RocketConfig`` the SoC top-level (``TOP``) Verilog file is ``ChipTop.sv`` while the (``Model``) file is ``TestHarness.sv``.

Fast Memory Loading
-------------------

The simulator loads the program binary over a simulated serial line. This can be quite slow if there is a lot of static data, so the simulator also allows data to be loaded from a file directly into the DRAM model.
Loadmem files should be ELF files. In the most common use case, this can be the binary.

.. code-block:: shell

    make run-binary BINARY=test.riscv LOADMEM=test.riscv

Usually the ``LOADMEM`` ELF is the same as the ``BINARY`` ELF, so ``LOADMEM=1`` can be used as a shortcut.

.. code-block:: shell

   make run-binary BINARY=test.riscv LOADMEM=1

Generating Waveforms
-----------------------

If you would like to extract waveforms from the simulation, run the command ``make debug`` instead of just ``make``.

A special target that automatically generates the waveform file for a specific test is also available.

.. code-block:: shell

    make run-binary-debug BINARY=test.riscv

For a Verilator simulation, this will generate a vcd file (vcd is a standard waveform representation file format) that can be loaded to any common waveform viewer.
An open-source vcd-capable waveform viewer is `GTKWave <http://gtkwave.sourceforge.net/>`__.

For a VCS simulation, this will generate an fsdb file that can be loaded to fsdb-supported waveform viewers.
If you have Synopsys licenses, we recommend using the Verdi waveform viewer.

Visualizing Chipyard SoCs
--------------------------

During verilog creation, a graphml file is emitted that will allow you to visualize your Chipyard SoC as a diplomacy graph.

To view the graph, first download a viewer such as `yEd <https://www.yworks.com/products/yed/>`__.

The ``*.graphml`` file will be located in ``generated-src/<...>/``. Open the file in the graph viewer.
To get a clearer view of the SoC, switch to "hierarchical" view. For yEd, this would be done by selecting ``layout`` -> ``hierarchical``, and then choosing "Ok" without changing any settings.

.. _sw-sim-verilator-opts:

Additional Verilator Options
-------------------------------

When building the verilator simulator there are some additional options:

.. code-block:: shell

   make VERILATOR_THREADS=8 NUMACTL=1

The ``VERILATOR_THREADS=<num>`` option enables the compiled Verilator simulator to use ``<num>`` parallel threads.
On a multi-socket machine, you will want to make sure all threads are on the same socket by using ``NUMACTL=1`` to enable ``numactl``.
By enabling this, you will use Chipyard's ``numa_prefix`` wrapper, which is a simple wrapper around ``numactl`` that runs your verilated simulator like this: ``$(numa_prefix) ./simulator-<name> <simulator-args>``.
Note that both these flags are mutually exclusive, you can use either independently (though it makes sense to use ``NUMACTL`` just with ``VERILATOR_THREADS=8`` during a Verilator simulation).
