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


example-openroad.yml
^^^^^^^^^^^^^^^^^^^^
This contains the Hammer configuration for the OpenROAD tool flow. 
It selects tools for synthesis (Yosys), place and route (OpenROAD), DRC (Magic), and LVS (NetGen).
For the remaining commands, we will need to specify this file as the tool configuration to hammer via the ``TOOLS_CONF`` Makefile variable.


Generating SRAMs
^^^^^^^^^^^^^^^^
To map the generic memory macros in the generarted Verilog to the SRAMs in your technology process, run the following command:

.. code-block:: shell

    make srams tech_name=sky130 CONFIG=TinyRocketConfig

Generating Verilog
^^^^^^^^^^^^^^^^^^
To elaborate the ``TinyRocketConfig`` from Chisel to Verilog, run:

.. code-block:: shell

    make verilog tech_name=sky130 CONFIG=TinyRocketConfig

The ``CONFIG=TinyRocketConfig`` selects the target generator config in the same manner as the rest of the Chipyard framework. This elaborates a stripped-down Rocket Chip in the interest of minimizing tool runtime. The resulting verilog is located in ``./generated-src/chipyard.TestHarness.TinyRocketConfig/chipyard.TestHarness.TinyRocketConfig.top.v``.

Note that in the generated Verilog, there are generic memory macros for the various memory components (dcache, icache, tag array, PTW). 
This is the same Verilog that is generated for RTL simulations in the ``~chipyard/sims/verilator`` directory, see :ref:`Simulation/Software-RTL-Simulation:Software RTL Simulation` for directions on how to run these simulations.

Building the Design
^^^^^^^^^^^^^^^^^^^
To set up all prerequisites for the build system to push the design and SRAM macros through the flow:

.. code-block:: shell

    make buildfile tech_name=sky130 TOOLS_CONF=example-openroad.yml CONFIG=TinyRocketConfig

The command ``make buildfile`` generates a set of Make targets in ``build/hammer.d``. 
It needs to be re-run if environment variables are changed. 
It is recommended that you edit these variables directly in the Makefile rather than exporting them to your shell environment.


Synthesis
^^^^^^^^^

.. code-block:: shell

    make syn tech_name=sky130 CONFIG=TinyRocketConfig

Post-synthesis logs and collateral are in ``build/syn-rundir``. 

.. The raw quality of results data is available at ``build/syn-rundir/reports``, and methods to extract this information for design space exploration are a work in progress.

Place-and-Route
^^^^^^^^^^^^^^^
.. code-block:: shell

    make par tech_name=sky130 CONFIG=TinyRocketConfig

After completion, the final database can be opened in an interactive OpenROAD session.

.. code-block:: shell

    cd ./build/par-rundir
    ./generated-scripts/open_chip


Below is the post-PnR layout for the TinyRocketConfig in Sky130 generated by OpenROAD.

.. image:: ../_static/images/vlsi-openroad-par-tinyrocketconfig.png

Intermediate databases are written in ``build/par-rundir`` between each step of the ``par`` action. These databases can be restored in an interactive OpenROAD session as desired for debugging purposes.

.. code-block:: shell

    openroad  # launch OpenROAD tool
    openroad> read_db pre_global_route

.. Timing reports are found in ``build/par-rundir/timingReports``. They are gzipped text files.

DRC & LVS
^^^^^^^^^
To run DRC & LVS:

.. code-block:: shell

    make drc tech_name=sky130 CONFIG=TinyRocketConfig
    make lvs tech_name=sky130 CONFIG=TinyRocketConfig

Some DRC errors are expected from this PDK, especially with regards to the SRAMs, as explained in the 
`Sky130 Hammer plugin README  <https://github.com/ucb-bar/hammer/blob/master/src/hammer-vlsi/technology/sky130/README.md>`__.


VLSI Flow Control
-----------------
The Hammer tool plugins for each action (e.g. ``syn``, ``par``) support multiple steps (e.g. ``macro_placement``, ``global_route``).
Hammer saves the design database before and after each step in ``build/par-rundir/<pre or post>_<step name>``.
The Hammer flow supports being able to start/stop before/after any of these steps. 
See the `Hammer documentation on Flow Control <https://docs.hammer-eda.org/en/latest/Hammer-Use/Flow-Control.html>`__ for a full list and description of the options.
The ``Makefile`` in the ``vlsi`` directory passes this extra information via the ``HAMMER_EXTRA_ARGS`` variable.
This variable can also be used to specify additional YAML configurations that may have changed or been omitted from the inital build.

The below examples use the ``redo-par`` Make target to re-run only place-and-route. ``redo-`` may be prepended to any of the VLSI flow actions to re-run only that action.

.. code-block:: shell

      # the following two statements are equivalent because the 
      #   extraction step immediately precedes the write_design step
      make redo-par HAMMER_EXTRA_ARGS="--start_after_step extraction"
      make redo-par HAMMER_EXTRA_ARGS="--start_before_step write_design"

      # example of re-running only floorplanning to test out a new floorplan configuration
      make redo-par HAMMER_EXTRA_ARGS="--only_step floorplan_design -p example-sky130.yml"

See the `OpenROAD tool plugin README <https://github.com/ucb-bar/hammer/tree/master/src/hammer-vlsi/par/openroad>`__ for the full list of OpenROAD tool steps.

Documentation
-------------
For more information about Hammer's underlying implementation, visit the `Hammer documentation website <https://docs.hammer-eda.org/en/latest/index.html>`__.

For details about the plugins used in this tutorial, check out the `OpenROAD tool plugin repo + README <https://github.com/ucb-bar/hammer/tree/master/src/hammer-vlsi/par/openroad>`__
and `Sky130 tech plugin repo + README <https://github.com/ucb-bar/hammer/tree/master/src/hammer-vlsi/technology/sky130>`__.