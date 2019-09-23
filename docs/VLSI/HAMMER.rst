Core HAMMER
================================

`HAMMER <https://github.com/ucb-bar/hammer>`__ is a physical design generator that wraps around vendor specific technologies and tools to provide a single API to create ASICs.
HAMMER allows for reusability in ASIC design while still providing the designers leeway to make their own modifications.

For more information, read the `HAMMER paper <https://people.eecs.berkeley.edu/~edwardw/pubs/hammer-woset-2018.pdf>`__ and see the `GitHub repository <https://github.com/ucb-bar/hammer>`__.

Actions
-------

Actions are the top-level tasks Hammer is capable of executing (e.g. synthesis, place-and-route, etc.)

Steps
-------

Steps are the sub-components of actions that individually addressable in Hammer (e.g. placement in the place-and-route action).

Hooks
-------

Hooks are modifications to steps or actions that are programmatically defined in a Hammer configuration.

Tool Plugins
============

Hammer supports separately managed plugins for different CAD tool vendors. You may be able to acquire access to the included Cadence, Synopsys, and Mentor Graphics plugins with permission from the respective CAD tool vendor.
The types of tools (by HAMMER names) supported currently include:

* synthesis
* par
* drc
* lvs
* sram_generator
* pcb

In order to configure your tool plugin of choice, you will need to set several configuration variables.
First, you should select which specific tool you want to use by setting ``vlsi.core.<tool_type>_tool`` to the name of your tool.
For example ``vlsi.core.par_tool: "innovus"``.
You will also need to point hammer to the folder that contains your tool plugin by setting ``vlsi.core.<tool_type>_tool_path``.
This directory should include a folder with the name of the tool as specified previously, which itself includes a python file ``__init__.py`` and a yaml file ``defaults.yml`` specifying the default values for any tool specific variables.
In addition you can also customize the version of the tools you use by setting ``<tool_type>.<tool_name>.version`` to a tool specific string.
Looking at the tools ``defaults.yml`` will inform you if there are other variables you would like to set for your use of this tool.

The ``__init__.py`` file should contain a variable, ``tool``, that points to the class implementing this tools Hammer support.
This class should be a subclass of ``Hammer<tool_type>Tool``, which will be a subclass of ``HammerTool``.

Technology Plugins
==================

Hammer supports separately managed plugins for different technologies. You may be able to acquire access to certain pre-built technology plugins with permission from the technology vendor. Or, to build your own tech plugin, you need at least a ``<tech_name>.tech.json`` and ``defaults.yml``. An ``__init__.py`` is optional if there are any technology-specific methods or hooks to run. Refer to the ASAP7 plugin and associated documentation for more information.

In order to configure your technology of choice, you will need to set several configuration variables.
First, you need to choose the technology, for example ``vlsi.core.technology: asap7`` and point to the location with the PDK tarball with ``technology.<tech_name>.tarball_dir`` or pre-installed directory with ``technology.<tech_name>.install_dir``.
Technology-specific options such as supplies, MMMC corners, metal layers, etc. will need to be matched to the technology in their respective ``vlsi.inputs...`` configurations.

Configuration
=============

To configure a hammer flow the user needs to supply a yaml or json configuration file the chooses the tool and technology plugins and versions as well as any design specific configuration APIs.

You can see the current set of all available Hammer APIs `here <https://github.com/ucb-bar/hammer/blob/master/src/hammer-vlsi/defaults.yml>`__.

ASAP7 Tutorial
==============
The ``vlsi`` folder of this repository contains an example HAMMER flow with the SHA-3 accelerator and a dummy hard macro in the ASAP7 PDK. It is tested with the Cadence and Mentor tool plugins.

Initial Setup
-------------
Run ``./scripts/init-vlsi.sh TECH_HAME`` to pull the HAMMER & plugin submodules. Note that for technologies other than ASAP7, the tech submodule must be added in the ``vlsi`` folder first.

An example of tool environment configuration for BWRC affiliates is given in ``bwrc-env.yml``. Replace paths as necessary for your build environment.

Pull the HAMMER environment into the shell:

::
    export HAMMER_HOME=$PWD/hammer
    source $HAMMER_HOME/sourceme.sh

Building the Design
-------------------
To elaborate the Sha3RocketConfig (Rocketchip w/ the accelerator) and set up all prerequisites for the build system to push just the accelerator + hard macro through the flow:

::
    export MACROCOMPILER_MODE=' --mode synflops'
    export CONFIG=Sha3RocketConfig
    export VLSI_TOP=Sha3AccelwBB
    make buildfile

Note that because the ASAP7 process does not yet have a memory compiler, synflops are elaborated instead.

Running the VLSI Flow
---------------------
The configuration for this example is contained in ``example.yml`` and the entry script with placeholders for hooks is contained in ``example-vlsi``. Before continuing, ensure you have the `ASAP7 PDK <http://asap.asu.edu/asap/>`__ tarball downloaded (but not extracted) and point the ``technology.asap7.tarball_dir`` to the tarball directory.

To synthesize, type ``make syn``.

Post-synthesis results are in ``build/syn-rundir``. Raw QoR data is available at ``build/syn-rundir/reports``, and methods to extract this information for design space exploration are a WIP.

To place and route, type ``make par``.

If successful, the resulting chip can be opened via ``./build/par-rundir/generated-scripts/open_chip``.

Intermediate database are written in ``build/par-rundir`` between each step of the ``par`` action, and can be restored in an interactive Innovus session as desired for debugging purposes. Compressed timing reports are found in ``build/par-rundir/timingReports``.

To run DRC & LVS, and view the results:

::
    make drc
    ./build/drc-rundir/generated-scripts/view-drc
    make lvs
    ./build/lvs-rundir/generated-scripts/view-lvs

Some DRC errors are expected from this PDK, as explained in the `ASAP7 plugin readme <https://github.com/ucb-bar/hammer/tree/master/src/hammer-vlsi/technology/asap7>`__

Alternative RTL Flows
---------------------
The Make-based build system provided supports using HAMMER without using RTL generated by Chipyard. To push a custom verilog module through, one only needs to export the following environment variables before ``make buildfile``.

::
    export CUSTOM_VLOG=<your verilog files>
    export VLSI_TOP=<your top module>
