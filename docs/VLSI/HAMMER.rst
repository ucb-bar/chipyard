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

Hooks are modifications to steps or actions that are programmaticly defined in a Hammer configuration.

Tool Plugins
============

Hammer supports separatly managed plugins for different CAD tool vendors.
The types of tools(in there hammer names) supported currently include:

* synthesis
* par
* drc
* lvs
* sram_generator
* pcb

In order to configure your tool plugin of choice you will need to set several configuration variables.
First you should select which specific tool you want to use by setting ``vlsi.core.<tool_type>_tool`` to the name of your tool.
For example ``vlsi.core.par_tool: "innovus"``.
You will also need to point hammer to the folder that contains your tool plugin by setting ``vlsi.core.<tool_type>_tool_path``.
This directory should include a folder with the name of the tool as specified previously, which itself includes a python file ``__init__.py`` and a yaml file ``defaults.yml`` specifing the default values for any tool specific variables.
In addition you can also customize the version of the tools you use by setting ``<tool_type>.<tool_name>.version`` to a tool specific string.
Looking at the tools ``defaults.yml`` will inform you if there are other variables you would like to set for your use of this tool.

The ``__init__.py`` file should contain a variable, ``tool``, that points to the class implementing this tools Hammer support.
This class should be a subclass of ``Hammer<tool_type>Tool``, which will be a subclass of ``HammerTool``.


Technology Plugins
==================

Hammer supports separately managed plugins for different technologies.


Configuration
=============

To configure a hammer flow the user needs to supply a yaml or json configuration file the chooses the tool and technology plugins and versions as well as any design specific configuration APIs.

You can see the current set of all avaialable Hammer APIs `here <https://github.com/ucb-bar/hammer/blob/master/src/hammer-vlsi/defaults.yml>`__.
