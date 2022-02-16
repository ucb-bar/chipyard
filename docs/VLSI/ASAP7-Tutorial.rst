.. _tutorial:

ASAP7 Tutorial
==============
The ``vlsi`` folder of this repository contains an example Hammer flow with the SHA-3 accelerator and a dummy hard macro. This example tutorial uses the built-in ASAP7 technology plugin and requires access to the included Cadence and Mentor tool plugin submodules. Cadence is necessary for synthesis & place-and-route, while Mentor is needed for DRC & LVS.

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

* ``example-vlsi``

  * Entry point to Hammer. Contains example placeholders for hooks.

* ``example-asap7.yml``, ``example-tools.yml``

  * Hammer IR for this tutorial.

* ``example-design.yml``, ``example-nangate45.yml``, ``example-tech.yml``

  * Hammer IR not used for this tutorial but provided as templates.

* ``generated-src``

  * All of the elaborated Chisel and FIRRTL.

* ``hammer``, ``hammer-<vendor>-plugins``, ``hammer-<tech>-plugin``

  * Core, tool, tech repositories.

* ``view_gds.py``

  * A convenience script to view a layout using gdstk or gdspy. Only use this for small layouts (i.e. smaller than the TinyRocketConfig example) since the gdstk-produced SVG will be too big and gdspy's GUI is very slow for large layouts!

Prerequisites
-------------

* Python 3.4+
* numpy and `gdstk <https://github.com/heitzmann/gdstk>`__ or `gdspy <https://github.com/heitzmann/gdspy>`__  packages. Note: gdspy must be version 1.4.
* Genus, Innovus, Voltus, VCS, and Calibre licenses
* For ASAP7 specifically (`README <https://github.com/ucb-bar/hammer/tree/master/src/hammer-vlsi/technology/asap7>`__ for more details):

  * First, download the `ASAP7 v1p7 PDK <https://github.com/The-OpenROAD-Project/asap7>`__ (we recommend shallow-cloning or downloading an archive of the repository). Then, download the `encrypted Calibre decks tarball <http://asap.asu.edu/asap/>`__ tarball to a directory of choice (e.g. the root directory of the PDK) but do not extract it like the instructions say. The tech plugin is configured to extract the tarball into a cache directory for you. 
  * If you have additional ASAP7 hard macros, their LEF & GDS need to be 4x upscaled @ 4000 DBU precision. 

Initial Setup
-------------
In the Chipyard root, run:

.. code-block:: shell

    ./scripts/init-vlsi.sh asap7
    
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

    make buildfile CONFIG=TinyRocketConfig

The ``CONFIG=TinyRocketConfig`` selects the target generator config in the same manner as the rest of the Chipyard framework. This elaborates a stripped-down Rocket Chip in the interest of minimizing tool runtime.

For the curious, ``make buildfile`` generates a set of Make targets in ``build/hammer.d``. It needs to be re-run if environment variables are changed. It is recommended that you edit these variables directly in the Makefile rather than exporting them to your shell environment.

Running the VLSI Flow
---------------------

example-vlsi
^^^^^^^^^^^^
This is the entry script with placeholders for hooks. In the ``ExampleDriver`` class, a list of hooks is passed in the ``get_extra_par_hooks``. Hooks are additional snippets of python and TCL (via ``x.append()``) to extend the Hammer APIs. Hooks can be inserted using the ``make_pre/post/replacement_hook`` methods as shown in this example. Refer to the Hammer documentation on hooks for a detailed description of how these are injected into the VLSI flow.

example-asap7.yml
^^^^^^^^^^^^^^^^^
This contains the Hammer configuration for this example project. Example clock constraints, power straps definitions, placement constraints, and pin constraints are given. Additional configuration for the extra libraries and tools are at the bottom.

First, set ``technology.asap7.tarball_dir`` to the absolute path to the directory where the downloaded the ASAP7 Calibre deck tarball lives. If it is not in the PDK's root directory, then also set ``technology.asap7.pdk_install_dir`` and ``technology.asap7.stdcell_install_dir``.

Synthesis
^^^^^^^^^
.. code-block:: shell

    make syn CONFIG=TinyRocketConfig

Post-synthesis logs and collateral are in ``build/syn-rundir``. The raw quality of results data is available at ``build/syn-rundir/reports``, and methods to extract this information for design space exploration are a work in progress.

Place-and-Route
^^^^^^^^^^^^^^^
.. code-block:: shell

    make par CONFIG=TinyRocketConfig

After completion, the final database can be opened in an interactive Innovus session via ``./build/par-rundir/generated-scripts/open_chip``.

Intermediate database are written in ``build/par-rundir`` between each step of the ``par`` action, and can be restored in an interactive Innovus session as desired for debugging purposes. 

Timing reports are found in ``build/par-rundir/timingReports``. They are gzipped text files.

`gdspy` can be used to `view the final layout <https://gdspy.readthedocs.io/en/stable/reference.html?highlight=scale#layoutviewer>`__, but it is somewhat crude and slow (wait a few minutes for it to load):

.. code-block:: shell

    ./view_gds.py build/chipyard.TestHarness.TinyRocketConfig/par-rundir/ChipTop.gds

By default, this script only shows the M2 thru M4 routing. Layers can be toggled in the layout viewer's side pane and ``view_gds.py`` has a mapping of layer numbers to layer names.

DRC & LVS
^^^^^^^^^
To run DRC & LVS, and view the results in Calibre:

.. code-block:: shell

    make drc CONFIG=TinyRocketConfig
    ./build/drc-rundir/generated-scripts/view-drc
    make lvs CONFIG=TinyRocketConfig
    ./build/lvs-rundir/generated-scripts/view-lvs

Some DRC errors are expected from this PDK, as explained in the `ASAP7 plugin readme <https://github.com/ucb-bar/hammer/tree/master/src/hammer-vlsi/technology/asap7>`__.
Furthermore, the dummy SRAMs that are provided in this tutorial and PDK do not have any geometry inside, so will certainly cause DRC errors.

Simulation
^^^^^^^^^^
Simulation with VCS is supported, and can be run at the RTL- or gate-level (post-synthesis and post-P&R). The simulation infrastructure as included here is intended for running RISC-V binaries on a Chipyard config. For example, for an RTL-level simulation:

.. code-block:: shell

    make sim-rtl CONFIG=TinyRocketConfig BINARY=$RISCV/riscv64-unknown-elf/share/riscv-tests/isa/rv32ui-p-simple

Post-synthesis and post-P&R simulations use the ``sim-syn`` and ``sim-par`` make targets, respectively.

Appending ``-debug`` and ``-debug-timing`` to these make targets will instruct VCS to write a SAIF + VPD (or FSDB if the ``USE_FSDB`` flag is set) and do timing-annotated simulations, respectively. See the ``sim.mk`` file for all available targets.

Power/Rail Analysis
^^^^^^^^^^^^^^^^^^^
Post-P&R power and rail (IR drop) analysis is supported with Voltus:

.. code-block:: shell

    make power-par CONFIG=TinyRocketConfig

If you append the ``BINARY`` variable to the command, it will use the activity file generated from a ``sim-<syn/par>-debug`` run and report dynamic power & IR drop from the toggles encoded in the waveform.

To bypass gate-level simulation, you will need to run the power tool manually (see the generated commands in the generated ``hammer.d`` buildfile). Static and active (vectorless) power & IR drop will be reported.
