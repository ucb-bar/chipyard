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

* ``env.yml``

  * A template file for tool environment configuration. Fill in the install and license server paths for your environment. For SLICE and BWRC affiliates, example environment configs are found `here <https://github.com/ucb-bar/hammer/tree/master/e2e/env>`__.

* ``example-vlsi-sky130``

  * Entry point to Hammer. Contains example placeholders for hooks.

* ``example-sky130.yml``, ``example-openroad.yml``, ``example-designs/sky130-openroad.yml``

  * Hammer IR for this tutorial. For SLICE and BWRC affiliates, an example ASAP7 config is found `here <https://github.com/ucb-bar/hammer/tree/master/e2e/pdks>`__.

* ``example-design.yml``, ``example-asap7.yml``, ``example-tech.yml``

  * Hammer IR not used for this tutorial but provided as templates.

* ``generated-src``

  * All of the elaborated Chisel and FIRRTL.

* ``hammer-<vendor>-plugins``

  * Tool plugin repositories not used for this tutorial (they are provided in the hammer-vlsi package).

Prerequisites
-------------

* Python 3.9+
* OpenROAD flow tools:

  * Yosys (synthesis), install `from source <https://yosyshq.net/yosys/download.html>`__ or `using conda <https://anaconda.org/TimVideos/yosys>`__
  * OpenROAD (place-and-route), install `from source <https://openroad.readthedocs.io/en/latest/main/README.html#install-dependencies>`__
  * Magic (DRC), install `from source <http://www.opencircuitdesign.com/magic/install.html>`__
  * NetGen (LVS), install `from source <http://www.opencircuitdesign.com/netgen/install.html>`__ or `using conda <https://anaconda.org/conda-forge/netgen>`__

* Sky130 PDK, install using `these directions  <https://github.com/ucb-bar/hammer/blob/master/hammer/technology/sky130>`__

Initial Setup
-------------
In the Chipyard root, ensure that you have the Chipyard conda environment activated. Then, run:

.. code-block:: shell

    ./scripts/init-vlsi.sh sky130 openroad

to pull and install the plugin submodules. Note that for technologies other than ``sky130`` or ``asap7``, the tech submodule is cloned in the ``vlsi`` folder, 
and for the commercial tool flow (set up by omitting the ``openroad`` argument), the tool plugin submodules are cloned into the ``vlsi`` folder.

Building the Design
--------------------
To elaborate the ``TinyRocketConfig`` and set up all prerequisites for the build system to push the design and SRAM macros through the flow:

.. code-block:: shell

    make buildfile tutorial=sky130-openroad

The command ``make buildfile`` generates a set of Make targets in ``build/hammer.d``.
It needs to be re-run if environment variables are changed.
It is recommended that you edit these variables directly in the Makefile rather than exporting them to your shell environment.

For the purpose of brevity, in this tutorial we will set the Make variable ``tutorial=sky130-openroad``,
which will cause additional variables to be set in ``tutorial.mk``, a few of which are summarized as follows:

* ``CONFIG=TinyRocketConfig`` selects the target generator config in the same manner as the rest of the Chipyard framework. This elaborates a stripped-down Rocket Chip in the interest of minimizing tool runtime.
* ``tech_name=sky130`` sets a few more necessary paths in the ``Makefile``, such as the appropriate Hammer plugin
* ``TOOLS_CONF`` and ``TECH_CONF`` select the approproate YAML configuration files, ``example-openroad.yml`` and ``example-sky130.yml``, which are described below
* ``DESIGN_CONF`` and ``EXTRA_CONFS`` allow for additonal design-specific overrides of the Hammer IR in ``example-sky130.yml``
* ``VLSI_OBJ_DIR=build-sky130-openroad`` gives the build directory a unique name to allow running multiple flows in the same repo. Note that for the rest of the tutorial we will still refer to this directory in file paths as ``build``, again for brevity.
* ``VLSI_TOP`` is by default ``ChipTop``, which is the name of the top-level Verilog module generated in the Chipyard SoC configs. By instead setting ``VLSI_TOP=Rocket``, we can use the Rocket core as the top-level module for the VLSI flow, which consists only of a single RISC-V core (and no caches, peripherals, buses, etc). This is useful to run through this tutorial quickly, and does not rely on any SRAMs.
* ``ENABLE_CUSTOM_FIRRTL_PASS = 1`` is required for synthesis through Yosys. This reverts to the Scala FIRRTL Compiler so that unsupported multidimensional arrays are not generated in the Verilog.

Running the VLSI Flow
---------------------

example-vlsi-sky130
^^^^^^^^^^^^^^^^^^^
This is the entry script with placeholders for hooks. In the ``ExampleDriver`` class, a list of hooks is passed in the ``get_extra_par_hooks``. Hooks are additional snippets of python and TCL (via ``x.append()``) to extend the Hammer APIs. Hooks can be inserted using the ``make_pre/post/replacement_hook`` methods as shown in this example. Refer to the Hammer documentation on hooks for a detailed description of how these are injected into the VLSI flow.


example-sky130.yml
^^^^^^^^^^^^^^^^^^
This contains the Hammer configuration for this example project. Example clock constraints, power straps definitions, placement constraints, and pin constraints are given. Additional configuration for the extra libraries and tools are at the bottom.

First, set ``technology.sky130.<sky130A, openram_lib>`` to the absolute path of the respective directories containing the Sky130 PDK and SRAM files. See the
`Sky130 Hammer plugin README <https://github.com/ucb-bar/hammer/blob/master/hammer/technology/sky130>`__
for details about the PDK setup.


example-openroad.yml
^^^^^^^^^^^^^^^^^^^^
This contains the Hammer configuration for the OpenROAD tool flow.
It selects tools for synthesis (Yosys), place and route (OpenROAD), DRC (Magic), and LVS (NetGen).

Synthesis
^^^^^^^^^

.. code-block:: shell

    make syn tutorial=sky130-openroad

Post-synthesis logs and collateral are in ``build/syn-rundir``.

.. The raw quality of results data is available at ``build/syn-rundir/reports``, and methods to extract this information for design space exploration are a work in progress.

Place-and-Route
^^^^^^^^^^^^^^^
.. code-block:: shell

    make par tutorial=sky130-openroad

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

    make drc tutorial=sky130-openroad
    make lvs tutorial=sky130-openroad

Some DRC errors are expected from this PDK, especially with regards to the SRAMs, as explained in the
`Sky130 Hammer plugin README  <https://github.com/ucb-bar/hammer/blob/master/hammer/technology/sky130>`__.


VLSI Flow Control
^^^^^^^^^^^^^^^^^
Firt, refer to the :ref:`VLSI/Hammer:VLSI Flow Control` documentation. The below examples use the ``redo-par`` Make target to re-run only place-and-route. ``redo-`` may be prepended to any of the VLSI flow actions to re-run only that action.

.. code-block:: shell

      # the following two statements are equivalent because the
      #   extraction step immediately precedes the write_design step
      make redo-par HAMMER_EXTRA_ARGS="--start_after_step extraction"
      make redo-par HAMMER_EXTRA_ARGS="--start_before_step write_design"

      # example of re-running only floorplanning to test out a new floorplan configuration
      make redo-par HAMMER_EXTRA_ARGS="--only_step floorplan_design -p example-sky130.yml"

See the `OpenROAD tool plugin <https://github.com/ucb-bar/hammer/blob/master/hammer/par/openroad>`__ for the full list of OpenROAD tool steps and their implementations.

Documentation
-------------
For more information about Hammer's underlying implementation, visit the `Hammer documentation website <https://hammer-vlsi.readthedocs.io/en/latest/index.html>`__.

For details about the plugins used in this tutorial, check out the `OpenROAD tool plugin repo + README <https://github.com/ucb-bar/hammer/blob/master/hammer/par/openroad>`__
and `Sky130 tech plugin repo + README <https://github.com/ucb-bar/hammer/blob/master/hammer/technology/sky130>`__.
