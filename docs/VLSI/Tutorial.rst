.. _tutorial:

ASAP7 Tutorial
==============
The ``vlsi`` folder of this repository contains an example Hammer flow with the SHA-3 accelerator and a dummy hard macro. This example tutorial uses the built-in ASAP7 technology plugin and requires access to the included Cadence and Mentor tool plugin submodules. Cadence is necessary for synthesis & place-and-route, while Mentor is needed for DRC & LVS.

Project Structure
-----------------

This example gives a suggested file structure and build system. The ``vlsi/`` folder will eventually contain the following files and folders:

* Makefile

  * Integration of Hammer's build system into Chipyard and abstracts away some Hammer commands.

* build

  * Hammer output directory. Can be changed with the ``OBJ_DIR`` variable.
  * Will contain subdirectories such as ``syn-rundir`` and ``par-rundir`` and the ``inputs.yml`` denoting the top module and input Verilog files.

* env.yml

  * A template file for tool environment configuration. Fill in the install and license server paths for your environment.

* example-vlsi

  * Entry point to Hammer. Contains example placeholders for hooks.

* example.v

  * Verilog wrapper around the accelerator and dummy hard macro.

* example-asap7.yml

  * Hammer IR for this tutorial.

* extra_libraries

  * Contains collateral for the dummy hard macro.

* generated-src

  * All of the elaborated Chisel and FIRRTL.

* hammer, hammer-<vendor>-plugins, hammer-<tech>-plugin

  * Core, tool, tech repositories.

Prerequisites
-------------

* Python 3.4+
* numpy and gdspy packages. gdspy must be version 1.4.
* Genus, Innovus, and Calibre licenses
* For ASAP7 specifically:

  * Download the `ASAP7 PDK v1p5 <http://asap.asu.edu/asap/>`__ tarball to a directory of choice but do not extract it. The tech plugin is configured to extract the PDK into a cache directory for you.
  * If you have additional ASAP7 hard macros, their LEF & GDS need to be 4x upscaled @ 4000 DBU precision. They may live outside ``extra_libraries`` at your discretion.
  * Innovus version must be >= 15.2 or <= 18.1 (ISRs excluded).

Initial Setup
-------------
In the Chipyard root, run:

.. code-block:: shell

    ./scripts/init-vlsi.sh asap7
    
to pull the Hammer & plugin submodules. Note that for technologies other than ``asap7``, the tech submodule must be added in the ``vlsi`` folder first.

Pull the Hammer environment into the shell:

.. code-block:: shell

    cd vlsi
    export HAMMER_HOME=$PWD/hammer
    source $HAMMER_HOME/sourceme.sh

Building the Design
--------------------
To elaborate the ``Sha3RocketConfig`` (Rocket Chip w/ the accelerator) and set up all prerequisites for the build system to push just the accelerator + hard macro through the flow:

.. code-block:: shell

    make buildfile MACROCOMPILER_MODE='--mode synflops' CONFIG=Sha3RocketConfig VLSI_TOP=Sha3AccelwBB

The ``MACROCOMPILER_MODE='--mode synflops'`` is needed because the ASAP7 process does not yet have a memory compiler, so flip-flop arrays are used instead. This will dramatically increase the synthesis runtime if your design has a lot of memory state (e.g. large caches). This change is automatically inferred by the makefile but is included here for completeness.

The ``CONFIG=Sha3RocketConfig`` selects the target generator config in the same manner as the rest of the Chipyard framework. This elaborates a Rocket Chip with the Sha3Accel module.

The ``VLSI_TOP=Sha3AccelwBB`` indicates that we are only interested in physical design of the accelerator block. If this variable is not set, the entire SoC will be pushed through physical design. Note that you should not set the ``TOP`` variable because it is used during Chisel elaboration.

For the curious, ``make buildfile`` generates a set of Make targets in ``build/hammer.d``. It needs to be re-run if environment variables are changed. It is recommended that you edit these variables directly in the Makefile rather than exporting them to your shell environment.

Running the VLSI Flow
---------------------

example-vlsi
^^^^^^^^^^^^
This is the entry script with placeholders for hooks. In the ``ExampleDriver`` class, a list of hooks is passed in the ``get_extra_par_hooks``. Hooks are additional snippets of python and TCL (via ``x.append()``) to extend the Hammer APIs. Hooks can be inserted using the ``make_pre/post/replacement_hook`` methods as shown in this example. Refer to the Hammer documentation on hooks for a detailed description of how these are injected into the VLSI flow.

The ``scale_final_gds`` hook is a particularly powerful hook. It dumps a Python script provided by the ASAP7 tech plugin, an executes it within the Innovus TCL interpreter, and should be inserted after ``write_design``. This hook is necessary because the ASAP7 PDK does place-and-route using 4x upscaled LEFs for Innovus licensing reasons, thereby requiring the cells created in the post-P&R GDS to be scaled down by a factor of 4.

example.yml
^^^^^^^^^^^
This contains the Hammer configuration for this example project. Example clock constraints, power straps definitions, placement constraints, and pin constraints are given. Additional configuration for the extra libraries and tools are at the bottom.

First, set ``technology.asap7.tarball_dir`` to the absolute path of where the downloaded the ASAP7 PDK tarball lives.

Synthesis
^^^^^^^^^
.. code-block:: shell

    make syn

Post-synthesis logs and collateral are in ``build/syn-rundir``. The raw QoR data is available at ``build/syn-rundir/reports``, and methods to extract this information for design space exploration are a WIP.

Place-and-Route
^^^^^^^^^^^^^^^
.. code-block:: shell

    make par

After completion, the final database can be opened in an interactive Innovus session via ``./build/par-rundir/generated-scripts/open_chip``.

Intermediate database are written in ``build/par-rundir`` between each step of the ``par`` action, and can be restored in an interactive Innovus session as desired for debugging purposes. 

Timing reports are found in ``build/par-rundir/timingReports``. They are gzipped text files.

`gdspy` can be used to `view the final layout <https://gdspy.readthedocs.io/en/stable/reference.html?highlight=scale#layoutviewer>`__, but it is somewhat crude and slow (wait a few minutes for it to load):

.. code-block:: shell

    python3 view_gds.py build/par-rundir/Sha3AccelwBB.gds

By default, this script only shows the M2 thru M4 routing. Layers can be toggled in the layout viewer's side pane and ``view_gds.py`` has a mapping of layer numbers to layer names.

DRC & LVS
^^^^^^^^^
To run DRC & LVS, and view the results in Calibre:

.. code-block:: shell

    make drc
    ./build/drc-rundir/generated-scripts/view-drc
    make lvs
    ./build/lvs-rundir/generated-scripts/view-lvs

Some DRC errors are expected from this PDK, as explained in the `ASAP7 plugin readme <https://github.com/ucb-bar/hammer/tree/master/src/hammer-vlsi/technology/asap7>`__.
