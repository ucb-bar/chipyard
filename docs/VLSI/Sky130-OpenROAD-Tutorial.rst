.. _sky130-openroad-tutorial:

Sky130 + OpenROAD Tutorial
==========================
The ``vlsi`` folder of this repository contains an example Hammer flow with the TinyRocketConfig from Chipyard. This example tutorial uses the built-in Sky130 technology plugin and OpenROAD tool plugin.

Project Structure
-----------------

This example gives a suggested file structure and build system. The ``vlsi/`` folder will eventually contain the following files and folders:

* ``Makefile``, ``sim.mk``, ``power.mk``

  * Integration of Hammer's build system into Chipyard and abstracts away some Hammer commands.

* ``build``

  * Hammer output directory. Can be changed with the ``OBJ_DIR`` variable.
  * Will contain subdirectories such as ``syn-rundir`` and ``par-rundir`` and the ``inputs.yml`` denoting the top module and input Verilog files.

* ``example-vlsi-sky130``

  * Entry point to Hammer. Contains example placeholders for hooks.

* ``example-sky130.yml``, ``example-openroad.yml``

  * Hammer IR for this tutorial.

* ``example-design.yml``, ``example-asap7.yml``, ``example-nangate45.yml``, ``example-tech.yml``

  * Hammer IR not used for this tutorial but provided as templates.

* ``generated-src``

  * All of the elaborated Chisel and FIRRTL.

* ``hammer``, ``hammer-<vendor>-plugins``, ``hammer-<tech>-plugin``

  * Core repository, and commercial tool and NDA technology plugins.
  * Open-source plugins are located under ``hammer/src/hammer-vlsi/<syn-par-drc-lvs>/<tool>`` and ``hammer/src/hammer-vlsi/technology/<tech>``

Prerequisites
-------------

* Python 3.4+
* numpy package
* OpenROAD flow tools:

  * Yosys (synthesis), install `from source <https://yosyshq.net/yosys/download.html>`__ or `using conda <https://anaconda.org/TimVideos/yosys>`__
  * OpenROAD (place-and-route), install `from source <https://openroad.readthedocs.io/en/latest/main/README.html#install-dependencies>`__
  * Magic (DRC), install `from source <http://www.opencircuitdesign.com/magic/install.html>`__
  * NetGen (LVS), install `from source <http://www.opencircuitdesign.com/netgen/install.html>`__ or `using conda <https://anaconda.org/conda-forge/netgen>`__ 

* Sky130 PDK, install using `these directions  <https://github.com/ucb-bar/hammer/blob/master/src/hammer-vlsi/technology/sky130/README.md>`__

Initial Setup
-------------
In the Chipyard root, run:

.. code-block:: shell

    ./scripts/init-vlsi.sh sky130 openroad
    
to pull the Hammer submodule. Note that for technologies other than ``sky130`` or ``asap7``, the tech plugin submodule is cloned into the ``vlsi`` folder, 
and for the commercial tool flow (set up by omitting the ``openroad`` argument), the tool plugin submodules are cloned into the ``vlsi`` folder.

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

First, set ``technology.sky130.<sky130A, sky130_nda, openram_lib>`` to the absolute path of the respective directories containing the Sky130 PDK and SRAM files. See the 
`Sky130 Hammer plugin README <https://github.com/ucb-bar/hammer/blob/master/src/hammer-vlsi/technology/sky130/README.md>`__
for details about the PDK setup.


Synthesis
^^^^^^^^^
.. code-block:: shell

    make syn tech_name=sky130 TOOLS_CONF=example-openroad.yml CONFIG=TinyRocketConfig

Post-synthesis logs and collateral are in ``build/syn-rundir``. 

.. The raw quality of results data is available at ``build/syn-rundir/reports``, and methods to extract this information for design space exploration are a work in progress.

Place-and-Route
^^^^^^^^^^^^^^^
.. code-block:: shell

    make par tech_name=sky130 TOOLS_CONF=example-openroad.yml CONFIG=TinyRocketConfig

After completion, the final database can be opened in an interactive OpenROAD session.

.. code-block:: shell

    cd ./build/par-rundir
    ./generated-scripts/open_chip

TODO: insert screenshot of database here

Intermediate databases are written in ``build/par-rundir`` between each step of the ``par`` action. These databases can be restored in an interactive OpenROAD session as desired for debugging purposes.

.. code-block:: shell

    openroad  # launch OpenROAD tool
    openroad> read_db pre_global_route

.. Timing reports are found in ``build/par-rundir/timingReports``. They are gzipped text files.

DRC & LVS
^^^^^^^^^
To run DRC & LVS:

.. code-block:: shell

    make drc tech_name=sky130 TOOLS_CONF=example-openroad.yml CONFIG=TinyRocketConfig
    make lvs tech_name=sky130 TOOLS_CONF=example-openroad.yml CONFIG=TinyRocketConfig

Some DRC errors are expected from this PDK, especially with regards to the SRAMs, as explained in the 
`Sky130 Hammer plugin README  <https://github.com/ucb-bar/hammer/blob/master/src/hammer-vlsi/technology/sky130/README.md>`__.
