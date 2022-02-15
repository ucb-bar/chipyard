Initial Repository Setup
========================================================

Requirements
-------------------------------------------

Chipyard is developed and tested on Linux-based systems.

.. Warning:: It is possible to use this on macOS or other BSD-based systems, although GNU tools will need to be installed; it is also recommended to install the RISC-V toolchain from ``brew``.

.. Warning:: Working under Windows is not recommended.


In CentOS-based platforms, we recommend installing the following dependencies:

.. include:: /../scripts/centos-req.sh
   :code: bash

In Ubuntu/Debian-based platforms (Ubuntu), we recommend installing the following dependencies.
These dependencies were written based on Ubuntu 16.04 LTS and 18.04 LTS - If they don't work for you, you can try out the Docker image (:ref:`Chipyard-Basics/Initial-Repo-Setup:Pre-built Docker Image`) before manually installing or removing dependencies:

.. include:: /../scripts/ubuntu-req.sh
   :code: bash

.. Note:: When running on an Amazon Web Services EC2 FPGA-development instance (for FireSim), FireSim includes a machine setup script that will install all of the aforementioned dependencies (and some additional ones).

Setting up the Chipyard Repo
-------------------------------------------

Start by fetching Chipyard's sources. Run:

.. parsed-literal::

    git clone https://github.com/ucb-bar/chipyard.git
    cd chipyard
    # checkout latest official chipyard release
    # note: this may not be the latest release if the documentation version != "stable"
    git checkout |version|
    ./scripts/init-submodules-no-riscv-tools.sh

This will initialize and checkout all of the necessary git submodules.
This will also validate that you are on a tagged branch, otherwise it will prompt for confirmation.

When updating Chipyard to a new version, you will also want to rerun this script to update the submodules.
Using git directly will try to initialize all submodules; this is not recommended unless you expressly desire this behavior.

.. _build-toolchains:

Building a Toolchain
------------------------

The `toolchains` directory contains toolchains that include a cross-compiler toolchain, frontend server, and proxy kernel, which you will need in order to compile code to RISC-V instructions and run them on your design.
Currently there are two toolchains, one for normal RISC-V programs, and another for Hwacha (``esp-tools``).
For custom installations, Each tool within the toolchains contains individual installation procedures within its README file.
To get a basic installation (which is the only thing needed for most Chipyard use-cases), just the following steps are necessary.
This will take about 20-30 minutes. You can expedite the process by setting a ``make`` environment variable to use parallel cores: ``export MAKEFLAGS=-j8``.

.. code-block:: shell

    ./scripts/build-toolchains.sh riscv-tools # for a normal risc-v toolchain

.. Note:: If you are planning to use the Hwacha vector unit, or other RoCC-based accelerators, you should build the esp-tools toolchain by adding the ``esp-tools`` argument to the script above.
  If you are running on an Amazon Web Services EC2 instance, intending to use FireSim, you can also use the ``--ec2fast`` flag for an expedited installation of a pre-compiled toolchain.

Once the script is run, a ``env.sh`` file is emitted that sets the ``PATH``, ``RISCV``, and ``LD_LIBRARY_PATH`` environment variables.
You can put this in your ``.bashrc`` or equivalent environment setup file to get the proper variables, or directly include it in your current environment:

.. code-block:: shell

    source ./env.sh

These variables need to be set for the ``make`` system to work properly.

Pre-built Docker Image
-------------------------------------------

An alternative to setting up the Chipyard repository locally is to pull the pre-built Docker image from Docker Hub. The image comes with all dependencies installed, Chipyard cloned, and toolchains initialized. This image sets up baseline Chipyard (not including FireMarshal, FireSim, and Hammer initializations). Each image comes with a tag that corresponds to the version of Chipyard cloned/set-up in that image. Not including a tag during the pull will pull the image with the latest version of Chipyard.
First, pull the Docker image. Run:

.. code-block:: shell

    sudo docker pull ucbbar/chipyard-image:<TAG>

To run the Docker container in an interactive shell, run:

.. code-block:: shell

    sudo docker run -it ucbbar/chipyard-image bash

What's Next?
-------------------------------------------

This depends on what you are planning to do with Chipyard.

* If you intend to run a simulation of one of the vanilla Chipyard examples, go to :ref:`sw-rtl-sim-intro` and follow the instructions.

* If you intend to run a simulation of a custom Chipyard SoC Configuration, go to :ref:`Simulation/Software-RTL-Simulation:Simulating A Custom Project` and follow the instructions.

* If you intend to run a full-system FireSim simulation, go to :ref:`firesim-sim-intro` and follow the instructions.

* If you intend to add a new accelerator, go to :ref:`customization` and follow the instructions.

* If you want to learn about the structure of Chipyard, go to :ref:`chipyard-components`.

* If you intend to change the generators (BOOM, Rocket, etc) themselves, see :ref:`generator-index`.

* If you intend to run a tutorial VLSI flow using one of the Chipyard examples, go to :ref:`tutorial` and follow the instructions.

* If you intend to build a chip using one of the vanilla Chipyard examples, go to :ref:`build-a-chip` and follow the instructions.

Upgrading Chipyard Release Versions
-------------------------------------------

In order to upgrade between Chipyard versions, we recommend using a fresh clone of the repository (or your fork, with the new release merged into it).


Chipyard is a complex framework that depends on a mix of build systems and scripts. Specifically, it relies on git submodules, on sbt build files, and on custom written bash scripts and generated files.
For this reason, upgrading between Chipyard versions is **not** as trivial as just running ``git submodule update -recursive``. This will result in recursive cloning of large submodules that are not necessarily used within your specific Chipyard environments. Furthermore, it will not resolve the status of stale state generated files which may not be compatible between release versions.


If you are an advanced git user, an alternative approach to a fresh repository clone may be to run ``git clean -dfx``, and then run the standard Chipyard setup sequence. This approach is dangerous, and **not-recommended** for users who are not deeply familiar with git, since it "blows up" the repository state and removes all untracked and modified files without warning. Hence, if you were working on custom un-committed changes, you would lose them.

If you would still like to try to perform an in-place manual version upgrade (**not-recommended**), we recommend at least trying to resolve stale state in the following areas:

* Delete stale ``target`` directories generated by sbt.

* Delete jar collateral generated by FIRRTL (``lib/firrtl.jar``)

* Re-generate generated scripts and source files (for example, ``env.sh``)

* Re-generating/deleting target software state (Linux kernel binaries, Linux images) within FireMarshal


This is by no means a comprehensive list of potential stale state within Chipyard. Hence, as mentioned earlier, the recommended method for a Chipyard version upgrade is a fresh clone (or a merge, and then a fresh clone).
