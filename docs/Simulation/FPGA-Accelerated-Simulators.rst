FPGA-Accelerated Simulators
==============================

FireSim
-----------------------

`FireSim <https://fires.im/>`__ is an open-source cycle-accurate FPGA-accelerated full-system hardware simulation platform that runs on cloud FPGAs (Amazon EC2 F1).
FireSim allows RTL-level simulation at orders-of-magnitude faster speeds than software RTL simulators.
FireSim also provides additional device models to allow full-system simulation, including memory models and network models.

FireSim currently supports running only on Amazon EC2 F1 FPGA-enabled virtual instances on the public cloud.
In order to simulate your Chipyard design using FireSim, if you have not
already, follow the initial EC2 setup instructions as detailed in the `FireSim
documentation  <http://docs.fires.im/en/latest/Initial-Setup/index.html>`__.
Then clone your full Chipyard repository onto your Amazon EC2 FireSim manager
instance, and setup your Chipyard repository as you would normally.

When you are ready to use FireSim, initalize it as library in Chipyard by running:

.. code-block:: shell

    # At the root of your chipyard repo
    ./scripts/firesim-setup.sh --fast


``firesim-setup.sh`` initializes additional submodules and then invokes
firesim's ``build-setup.sh`` script. ``firesim-setup.sh`` accepts all of the same arguments and
passes them through to ``build-setup.sh``, adding ``--library`` to properly
initialize FireSim as a library submodule in chipyard. You may run
``./sims/firesim/build-setup.sh --help`` to see more options.

In order to build bitstreams, run simulations, or to generate MIDAS-transformed RTL for your
simulator, you'll need to source one of the following three environments:

.. code-block:: shell

    cd sims/firesim
    # (Recommended) The default manager environment (includes env.sh)
    source sourceme-f1-manager.sh

    # OR A minimal environment to run recipes out of sim/ (to invoke MIDAS; run MIDAS-level RTL simulation)generate RTL; transform At the root of your chipyard repo
    source env.sh

    # OR A complete environment to run local FPGA builds with Vivado
    source sourceme-f1-full.sh

At this point you're ready to use FireSim with Chipyard. If you're not already
familiar with FireSim, please refer to the `FireSim Docs <http://docs.fires.im/>`__, and proceed
through the rest of the tutorial.


Current Limitations:
++++++++++++++++++++

FireSim integration in chipyard is still a work in progress. Presently, you
cannot build a FireSim simulator from any generator project in Chipyard except ``firechip``, 
which properly invokes MIDAS on the target RTL.

In the interim, workaround this limitation by importing Config and Module
classes from other generator projects into FireChip. You should then be able to
refer to those classes or an alias of them in  your ``DESIGN`` or ``TARGET_CONFIG``
variables. Note that if your target machine has I/O not provided in the default
FireChip targets (see ``generators/firechip/src/main/scala/Targets.scala``) you may need
to write a custom endpoint.
