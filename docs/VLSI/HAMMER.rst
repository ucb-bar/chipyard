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

Technology Plugins
==================

Hammer supports separately managed plugins for different technologies.


Configuration
=============

To configure a hammer flow the user needs to supply a yaml or json configuration file the chooses the tool and technology plugins and versions as well as any design specific configuration APIs.

You can see the current set of all avaialable Hammer APIs `here <https://github.com/ucb-bar/hammer/blob/master/src/hammer-vlsi/defaults.yml>`.
