FPGA-Accelerated Simulators
==============================

FireSim
-----------------------

`FireSim <https://fires.im/>`__ is an open-source cycle-accurate FPGA-accelerated full-system hardware simulation platform that runs on cloud FPGAs (Amazon EC2 F1).
FireSim allows RTL-level simulation at orders-of-magnitude faster speeds than software RTL simulators.
FireSim also provides additional device models to allow full-system simulation, including memory models and network models.

FireSim currently supports running only on Amazon EC2 F1 FPGA-enabled virtual instances.
In order to simulate your Chipyard design using FireSim, if you have not
already, follow the initial EC2 setup instructions as detailed in the `FireSim
documentation  <http://docs.fires.im/en/latest/Initial-Setup/index.html>`__.
Then clone Chipyard onto your FireSim manager
instance, and setup your Chipyard repository as you would normally.

Next, initalize FireSim as library in Chipyard by running:

.. code-block:: shell

    # At the root of your chipyard repo
    ./scripts/firesim-setup.sh --fast

``firesim-setup.sh`` initializes additional submodules and then invokes
firesim's ``build-setup.sh`` script adding ``--library`` to properly
initialize FireSim as a library submodule in chipyard. You may run
``./sims/firesim/build-setup.sh --help`` to see more options.

Finally, source the following environment at the root of the firesim directory:

.. code-block:: shell

    cd sims/firesim
    # (Recommended) The default manager environment (includes env.sh)
    source sourceme-f1-manager.sh

`Every time you want to use FireSim with a fresh shell, you must source this sourceme.sh`

At this point you're ready to use FireSim with Chipyard. If you're not already
familiar with FireSim, please return to the `FireSim Docs
<https://docs.fires.im/en/latest/Initial-Setup/Setting-up-your-Manager-Instance.html#completing-setup-using-the-manager>`__,
and proceed with the rest of the tutorial.

Current Limitations:
++++++++++++++++++++

FireSim integration in Chipyard is still a work in progress. Presently, you
cannot build a FireSim simulator from any generator project in Chipyard except ``firechip``, 
which properly invokes MIDAS on the target RTL.

In the interim, workaround this limitation by importing Config and Module
classes from other generator projects into FireChip. You should then be able to
refer to those classes or an alias of them in  your ``DESIGN`` or ``TARGET_CONFIG``
variables. Note that if your target machine has I/O not provided in the default
FireChip targets (see ``generators/firechip/src/main/scala/Targets.scala``) you may need
to write a custom endpoint.
