.. _sky130-commercial-tutorial:

Sky130 Tutorial
===============
The ``vlsi`` folder of this repository contains an example Hammer flow with the TinyRocketConfig from Chipyard. This example tutorial uses the built-in Sky130 technology plugin and requires access to the included Cadence and Mentor tool plugin submodules. Cadence is necessary for synthesis & place-and-route, while Mentor is needed for DRC & LVS.

Project Structure
-----------------

This example gives a suggested file structure and build system. The ``vlsi/`` folder will eventually contain the following files and folders:

* ``Makefile``, ``sim.mk``, ``power.mk``

  * Integration of Hammer's build system into Chipyard and abstracts away some Hammer commands.

* ``build``

  * Hammer output directory. Can be changed with the ``OBJ_DIR`` variable.
  * Will contain subdirectories such as ``syn-rundir`` and ``par-rundir`` and the ``inputs.yml`` denoting the top module and input Verilog files.

* ``env.yml``

  * A template file for tool environment configuration. Fill in the install and license server paths for your environment. For SLICE and BWRC affiliates, example environment configs are found `here <https://github.com/ucb-bar/hammer/tree/master/e2e/env>`__.

* ``example-vlsi-sky130``

  * Entry point to Hammer. Contains example placeholders for hooks.

* ``example-sky130.yml``, ``example-tools.yml``, ``example-designs/sky130-commercial.yml``

  * Hammer IR for this tutorial. For SLICE and BWRC affiliates, an example ASAP7 config is found `here <https://github.com/ucb-bar/hammer/tree/master/e2e/pdks>`__.

* ``example-design.yml``, ``example-asap7.yml``, ``example-tech.yml``

  * Hammer IR not used for this tutorial but provided as templates.

* ``generated-src``

  * All of the elaborated Chisel and FIRRTL.

* ``hammer-<vendor>-plugins``

  * Tool plugin repositories.

Prerequisites
-------------

* Python 3.9+
* Genus, Innovus, Voltus, VCS, and Calibre licenses
* Sky130 PDK, install using `these directions  <https://github.com/ucb-bar/hammer/blob/master/hammer/technology/sky130>`__

Initial Setup
-------------
In the Chipyard root, ensure that you have the Chipyard conda environment activated. Then, run:

.. code-block:: shell

    ./scripts/init-vlsi.sh sky130

to pull and install the plugin submodules. Note that for technologies other than ``sky130`` or ``asap7``, the tech submodule must be added in the ``vlsi`` folder first.

Building the Design
--------------------
To elaborate the ``TinyRocketConfig`` and set up all prerequisites for the build system to push the design and SRAM macros through the flow:

.. code-block:: shell

    make buildfile tutorial=sky130-commercial

The command ``make buildfile`` generates a set of Make targets in ``build/hammer.d``.
It needs to be re-run if environment variables are changed.
It is recommended that you edit these variables directly in the Makefile rather than exporting them to your shell environment.

For the purpose of brevity, in this tutorial we will set the Make variable ``tutorial=sky130-commercial``,
which will cause additional variables to be set in ``tutorial.mk``, a few of which are summarized as follows:

* ``CONFIG=TinyRocketConfig`` selects the target generator config in the same manner as the rest of the Chipyard framework. This elaborates a stripped-down Rocket Chip in the interest of minimizing tool runtime.
* ``tech_name=sky130`` sets a few more necessary paths in the ``Makefile``, such as the appropriate Hammer plugin
* ``TOOLS_CONF`` and ``TECH_CONF`` select the approproate YAML configuration files, ``example-tools.yml`` and ``example-sky130.yml``, which are described below
* ``DESIGN_CONF`` and ``EXTRA_CONFS`` allow for additonal design-specific overrides of the Hammer IR in ``example-sky130.yml``
* ``VLSI_OBJ_DIR=build-sky130-commercial`` gives the build directory a unique name to allow running multiple flows in the same repo. Note that for the rest of the tutorial we will still refer to this directory in file paths as ``build``, again for brevity.
* ``VLSI_TOP`` is by default ``ChipTop``, which is the name of the top-level Verilog module generated in the Chipyard SoC configs. By instead setting ``VLSI_TOP=Rocket``, we can use the Rocket core as the top-level module for the VLSI flow, which consists only of a single RISC-V core (and no caches, peripherals, buses, etc). This is useful to run through this tutorial quickly, and does not rely on any SRAMs.

Running the VLSI Flow
---------------------

example-vlsi-sky130
^^^^^^^^^^^^^^^^^^^
This is the entry script with placeholders for hooks. In the ``ExampleDriver`` class, a list of hooks is passed in the ``get_extra_par_hooks``. Hooks are additional snippets of python and TCL (via ``x.append()``) to extend the Hammer APIs. Hooks can be inserted using the ``make_pre/post/replacement_hook`` methods as shown in this example. Refer to the Hammer documentation on hooks for a detailed description of how these are injected into the VLSI flow.


example-sky130.yml
^^^^^^^^^^^^^^^^^^
This contains the Hammer configuration for this example project. Example clock constraints, power straps definitions, placement constraints, and pin constraints are given. Additional configuration for the extra libraries and tools are at the bottom.

First, set ``technology.sky130.sky130A/sky130_nda/openram_lib`` to the absolute path of the respective directories containing the Sky130 PDK and SRAM files. See the
`Sky130 Hammer plugin README <https://github.com/ucb-bar/hammer/blob/master/hammer/technology/sky130>`__
for details about the PDK setup.

example-tools.yml
^^^^^^^^^^^^^^^^^
This contains the Hammer configuration for a commercial tool flow.
It selects tools for synthesis (Cadence Genus), place and route (Cadence Innovus), DRC and LVS (Mentor Calibre).

Synthesis
^^^^^^^^^
.. code-block:: shell

    make syn tutorial=sky130-commercial

Post-synthesis logs and collateral are in ``build/syn-rundir``. The raw quality of results data is available at ``build/syn-rundir/reports``, and methods to extract this information for design space exploration are a work in progress.

Place-and-Route
^^^^^^^^^^^^^^^
.. code-block:: shell

    make par tutorial=sky130-commercial

After completion, the final database can be opened in an interactive Innovus session via ``./build/par-rundir/generated-scripts/open_chip``.

Intermediate database are written in ``build/par-rundir`` between each step of the ``par`` action, and can be restored in an interactive Innovus session as desired for debugging purposes.

Timing reports are found in ``build/par-rundir/timingReports``. They are gzipped text files.

DRC & LVS
^^^^^^^^^
To run DRC & LVS, and view the results in Calibre:

.. code-block:: shell

    make drc tutorial=sky130-commercial
    ./build/chipyard.TestHarness.TinyRocketConfig-ChipTop/drc-rundir/generated-scripts/view_drc
    make lvs tutorial=sky130-commercial
    ./build/chipyard.TestHarness.TinyRocketConfig-ChipTop/lvs-rundir/generated-scripts/view_lvs

Some DRC errors are expected from this PDK, especially with regards to the SRAMs, as explained in the
`Sky130 Hammer plugin README  <https://github.com/ucb-bar/hammer/blob/master/hammer/technology/sky130>`__.
For this reason, the ``example-vlsi-sky130`` script black-boxes the SRAMs for DRC/LVS analysis.

Simulation
^^^^^^^^^^
Simulation with VCS is supported, and can be run at the RTL- or gate-level (post-synthesis and post-P&R). The simulation infrastructure as included here is intended for running RISC-V binaries on a Chipyard config. For example, for an RTL-level simulation:

.. code-block:: shell

    make sim-rtl tutorial=sky130-commercial BINARY=$RISCV/riscv64-unknown-elf/share/riscv-tests/isa/rv64ui-p-simple

Post-synthesis and post-P&R simulations use the ``sim-syn`` and ``sim-par`` make targets, respectively.

Appending ``-debug`` and ``-debug-timing`` to these make targets will instruct VCS to write a SAIF + FSDB (or VPD if the ``USE_VPD`` flag is set) and do timing-annotated simulations, respectively. See the ``sim.mk`` file for all available targets.

Power/Rail Analysis
^^^^^^^^^^^^^^^^^^^
Post-P&R power and rail (IR drop) analysis is supported with Voltus:

.. code-block:: shell

    make power-par tutorial=sky130-commercial

If you append the ``BINARY`` variable to the command, it will use the activity file generated from a ``sim-<syn/par>-debug`` run and report dynamic power & IR drop from the toggles encoded in the waveform.

To bypass gate-level simulation, you will need to run the power tool manually (see the generated commands in the generated ``hammer.d`` buildfile). Static and active (vectorless) power & IR drop will be reported.
