First Verilog Build
===================

This tutorial guides you through setting up Chipyard for the first time and building Verilog (specifically a Rocket core SoC).

First navigate to the Chipyard directory.
Ensure that ``$CHIPYARD_DIR`` is set to the top-level directory.

.. code-block:: shell

    cd $CHIPYARD_DIR

.. only:: replace-code-above

    cd $CHIPYARD_DIR
    export MAKEFLAGS="-j32"

Next, run the Chipyard setup script.

.. code-block:: shell

    ./build-setup.sh

.. only:: replace-code-above

    ./build-setup.sh -f -v

Chipyard should be setup.
Next, source the ``env.sh`` script to setup your ``PATH`` and other environment variables.

.. code-block:: shell

    source env.sh

Next, let's build some Verilog specifically the default SoC which includes a Rocket in-order core.

.. code-block:: shell

    cd sims/verilator
    make verilog

Next, let's build the Verilator simulator binary of that SoC that can run a RISC-V binary (like a "Hello, World" program).

.. code-block:: shell

    make

Finally, look at the ``generated-src`` directory which holds the Verilog sources and the ``simulator-*`` file which is the compiled Verilator binary.

.. code-block:: shell

    ls -alh generated-src
    ls -alh simulator*
