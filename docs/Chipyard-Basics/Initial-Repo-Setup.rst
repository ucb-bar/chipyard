Initial Repository Setup
========================================================

Prerequisites
-------------------------------------------

Chipyard is developed and tested on x86 Linux-based systems.

.. Warning:: It is possible to use this on macOS or other BSD-based systems, although GNU tools will need to be installed;
    it is also recommended to install the RISC-V toolchain from ``brew``. Builds of Chipyard on ARM Linux-based systems have not been tested and may experience dependency issues.

.. Warning:: If using Windows, it is recommended that you use `Windows Subsystem for Linux (WSL) <https://learn.microsoft.com/en-us/windows/wsl/>`__.

Running on AWS EC2 with FireSim
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

If you plan on using Chipyard alongside FireSim on AWS EC2 instances, you should refer to the :fsim_doc:`FireSim documentation <>`.
If you are a new user, start with the :fsim_doc:`FireSim AWS first time setup <AWS-EC2-F2-Initial-Setup/index.html>`. Otherwise, follow the :fsim_doc:`Initial Setup/Installation <Getting-Started-Guides/AWS-EC2-F2-Getting-Started/index.html>`
section of the docs up until :fsim_doc:`Setting up the FireSim Repo <Getting-Started-Guides/AWS-EC2-F2-Getting-Started/Setting-Up-The-FireSim-Repo.html#setting-up-the-firesim-repo>`.
At that point, instead of cloning FireSim you can clone Chipyard by following :ref:`Chipyard-Basics/Initial-Repo-Setup:Setting up the Chipyard Repo`.

Default Requirements Installation
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Chipyard uses `Pixi <https://pixi.sh/>`__ to install its compilers, tools, and Python dependencies.
Install Git and Python 3 with your system package manager, then install Pixi 0.82.1 or newer.
If Pixi is already installed, run ``pixi self-update`` to update it.
The repository also provides ``./scripts/install-pixi.sh`` to install the tested Pixi version
into ``~/.pixi/bin`` after cloning; add that directory to your ``PATH``.

Setting up the Chipyard Repo
-------------------------------------------

Start by cloning Chipyard. The `main` branch will be the latest stable version. Run:

.. code-block:: shell

    git clone https://github.com/ucb-bar/chipyard.git
    cd chipyard
.. # checkout latest official chipyard release
.. # note: this may not be the latest release if the documentation version != "stable"
.. git checkout |version| no longer needed as main will be latest version

Next run the following script to fully setup Chipyard with the ``riscv-tools`` toolchain.

.. Warning:: The following script will complete a "full" installation of Chipyard which may take a long time depending on the system.
    Ensure that this script completes fully (no interruptions) before continuing on. User can use the ``--skip`` or ``-s`` flag to skip steps:

    ``-s 1`` skips initializing the Pixi environment

    ``-s 2`` skips initializing Chipyard submodules

    ``-s 3`` skips initializing toolchain collateral (Spike, PK, tests, libgloss)

    ``-s 4`` skips initializing ctags

    ``-s 5`` skips pre-compiling Chipyard Scala sources

    ``-s 6`` skips initializing FireSim

    ``-s 7`` skips pre-compiling FireSim sources

    ``-s 8`` skips initializing FireMarshal

    ``-s 9`` skips pre-compiling FireMarshal default buildroot Linux sources

    ``-s 10`` skips installing CIRCT

    ``-s 11`` skips running repository clean-up

.. code-block:: shell

    ./build-setup.sh riscv-tools

This script wraps around Pixi environment installation, initializes all submodules (with the ``init-submodules-no-riscv-tools.sh`` script), installs a toolchain, and runs other setups.
See ``./build-setup.sh --help`` for more details on what this does and how to disable parts of the setup.

.. Warning:: Using ``git`` directly will try to initialize all submodules; this is not recommended unless you expressly desire this behavior.

.. Note:: Setup installs the ``default`` environment in ``pixi-reqs/.pixi/envs/default``.
    ``--use-lean-pixi`` selects ``lean`` and skips FireSim and FireMarshal.
    The old ``--use-lean-conda`` and ``--skip-conda`` flags remain aliases.
    Toolchain collateral is installed under ``$CONDA_PREFIX/riscv-tools``;
    Pixi exports ``CONDA_PREFIX`` for compatibility with existing build scripts.

.. Note:: Setup keeps the pinned sysroot on newer hosts. On an older host, it selects the
    newest published conda-forge sysroot that does not exceed the host glibc, and refreshes
    the lockfile when the manifest changes. It does not pin an unpublished host version
    such as glibc 2.31 or 2.35. A compiler sysroot and the host runtime glibc are separate
    requirements; dependency solving must also satisfy the manifest's virtual glibc baseline.

.. Note for power users: Chipyard includes internal scripts that can selectively initialize generator submodules. The default ``./build-setup.sh`` initializes all standard generator submodules and is the recommended path.

.. Note:: If you are a power user and would like to build your own compiler/toolchain, you can refer to the https://github.com/ucb-bar/riscv-tools-feedstock repository (submoduled in the ``toolchains/*`` directories) on how to build the compiler yourself.

To inspect installed packages, run:

.. code-block:: shell

    pixi list --manifest-path pixi-reqs/pixi.toml --environment default

Sourcing ``env.sh``
-------------------

Once setup is complete, an emitted ``env.sh`` file should exist in the top-level repository.
This file activates the Pixi environment created in ``build-setup.sh`` and sets up necessary environment variables needed for future Chipyard steps (needed for the ``make`` system to work properly).
Once the script is run, the ``PATH``, ``RISCV``, and ``LD_LIBRARY_PATH`` environment variables will be set properly for the toolchain requested.
You can source this file in your ``.bashrc`` or equivalent environment setup file to get the proper variables, or directly include it in your current environment:

.. code-block:: shell

    source ./env.sh

.. Warning:: This ``env.sh`` file should always be sourced before running any ``make`` commands.

Activation changes your current Bash or Zsh shell. Use ``source env.sh``;
executing ``./env.sh`` cannot modify a parent shell. Repeated sourcing is safe.
Deactivate and reactivate without opening another shell:

.. code-block:: shell

    pixi deactivate          # also available as chipyard_deactivate
    source ./env.sh
    pixi activate lean       # switch to another installed environment

After sourcing, add packages from any directory with:

.. code-block:: shell

    pixi add htop
    pixi add --pypi rich

Packages added without ``--feature`` enter the shared default feature, inherited by all
Chipyard environments. The shell helper installs and refreshes the active environment,
including ``lean``, and reports installation failures. Use ``--no-install`` to defer installation
or an explicit ``--manifest-path`` when working on a different Pixi workspace.
FireSim library setup uses the same complete ``default`` environment and its added packages.
Its Fab Classic and Paramiko dependencies are locked in Pixi rather than replaced by pip during setup.

.. Warning:: ``env.sh`` files are generated per-Chipyard repository.
    In a multi-Chipyard repository setup, it is possible to source multiple ``env.sh`` files (in any order).
    However, it is recommended that the final ``env.sh`` file sourced is the ``env.sh`` located in the
    Chipyard repo that you expect to run ``make`` commands in.

.. DEPRECATED: Pre-built Docker Image
.. -------------------------------------------

.. An alternative to setting up the Chipyard repository locally is to pull the pre-built Docker image from Docker Hub. The image comes with all dependencies installed, Chipyard cloned, and toolchains initialized. This image sets up baseline Chipyard (not including FireMarshal, FireSim, and Hammer initializations). Each image comes with a tag that corresponds to the version of Chipyard cloned/set-up in that image. Not including a tag during the pull will pull the image with the latest version of Chipyard.
.. First, pull the Docker image. Run:

.. .. code-block:: shell

..     sudo docker pull ucbbar/chipyard-image:<TAG>

.. To run the Docker container in an interactive shell, run:

.. .. code-block:: shell

..     sudo docker run -it ucbbar/chipyard-image bash

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

Tips on navigating Chipyard
-------------------------------------------

To aid in navigating Chipyard and its various generators, consider installing a Scala LSP, such as `Scala Metals <https://scalameta.org/metals/>`__, then running the ``Metals: Import build`` command from the VS Code command palette, then selecting ``sbt`` as the build system. If you run into errors, check the status with ``Metals: Run doctor`` from the VS Code command palette.

IntelliJ IDEA also has native support for Scala and can help with navigating the Scala codebase.

Additionally, most references can also be located via `grep` and/or the VS Code search function.
