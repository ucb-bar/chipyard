.. _sky130-tutorial:

Sky130 Tutorial
===============
The ``vlsi`` folder of this repository contains an example Hammer flow with the SHA-3 accelerator and a dummy hard macro. This example tutorial uses the built-in Sky130 technology plugin and requires access to the included Cadence and Mentor tool plugin submodules. Cadence is necessary for synthesis & place-and-route, while Mentor is needed for DRC & LVS.

Project Structure
-----------------

This example gives a suggested file structure and build system. The ``vlsi/`` folder will eventually contain the following files and folders:

* ``Makefile``, ``sim.mk``, ``power.mk``

  * Integration of Hammer's build system into Chipyard and abstracts away some Hammer commands.

* ``build``

  * Hammer output directory. Can be changed with the ``OBJ_DIR`` variable.
  * Will contain subdirectories such as ``syn-rundir`` and ``par-rundir`` and the ``inputs.yml`` denoting the top module and input Verilog files.

* ``env.yml``

  * A template file for tool environment configuration. Fill in the install and license server paths for your environment.

* ``example-vlsi-sky130``

  * Entry point to Hammer. Contains example placeholders for hooks.

* ``example-sky130.yml``, ``example-tools.yml``

  * Hammer IR for this tutorial.

* ``example-design.yml``, ``example-nangate45.yml``, ``example-tech.yml``

  * Hammer IR not used for this tutorial but provided as templates.

* ``generated-src``

  * All of the elaborated Chisel and FIRRTL.

* ``hammer``, ``hammer-<vendor>-plugins``, ``hammer-<tech>-plugin``

  * Core, tool, tech repositories.

Prerequisites
-------------

* Python 3.4+
* numpy package
* Genus, Innovus, Voltus, VCS, and Calibre licenses
* Sky130 PDK, install using `these directions  <https://github.com/ucb-bar/hammer/blob/master/src/hammer-vlsi/technology/sky130/README.md>`__

Initial Setup
-------------
In the Chipyard root, run:

.. code-block:: shell

    ./scripts/init-vlsi.sh sky130
    
to pull the Hammer & plugin submodules. Note that for technologies other than ``sky130`` or ``asap7``, the tech submodule must be added in the ``vlsi`` folder first.

Pull the Hammer environment into the shell:

.. code-block:: shell

    cd vlsi
    export HAMMER_HOME=$PWD/hammer
    source $HAMMER_HOME/sourceme.sh

Building the Design
--------------------
To elaborate the ``TinyRocketConfig`` and set up all prerequisites for the build system to push the design and SRAM macros through the flow:

.. code-block:: shell

    make buildfile tech_name=sky130 CONFIG=TinyRocketConfig

The ``CONFIG=TinyRocketConfig`` selects the target generator config in the same manner as the rest of the Chipyard framework. This elaborates a stripped-down Rocket Chip in the interest of minimizing tool runtime.

For the curious, ``make buildfile`` generates a set of Make targets in ``build/hammer.d``. It needs to be re-run if environment variables are changed. It is recommended that you edit these variables directly in the Makefile rather than exporting them to your shell environment.

Running the VLSI Flow
---------------------

example-vlsi-sky130
^^^^^^^^^^^^^^^^^^^
This is the entry script with placeholders for hooks. In the ``ExampleDriver`` class, a list of hooks is passed in the ``get_extra_par_hooks``. Hooks are additional snippets of python and TCL (via ``x.append()``) to extend the Hammer APIs. Hooks can be inserted using the ``make_pre/post/replacement_hook`` methods as shown in this example. Refer to the Hammer documentation on hooks for a detailed description of how these are injected into the VLSI flow.


example-sky130.yml
^^^^^^^^^^^^^^^^^^
This contains the Hammer configuration for this example project. Example clock constraints, power straps definitions, placement constraints, and pin constraints are given. Additional configuration for the extra libraries and tools are at the bottom.

First, set ``technology.sky130.sky130A/sky130_nda/openram_lib`` to the absolute path of the respective directories containing the Sky130 PDK and SRAM files. See the 
`Sky130 Hammer plugin README <https://github.com/ucb-bar/hammer/blob/master/src/hammer-vlsi/technology/sky130/README.md>`__
for details about the PDK setup.


Synthesis
^^^^^^^^^
.. code-block:: shell

    make syn tech_name=sky130 CONFIG=TinyRocketConfig

Post-synthesis logs and collateral are in ``build/syn-rundir``. The raw quality of results data is available at ``build/syn-rundir/reports``, and methods to extract this information for design space exploration are a work in progress.

Place-and-Route
^^^^^^^^^^^^^^^
.. code-block:: shell

    make par tech_name=sky130 CONFIG=TinyRocketConfig

After completion, the final database can be opened in an interactive Innovus session via ``./build/par-rundir/generated-scripts/open_chip``.

Intermediate database are written in ``build/par-rundir`` between each step of the ``par`` action, and can be restored in an interactive Innovus session as desired for debugging purposes. 

Timing reports are found in ``build/par-rundir/timingReports``. They are gzipped text files.

DRC & LVS
^^^^^^^^^
To run DRC & LVS, and view the results in Calibre:

.. code-block:: shell

    make drc tech_name=sky130 CONFIG=TinyRocketConfig
    ./build/chipyard.TestHarness.TinyRocketConfig-ChipTop/drc-rundir/generated-scripts/view_drc
    make lvs tech_name=sky130 CONFIG=TinyRocketConfig
    ./build/chipyard.TestHarness.TinyRocketConfig-ChipTop/lvs-rundir/generated-scripts/view_lvs

Some DRC errors are expected from this PDK, especially with regards to the SRAMs, as explained in the 
`Sky130 Hammer plugin README  <https://github.com/ucb-bar/hammer/blob/master/src/hammer-vlsi/technology/sky130/README.md>`__.
For this reason, the ``example-vlsi-sky130`` script black-boxes the SRAMs for DRC/LVS analysis. 

Simulation
^^^^^^^^^^
Simulation with VCS is supported, and can be run at the RTL- or gate-level (post-synthesis and post-P&R). The simulation infrastructure as included here is intended for running RISC-V binaries on a Chipyard config. For example, for an RTL-level simulation:

.. code-block:: shell

    make sim-rtl CONFIG=TinyRocketConfig BINARY=$RISCV/riscv64-unknown-elf/share/riscv-tests/isa/rv64ui-p-simple

Post-synthesis and post-P&R simulations use the ``sim-syn`` and ``sim-par`` make targets, respectively.

Appending ``-debug`` and ``-debug-timing`` to these make targets will instruct VCS to write a SAIF + VPD (or FSDB if the ``USE_FSDB`` flag is set) and do timing-annotated simulations, respectively. See the ``sim.mk`` file for all available targets.

Power/Rail Analysis
^^^^^^^^^^^^^^^^^^^
Post-P&R power and rail (IR drop) analysis is supported with Voltus:

.. code-block:: shell

    make power-par tech_name=sky130 CONFIG=TinyRocketConfig

If you append the ``BINARY`` variable to the command, it will use the activity file generated from a ``sim-<syn/par>-debug`` run and report dynamic power & IR drop from the toggles encoded in the waveform.

To bypass gate-level simulation, you will need to run the power tool manually (see the generated commands in the generated ``hammer.d`` buildfile). Static and active (vectorless) power & IR drop will be reported.
