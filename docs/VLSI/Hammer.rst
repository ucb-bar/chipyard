.. _hammer:

Core Hammer
================================

`Hammer <https://github.com/ucb-bar/hammer>`__ is a physical design flow which encourages reusability by partitioning physical design specifications into three distinct concerns: design, CAD tool, and process technology. Hammer wraps around vendor specific technologies and tools to provide a single API to address ASIC design concerns.
Hammer allows for reusability in ASIC design while still providing the designers leeway to make their own modifications.

For more information, read the `Hammer paper <https://dl.acm.org/doi/abs/10.1145/3489517.3530672>`__ and see the `GitHub repository <https://github.com/ucb-bar/hammer>`__ and associated documentation.

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


VLSI Flow Control
-----------------
Sometimes we want more fine-grained control of the VLSI flow than at the action level.
The Hammer flow supports being able to start/stop before/after any of the steps in a particular action.
See the `Hammer documentation on Flow Control <https://hammer-vlsi.readthedocs.io/en/latest/Hammer-Use/Flow-Control.html>`__ for a full list and description of the options.
The ``Makefile`` in the ``vlsi`` directory passes this extra information via the ``HAMMER_EXTRA_ARGS`` variable.
This variable can also be used to specify additional YAML configurations that may have
changed or been omitted from the inital build.


Configuration (Hammer IR)
=========================

To configure a Hammer flow, supply a set ``yaml`` or ``json`` configuration files that chooses the tool and technology plugins and versions as well as any design specific configuration options. Collectively, this configuration API is referred to as Hammer IR and can be generated from higher-level abstractions.

The current set of all available Hammer APIs is codified `here <https://github.com/ucb-bar/hammer/blob/master/hammer/config/defaults.yml>`__.

Tool Plugins
============

Hammer supports separately managed plugins for different CAD tool vendors. You may be able to acquire access to the included Cadence, Synopsys, and Mentor plugins repositories with permission from the respective CAD tool vendor.
The types of tools (by Hammer names) supported currently include:

* synthesis
* par
* drc
* lvs
* sram_generator
* sim
* power
* pcb

Several configuration variables are needed to configure your tool plugin of choice.

First, select which tool to use for each action by setting ``vlsi.core.<tool_type>_tool`` to the package name of your tool, e.g. ``vlsi.core.par_tool: "hammer.par.innovus"``.

This package directory should include a folder with the name of the tool, which itself includes a python file ``__init__.py`` and a yaml file ``defaults.yml``. Customize the version of the tool by setting ``<tool_type>.<tool_name>.version`` to a tool specific string.

The ``__init__.py`` file should contain a variable, ``tool``, that points to the class implementing this tool.
This class should be a subclass of ``Hammer<tool_type>Tool``, which will be a subclass of ``HammerTool``. The class should implement methods for all the tool's steps.

The ``defaults.yml`` file contains tool-specific configuration variables. The defaults may be overridden as necessary.

Technology Plugins
==================

Hammer supports separately managed technology plugins to satisfy NDAs. You may be able to acquire access to certain pre-built technology plugins with permission from the technology vendor. Or, to build your own tech plugin, you need at least a ``<tech_name>.tech.json`` and ``defaults.yml``. An ``__init__.py`` is optional if there are any technology-specific methods or hooks to run.

The `ASAP7 plugin <https://github.com/ucb-bar/hammer/blob/master/hammer/technology/asap7>`__ is a good starting point for setting up a technology plugin because it is an open-source example that is not suitable for taping out a chip. Refer to Hammer's documentation for the schema and detailed setup instructions.

Several configuration variables are needed to configure your technology of choice.

First, choose the technology package, e.g. ``vlsi.core.technology: hammer.technology.asap7``, then point to the location with the PDK tarball with ``technology.<tech_name>.tarball_dir`` or pre-installed directory with ``technology.<tech_name>.install_dir``.

Technology-specific options such as supplies, MMMC corners, etc. are defined in their respective ``vlsi.inputs...`` configurations. Options for the most common use case are already defined in the technology's ``defaults.yml`` and can be overridden by the user.
