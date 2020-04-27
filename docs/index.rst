.. Chipyard documentation master file, created by
   sphinx-quickstart on Fri Mar  8 11:46:38 2019.
   You can adapt this file completely to your liking, but it should at least
   contain the root `toctree` directive.

Welcome to Chipyard's documentation!
====================================

.. image:: ./_static/images/chipyard-logo.svg

Chipyard is a framework for designing and evaluating full-system hardware using agile teams.
It is composed of a collection of tools and libraries designed to provide an integration between open-source and commercial tools for the development of systems-on-chip.

.. IMPORTANT:: **New to Chipyard?** Jump to the :ref:`Chipyard Basics` page for more info.

System Requirements
-------------------------------------------

Chipyard is developed and tested on Linux-based systems.

.. Warning:: It is possible to use this on macOS or other BSD-based systems, although GNU tools will need to be installed; it is also recommended to install the RISC-V toolchain from ``brew``.

.. Warning:: Working under Windows is not recommended.

Setting up the Chipyard Repo
-------------------------------------------

Start by fetching Chipyard's sources. Run:

.. code-block:: shell

    git clone https://github.com/ucb-bar/chipyard.git
    cd chipyard
    ./scripts/init-submodules-no-riscv-tools.sh

This will initialize and checkout all of the necessary git submodules.

Installing Dependencies
-------------------------------------------
Installing the recommended dependencies on Ubuntu/Debian-based platforms:

.. code-block:: shell

    ./scripts/ubuntu-req.sh
    
Installing the recommended dependencies on CentOS-based platforms:

.. code-block:: shell

   ./scripts/centos-req.sh

Installing the RISC-V Tools
-------------------------------------------

We need to install the RISC-V toolchain in order to be able to run RISC-V programs using the Chipyard infrastructure.
This will take about 20-30 minutes. You can expedite the process by setting a ``make`` environment variable to use parallel cores: ``export MAKEFLAGS=-j8``.
To build the toolchains, you should run:

.. code-block:: shell

    ./scripts/build-toolchains.sh

.. Note:: If you are planning to use the Hwacha vector unit, or other RoCC-based accelerators, you should build the esp-tools toolchain by adding the ``esp-tools`` argument to the script above.
  If you are running on an Amazon Web Services EC2 instance, intending to use FireSim, you can also use the ``--ec2fast`` flag for an expedited installation of a pre-compiled toolchain.

Finally, set up Chipyard's environment variables and put the newly built toolchain on your path:

.. code-block:: shell

    source ./env.sh

What's Next?
-------------------------------------------

This depends on what you are planning to do with Chipyard.

* If you intend to run a simulation of one of the vanilla Chipyard examples, go to :ref:`sw-rtl-sim-intro` and follow the instructions.

* If you intend to run a simulation of a custom Chipyard SoC Configuration, go to :ref:`Simulating A Custom Project` and follow the instructions.

* If you intend to run a full-system FireSim simulation, go to :ref:`firesim-sim-intro` and follow the instructions.

* If you intend to add a new accelerator, go to :ref:`customization` and follow the instructions.

* If you want to learn about the structure of Chipyard, go to :ref:`chipyard-components`.

* If you intend to change the generators (BOOM, Rocket, etc) themselves, see :ref:`generator-index`.

* If you intend to run a tutorial VLSI flow using one of the Chipyard examples, go to :ref:`tutorial` and follow the instructions.

* If you intend to build a chip using one of the vanilla Chipyard examples, go to :ref:`build-a-chip` and follow the instructions.

  
Getting Help
------------

If you have a question about Chipyard that isn't answered by the existing
documentation, feel free to ask for help on the
`Chipyard Google Group <https://groups.google.com/forum/#!forum/chipyard>`_.

Table of Contents
-----------------

.. toctree::
   :maxdepth: 3
   :numbered:

   Chipyard-Basics/index

   Simulation/index

   Generators/index

   Tools/index

   VLSI/index

   Customization/index

   Software/index

   Advanced-Concepts/index

   TileLink-Diplomacy-Reference/index


Indices and tables
==================

* :ref:`genindex`
* :ref:`modindex`
* :ref:`search`
