Core HAMMER
================================

`HAMMER <https://github.com/ucb-bar/hammer>`__ is a physical design generator that wraps around vendor specific technologies and tools to provide a single API to create ASICs.
HAMMER allows for reusability in ASIC design while still providing the designers leeway to make their own modifications.

For more information, read the `HAMMER paper <https://people.eecs.berkeley.edu/~edwardw/pubs/hammer-woset-2018.pdf>`__ and see the `GitHub repository <https://github.com/ucb-bar/hammer>`__ and associated documentation.

Hammer implements a VLSI flow using the following high-level constructs:

Actions
-------

Actions are the top-level tasks Hammer is capable of executing (e.g. synthesis, place-and-route, etc.)

Steps
-------

Steps are the sub-components of actions that individually addressable in Hammer (e.g. placement in the place-and-route action).

Hooks
-------

Hooks are modifications to steps or actions that are programmatically defined in a Hammer configuration.

Configuration (Hammer IR)
=========================

To configure a Hammer flow, supply a set yaml or json configuration files that chooses the tool and technology plugins and versions as well as any design specific configuration options. Collectively, this configuration API is referred to as Hammer IR and can be generated from higher-level abstractions.

The current set of all available Hammer APIs is codified `here <https://github.com/ucb-bar/hammer/blob/master/src/hammer-vlsi/defaults.yml>`__.

Tool Plugins
============

Hammer supports separately managed plugins for different CAD tool vendors. You may be able to acquire access to the included Cadence, Synopsys, and Mentor plugins repositories with permission from the respective CAD tool vendor.
The types of tools (by Hammer names) supported currently include:

* synthesis
* par
* drc
* lvs
* sram_generator
* pcb

Several configuration variables are needed to configure your tool plugin of choice.

First, select which tool to use for each action by setting ``vlsi.core.<tool_type>_tool`` to the name of your tool, e.g. ``vlsi.core.par_tool: "innovus"``.

Then, point Hammer to the folder that contains your tool plugin by setting ``vlsi.core.<tool_type>_tool_path``.
This directory should include a folder with the name of the tool, which itself includes a python file ``__init__.py`` and a yaml file ``defaults.yml``. Customize the version of the tool by setting ``<tool_type>.<tool_name>.version`` to a tool specific string.

The ``__init__.py`` file should contain a variable, ``tool``, that points to the class implementing this tool.
This class should be a subclass of ``Hammer<tool_type>Tool``, which will be a subclass of ``HammerTool``. The class should implement methods for all the tool's steps.

The ``defaults.yml`` file contains tool-specific configuration variables. The defaults may be overridden as necessary.

Technology Plugins
==================

Hammer supports separately managed technology plugins to satisfy NDAs. You may be able to acquire access to certain pre-built technology plugins with permission from the technology vendor. Or, to build your own tech plugin, you need at least a ``<tech_name>.tech.json`` and ``defaults.yml``. An ``__init__.py`` is optional if there are any technology-specific methods or hooks to run.

The `ASAP7 plugin <https://github.com/ucb-bar/hammer/tree/master/src/hammer-vlsi/technology/asap7>`__ is a good starting point for setting up a technology plugin. Refer to Hammer's documentation for the schema and setup instructions.

Several configuration variables are needed to configure your technology of choice.

First, choose the technology, e.g. ``vlsi.core.technology: asap7``, then point to the location with the PDK tarball with ``technology.<tech_name>.tarball_dir`` or pre-installed directory with ``technology.<tech_name>.install_dir`` and (if applicable) the plugin repository with ``vlsi.core.technology_path``.

Technology-specific options such as supplies, MMMC corners, etc. are defined in their respective ``vlsi.inputs...`` configurations. Options for the most common use case are already defined in the technology's ``defaults.yml`` and can be overridden by the user.

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

Advanced Usage
==============

Alternative RTL Flows
---------------------
The Make-based build system provided supports using Hammer without using RTL generated by Chipyard. To push a custom verilog module through, one only needs to export the following environment variables before ``make buildfile``.

.. code-block:: shell

    export CUSTOM_VLOG=<your verilog files>
    export VLSI_TOP=<your top module>

Manual Step Execution & Dependency Tracking
-------------------------------------------
It is invariably necessary to debug certain steps of the flow, e.g. if the power strap settings need to be updated. The underlying Hammer commands support options such as ``--to_step``, ``--from_step``, and ``--only_step``. These allow you to control which steps of a particular action are executed.

Make's dependency tracking can sometimes result in re-starting the entire flow when the user only wants to re-run a certain action. Hammer's build system has "redo" targets such as ``redo-syn`` and ``redo-par``.

Say you need to update some power straps settings in ``example.yml`` and want to try out the new settings:

.. code-block:: shell

   make redo-par HAMMER_REDO_ARGS='-p example.yml --only_step power_straps'

Simulation
----------
With the Synopsys plugin, RTL and gate-level simulation is supported using VCS. While this example does not implement any simulation, refer to Hammer's documentation for how to set it up for your design.
