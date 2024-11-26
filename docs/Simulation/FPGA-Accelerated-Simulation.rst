.. _firesim-sim-intro:

FPGA-Accelerated Simulation
==============================

FireSim
-----------------------

`FireSim <https://fires.im/>`__ is an open-source cycle-accurate FPGA-accelerated full-system hardware simulation platform that runs on FPGAs (Amazon EC2 F1 FPGAs and local FPGAs).
FireSim allows RTL-level simulation at orders-of-magnitude faster speeds than software RTL simulators.
FireSim also provides additional device models to allow full-system simulation, including memory models and network models.

FireSim supports running on Amazon EC2 F1 FPGA-enabled cloud instances and on locally managed Linux machines with FPGAs attached.
Please refer to the `FireSim documentation <https://docs.fires.im/en/latest/>` for how to initially setup your machine for FireSim simulations.

Example FireSim Workflow
------------------------

First, ensure your machine is setup for FireSim by following the `FireSim documentation <https://docs.fires.im/en/latest/>`.
There are setup options for Amazon EC2 F1 FPGA-enabled virtual instances, local Linux machines with FPGAs, and more.

.. note:: The rest of this documentation assumes you are running on an Amazon EC2 F1 FPGA-enabled virtual instance.

In order to simuate your Chipyard design using FireSim, make sure to follow the repository setup as described by :ref:`Chipyard-Basics/Initial-Repo-Setup:Initial Repository Setup`, if you have not already.
By default, Chipyard initializes FireSim with it's :gh-file-ref:`build-setup.sh` script by internally running :gh-file-ref:`scripts/firesim-setup.sh`.
This :gh-file-ref:`scripts/firesim-setup.sh` script initializes additional submodules and then invokes FireSim's ``build-setup.sh`` script with the ``--library`` to properly initialize FireSim as a library submodule in Chipyard.

Finally, source the following environment at the root of the FireSim directory:

.. code-block:: shell

    cd sims/firesim
    # (Recommended) The default manager environment (includes env.sh)
    source sourceme-manager.sh
    # Completing setup using the manager
    firesim managerinit --platform f1

.. Note:: Every time you want to use FireSim with a fresh shell, you must source ``sourceme-manager.sh``

At this point you're ready to use FireSim with Chipyard.
If you're not already familiar with FireSim, please return to the `FireSim documentation <https://docs.fires.im/en/latest/>` and visit one of the guides/tutorials.

Running your Design in FireSim
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Converting a Chipyard config (one in :gh-file-ref:`generators/chipyard/src/main/scala` to run in FireSim is simple, and can be done either through the traditional configuration system or through FireSim's build-recipes scheme.

A FireSim simulation requires 2 additional config fragments:

* ``WithFireSimConfigTweaks`` modifies your design to better fit the FireSim usage model. This is composed of multiple smaller config fragments. For example, the removal of clock-gating (using the ``WithoutClockGating`` config fragment) which is required for correct functioning of the compiler. This config fragment also includes other config fragments such as the inclusion of UART in the design, which although may technically be optional,is *strongly* recommended.
* ``WithDefaultFireSimBridges`` sets the ``IOBinders`` key to use FireSim's Bridge system, which can drive target IOs with software bridge models running on the simulation host. See the `FireSim documentation <https://docs.fires.im/en/latest/>` for details.

The simplest method to add this config fragment to your custom Chipyard config is through FireSim's build recipe scheme.
After your FireSim environment is setup, you will define your custom build recipe in ``sims/firesim/deploy/config_build_recipes.yaml``.
By prepending the FireSim config fragments (separated by ``_``) to your Chipyard configuration, these config fragments will be added to your custom configuration as if they were listed in a custom Chisel config class definition.
For example, if you would like to convert the Chipyard ``chipyard.LargeBoomV3Config`` to a FireSim simulation with a DDR3 memory model, the appropriate FireSim ``TARGET_CONFIG`` would be ``WithDefaultFireSimBridges_WithFireSimConfigTweaks_chipyard.LargeBoomV3Config`` and the ``PLATFORM_CONFIG`` would be something like ``FRFCFS16GBQuadRankLLC4MB_*``.

An alternative method to prepending the FireSim config fragments in the FireSim build recipe is to create a new "permanent" FireChip custom configuration, which includes the FireSim config fragments.
We are using the same target (top) RTL, and only need to specify a new set of connection behaviors for the IOs of that module.
Simply create a matching config within :gh-file-ref:`generators/firechip/chip/src/main/scala/TargetConfigs.scala` that inherits your config defined in the ``chipyard`` Scala package.

.. literalinclude:: ../../generators/firechip/chip/src/main/scala/TargetConfigs.scala
    :language: scala
    :start-after: DOC include start: firesimconfig
    :end-before: DOC include end: firesimconfig

While this option seems to require the maintenance of additional configuration code, it has the benefit of allowing for the inclusion of more complex config fragments which also accept custom arguments.

For more information on how to build your own hardware design on FireSim, please refer to :fsim_doc:`FireSim Docs <Getting-Started-Guides/AWS-EC2-F1-Getting-Started/Building-a-FireSim-AFI.html#building-your-own-hardware-designs-firesim-amazon-fpga-images>`.

Pre-built FireSim FPGA Bitstreams
---------------------------------

Chipyard provides a set of pre-built FPGA bitstreams and build recipes for FireSim located in :gh-file-ref:`sims/firesim-staging/sample_config_hwdb.yaml` and :gh-file-ref:`sims/firesim-staging/sample_config_build_recipes.yaml`.
To use these bitstreams, you can add the following args to the FireSim manager invocation:

.. code-block:: shell

   firesim buildbitstream -r sims/firesim-staging/sample_config_build_recipes.yaml

   # or

   firesim {launchrunfarm, infrasetup, runworkload, terminaterunfarm} -a sims/firesim-staging/sample_config_hwdb.yaml -r sims/firesim-staging/sample_config_build_recipes.yaml

Remember that you still need to properly modify the other YAML input files to refer to the build recipes and/or bitstreams in the pre-built files.
