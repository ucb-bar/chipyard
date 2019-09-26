.. _tutorial:

ASAP7 Tutorial
==============
The ``vlsi`` folder of this repository contains an example HAMMER flow with the SHA-3 accelerator and a dummy hard macro in the ASAP7 PDK. It is intended for use with the Cadence and Mentor tool plugins.

Project Structure
-----------------

This example gives a suggested file structure and build system. The ``vlsi/`` folder will eventually contain the following files and folders:

* Makefile

  * Integration of Hammer's build system into Chipyard and abstracts away some Hammer commands.

* build

  * Hammer output directory. Can be changed with the ``OBJ_DIR`` variable.
  * Will contain subdirectories such as ``syn-rundir`` and ``par-rundir`` and the ``inputs.yml`` denoting the top module and input Verilog files.

* bwrc-env.yml

  * An example of tool environment configuration for BWRC affiliates. Replace as necessary for your environment.

* example-vlsi

  * Entry point to Hammer. Contains example placeholders for hooks.

* example.v

  * Verilog wrapper around the accelerator and dummy hard macro.

* example.yml

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
* numpy and gdspy packages
* Genus, Innovus, and Calibre licenses
* For ASAP7 specifically:

  * Download the `ASAP7 PDK <http://asap.asu.edu/asap/>`__ tarball and do not extract it
  * If you have additional ASAP7 hard macros, their LEF & GDS need to be 4x upscaled @ 4000 DBU precision

Initial Setup
-------------
In the Chipyard root, run:

.. code-block:: shell

    ``./scripts/init-vlsi.sh asap7`` 
    
to pull the HAMMER & plugin submodules. Note that for technologies other than ``asap7``, the tech submodule must be added in the ``vlsi`` folder first.

Pull the Hammer environment into the shell:

.. code-block:: shell

    cd vlsi
    export HAMMER_HOME=$PWD/hammer
    source $HAMMER_HOME/sourceme.sh

Building the Design
-------------------
To elaborate the Sha3RocketConfig (Rocketchip w/ the accelerator) and set up all prerequisites for the build system to push just the accelerator + hard macro through the flow:

.. code-block:: shell

    export MACROCOMPILER_MODE='--mode synflops'
    export CONFIG=Sha3RocketConfig
    export VLSI_TOP=Sha3AccelwBB
    make buildfile

Note that because the ASAP7 process does not yet have a memory compiler, flip-flop arrays are used instead.

For the curious, Hammer generates a set of Make targets in ``build/hammer.d``. ``make buildfile`` needs to be re-run if Make variables are changed.

Running the VLSI Flow
---------------------

example-vlsi
^^^^^^^^^^^^
This is the entry script with placeholders for hooks. In the ``ExampleDriver`` class, a list of hooks is passed in the ``get_extra_par_hooks``. Hooks are additional snippets of python and TCL (via ``x.append()``) to extend the Hammer APIs. Hooks can be inserted using the ``make_pre/post/replacement_hook`` methods.

example.yml
^^^^^^^^^^^
This contains the Hammer configuration for this example project. Example clock constraints, power straps definitions, placement constraints, and pin constraints are given. Additional configuration for the extra libraries and tools are at the bottom.

First, set ``technology.asap7.tarball_dir`` to where you downloaded the ASAP7 PDK.

Synthesis
^^^^^^^^^
.. code-block:: shell

    ``make syn``

Post-synthesis logs and collateral are in ``build/syn-rundir``. The Raw QoR data is available at ``build/syn-rundir/reports``, and methods to extract this information for design space exploration are a WIP.

Place-and-Route
^^^^^^^^^^^^^^^
.. code-block:: shell

    ``make par``

After completion, the final database can be opened in an interactive Innovus session via ``./build/par-rundir/generated-scripts/open_chip``.

Intermediate database are written in ``build/par-rundir`` between each step of the ``par`` action, and can be restored in an interactive Innovus session as desired for debugging purposes. 

Timing reports are found in ``build/par-rundir/timingReports``. They are gzipped text files.

DRC & LVS
^^^^^^^^^
To run DRC & LVS, and view the results in Calibre:

.. code-block:: shell

    make drc
    ./build/drc-rundir/generated-scripts/view-drc
    make lvs
    ./build/lvs-rundir/generated-scripts/view-lvs

Some DRC errors are expected from this PDK, as explained in the `ASAP7 plugin readme <https://github.com/ucb-bar/hammer/tree/master/src/hammer-vlsi/technology/asap7>`__.
