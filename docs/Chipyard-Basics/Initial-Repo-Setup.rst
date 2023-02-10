Initial Repository Setup
========================================================

Prerequisites
-------------------------------------------

Chipyard is developed and tested on Linux-based systems.

.. Warning:: It is possible to use this on macOS or other BSD-based systems, although GNU tools will need to be installed;
    it is also recommended to install the RISC-V toolchain from ``brew``.

.. Warning:: Working under Windows is not recommended.

Running on AWS EC2 with FireSim
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

If you plan on using Chipyard alongside FireSim on AWS EC2 instances, you should refer to the :fsim_doc:`FireSim documentation <>`.
Specifically, you should follow the :fsim_doc:`Initial Setup/Installation <Initial-Setup/index.html>`
section of the docs up until :fsim_doc:`Setting up the FireSim Repo <Initial-Setup/Setting-up-your-Manager-Instance.html#setting-up-the-firesim-repo>`.
At that point, instead of cloning FireSim you can clone Chipyard by following :ref:`Chipyard-Basics/Initial-Repo-Setup:Setting up the Chipyard Repo`.

Default Requirements Installation
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

In Chipyard, we use the `Conda <https://docs.conda.io/en/latest/>`__ package manager to help manage system dependencies.
Conda allows users to create an "environment" that holds system dependencies like ``make``, ``gcc``, etc.

.. Note:: Chipyard can also run on systems without a Conda installation. However, users on these systems must manually install toolchains and dependencies.

First, Chipyard requires Conda to be installed on the system.
Please refer to the `Conda installation instructions <https://github.com/conda-forge/miniforge/#download>`__ on how to install Conda with the **Miniforge** installer.
Afterwards, verify that Conda is a sufficient version (we test on version 4.12.0 but higher is most likely fine).

.. Note:: If you have installed conda separately from this documentation (i.e. from miniconda or full Anaconda), please ensure you follow https://conda-forge.org/docs/user/introduction.html#how-can-i-install-packages-from-conda-forge to use ``conda-forge`` packages without any issues.

.. code-block:: shell

    conda --version # must be version 4.12.0 or higher

After Conda is installed and is on your ``PATH``, we need to install a version of ``git`` to initially checkout the repository.
For this you can use the system package manager like ``yum`` or ``apt`` to install ``git``.
This ``git`` is only used to first checkout the repository, we will later install a newer version of ``git`` with Conda.

Finally we need to install ``conda-lock`` into the ``base`` conda environment.
This is done by the following:

.. code-block:: shell

    conda install -n base conda-lock
    conda activate base

.. Note:: We also recommended switching to `libmamba <https://www.anaconda.com/blog/a-faster-conda-for-a-growing-community>`__ for much faster dependency solving.

Setting up the Chipyard Repo
-------------------------------------------

Start by checking out the proper Chipyard version. Run:

.. parsed-literal::

    git clone https://github.com/ucb-bar/chipyard.git
    cd chipyard
    # checkout latest official chipyard release
    # note: this may not be the latest release if the documentation version != "stable"
    git checkout |version|

Next run the following script to fully setup Chipyard with a specific toolchain.
There are two toolchains, one for normal RISC-V programs called ``riscv-tools`` which is the one needed for most Chipyard use-cases, and another for Hwacha called ``esp-tools``.
Run the following script based off which compiler you would like to use.

.. Note:: Prior versions of Chipyard recommended ``esp-tools`` for Gemmini development. Gemmini should now be used with the standard ``riscv-tools``.

.. Warning:: The following script will complete a "full" installation of Chipyard which may take a long time depending on the system.
    Ensure that this script completes fully (no interruptions) before continuing on.

.. code-block:: shell

    ./build-setup.sh riscv-tools # or esp-tools

This script wraps around the conda environment initialization process, initializes all submodules (with the ``init-submodules-no-riscv-tools.sh`` script), installs a toolchain, and runs other setups.
See ``./build-setup.sh --help`` for more details on what this does and how to disable parts of the setup.

.. Warning:: Using ``git`` directly will try to initialize all submodules; this is not recommended unless you expressly desire this behavior.

.. Note:: By default, the ``build-setup.sh`` script installs extra toolchain utilities (RISC-V tests, PK, Spike, etc) to ``$CONDA_PREFIX/<toolchain-type>``. Thus, if you uninstall the compiler using ``conda remove`` these utilities/tests will also have to be re-installed/built.

.. Note:: If you already have a working conda environment setup, separate Chipyard clones can use that pre-used environment in combination with running the aforementioned scripts yourself (``init-submodules...``, ``build-toolchain...``, etc).

.. Note:: If you are a power user and would like to build your own compiler/toolchain, you can refer to the https://github.com/ucb-bar/riscv-tools-feedstock and https://github.com/ucb-bar/esp-tools-feedstock repositories (submoduled in the ``toolchains/*`` directories) on how to build the compiler yourself.

By running the following command you should see a environment listed with the path ``$CHIPYARD_DIRECTORY/.conda-env``.

.. code-block:: shell

    conda env list

.. Note:: Refer to FireSim's :fsim_doc:`Conda documentation <Advanced-Usage/Conda.html>` on more information
    on how to use Conda and some of its benefits.

Sourcing ``env.sh``
-------------------

Once setup is complete, an emitted ``env.sh`` file should exist in the top-level repository.
This file activates the conda environment created in ``build-setup.sh`` and sets up necessary environment variables needed for future Chipyard steps (needed for the ``make`` system to work properly).
Once the script is run, the ``PATH``, ``RISCV``, and ``LD_LIBRARY_PATH`` environment variables will be set properly for the toolchain requested.
You can source this file in your ``.bashrc`` or equivalent environment setup file to get the proper variables, or directly include it in your current environment:

.. Note:: If you are on a Mac or a RHEL/CentOS-based Linux distribution, you must deactivate the base conda environment with ``conda deactivate`` first before proceeding. You may also choose to keep it deactivated by default with ``conda config --set auto_activate_base false``. See this `issue <https://github.com/conda/conda/issues/9392>`__ for more details.

.. code-block:: shell

    source ./env.sh

.. Warning:: This ``env.sh`` file should always be sourced before running any ``make`` commands.

.. Note:: You can deactivate/activate a compiler/toolchain (but keep it installed) by running ``source $CONDA_PREFIX/etc/conda/deactivate.d/deactivate-${PKG_NAME}.sh`` or ``$CONDA_PREFIX/etc/conda/activate.d/activate-${PKG_NAME}.sh`` (``PKG_NAME`` for example is ``ucb-bar-riscv-tools``). This will modify the aforementioned 3 environment variables.

.. Warning:: ``env.sh`` files are generated per-Chipyard repository.
    In a multi-Chipyard repository setup, it is possible to source multiple ``env.sh`` files (in any order).
    However, it is recommended that the final ``env.sh`` file sourced is the ``env.sh`` located in the
    Chipyard repo that you expect to run ``make`` commands in.

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
For this reason, upgrading between Chipyard versions is **not** as trivial as just running ``git submodule update --recursive``. This will result in recursive cloning of large submodules that are not necessarily used within your specific Chipyard environments.
Furthermore, it will not resolve the status of stale state generated files which may not be compatible between release versions.


If you are an advanced git user, an alternative approach to a fresh repository clone may be to run ``git clean -dfx``, and then run the standard Chipyard setup sequence.
This approach is dangerous, and **not-recommended** for users who are not deeply familiar with git, since it "blows up" the repository state and removes all untracked and modified files without warning.
Hence, if you were working on custom un-committed changes, you would lose them.

If you would still like to try to perform an in-place manual version upgrade (**not-recommended**), we recommend at least trying to resolve stale state in the following areas:

* Delete stale ``target`` directories generated by sbt.

* Re-generate generated scripts and source files (for example, ``env.sh``)

* Re-generating/deleting target software state (Linux kernel binaries, Linux images) within FireMarshal


This is by no means a comprehensive list of potential stale state within Chipyard.
Hence, as mentioned earlier, the recommended method for a Chipyard version upgrade is a fresh clone (or a merge, and then a fresh clone).
