.. _firesim-sim-intro:

FPGA-Accelerated Simulation
==============================

FireSim
-----------------------

`FireSim <https://fires.im/>`__ is an open-source cycle-accurate FPGA-accelerated full-system hardware simulation platform that runs on FPGAs (Amazon EC2 F1 FPGAs and local FPGAs).
FireSim allows RTL-level simulation at orders-of-magnitude faster speeds than software RTL simulators.
FireSim also provides additional device models to allow full-system simulation, including memory models and network models.

FireSim supports running on Amazon EC2 F1 FPGA-enabled cloud instances and on locally managed Linux machines with FPGAs attached.
The rest of this documentation assumes you are running on an Amazon EC2 F1 FPGA-enabled virtual instance.
In order to simuate your Chipyard design using FireSim, make sure to follow the repository setup as described by
:ref:`Chipyard-Basics/Initial-Repo-Setup:Initial Repository Setup`, if you have not already.
This setup should have setup the Chipyard repository including FireSim by running the ``./scripts/firesim-setup.sh`` script.
``firesim-setup.sh`` initializes additional submodules and then invokes
FireSim's ``build-setup.sh`` script adding ``--library`` to properly
initialize FireSim as a library submodule in Chipyard. You may run
``./sims/firesim/build-setup.sh --help`` to see more options.

Finally, source the following environment at the root of the FireSim directory:

.. code-block:: shell

    cd sims/firesim
    # (Recommended) The default manager environment (includes env.sh)
    source sourceme-manager.sh

.. Note:: Every time you want to use FireSim with a fresh shell, you must source ``sourceme-manager.sh``

At this point you're ready to use FireSim with Chipyard. If you're not already
familiar with FireSim, please return to the :fsim_doc:`FireSim Docs <Initial-Setup/Setting-up-your-Manager-Instance.html#completing-setup-using-the-manager>`,
and proceed with the rest of the tutorial.

Running your Design in FireSim
------------------------------

Converting a Chipyard config (one in ``chipyard/src/main/scala`` to run in FireSim is simple, and can be done either through the traditional configuration system or through FireSim's build-recipes scheme.

A FireSim simulation requires 3 additional config fragments:

* ``WithFireSimConfigTweaks`` modifies your design to better fit the FireSim usage model. This is composed of multiple smaller config fragments. For example, the removal of clock-gating (using the ``WithoutClockGating`` config fragment) which is required for correct functioning of the compiler. This config fragment also includes other config fragments such as the inclusion of UART in the design, which although may technically be optional,is *strongly* recommended.
* ``WithDefaultMemModel`` provides a default configuration for FASED memory models in the FireSim simulation. See the FireSim documentation for details. This config fragment is currently included by default within ``WithFireSimConfigTweaks``, so it isn't neccessary to add in separately, but it is required if you choose not to use ``WithFireSimConfigTweaks``.
* ``WithDefaultFireSimBridges`` sets the ``IOBinders`` key to use FireSim's Bridge system, which can drive target IOs with software bridge models running on the simulation host. See the FireSim documentation for details.


The simplest method to add this config fragments to your custom Chipyard config is through FireSim's build recipe scheme.
After your FireSim environment is setup, you will define your custom build recipe in ``sims/firesim/deploy/deploy/config_build_recipes.ini``. By prepending the FireSim config fragments (separated by ``_``) to your Chipyard configuration, these config fragments will be added to your custom configuration as if they were listed in a custom Chisel config class definition. For example, if you would like to convert the Chipyard ``LargeBoomConfig`` to a FireSim simulation with a DDR3 memory model, the appropriate FireSim ``TARGET_CONFIG`` would be ``DDR3FRFCFSLLC4MB_WithDefaultFireSimBridges_WithFireSimConfigTweaks_chipyard.LargeBoomConfig``. Note that the FireSim config fragments are part of the ``firesim.firesim`` scala package and therefore there do not need to be prefixed with the full package name as opposed to the Chipyard config fragments which need to be prefixed with the chipyard package name.

An alternative method to prepending the FireSim config fragments in the FireSim build recipe is to create a new "permanent" FireChip custom configuration, which includes the FireSim config fragments.
We are using the same target (top) RTL, and only need to specify a new set of connection behaviors for the IOs of that module. Simply create a matching config within ``generators/firechip/src/main/scala/TargetConfigs`` that inherits your config defined in ``chipyard``.


.. literalinclude:: ../../generators/firechip/src/main/scala/TargetConfigs.scala
    :language: scala
    :start-after: DOC include start: firesimconfig
    :end-before: DOC include end: firesimconfig

While this option seems to require the maintenance of additional configuration code, it has the benefit of allowing for the inclusion of more complex config fragments which also accept custom arguments (for example, ``WithDefaultMemModel`` can take an optional argument``)
