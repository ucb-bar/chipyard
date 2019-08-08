Initial Repository Setup
========================================================

Checking out the sources
------------------------

After cloning this repo, you will need to initialize all of the submodules.

.. code-block:: shell

    git clone https://github.com/ucb-bar/chipyard.git
    cd chipyard
    ./scripts/init-submodules-no-riscv-tools.sh

Building a Toolchain
------------------------

The `toolchains` directory contains toolchains that include a cross-compiler toolchain, frontend server, and proxy kernel, which you will need in order to compile code to RISC-V instructions and run them on your design.
Currently there are two toolchains, one for normal RISC-V programs, and another for Hwacha (``esp-tools``).
There are detailed instructions at https://github.com/riscv/riscv-tools to install the ``riscv-tools`` toolchain, however, the instructions are similar for the Hwacha ``esp-tools`` toolchain.
But to get a basic installation, just the following steps are necessary.

.. code-block:: shell

    ./scripts/build-toolchains.sh riscv # for a normal risc-v toolchain

    # OR

    ./scripts/build-toolchains.sh esp-tools # for a modified risc-v toolchain with Hwacha vector instructions

Once the script is run, a ``env.sh`` file is emitted at sets the ``PATH``, ``RISCV``, and ``LD_LIBRARY_PATH`` environment variables.
You can put this in your ``.bashrc`` or equivalent environment setup file to get the proper variables.
These variables need to be set for the make system to work properly.
