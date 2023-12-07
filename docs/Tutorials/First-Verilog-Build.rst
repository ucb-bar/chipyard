First Verilog Build
===================

This tutorial guides you through setting up Chipyard for the first time and building Verilog (specifically a Rocket core SoC).

.. code-block:: shell

    cd $CHIPYARD_DIR

.. only:: replace-code-above

    cd $CHIPYARD_DIR
    export MAKEFLAGS="-j32"

.. code-block:: shell

    ./build-setup.sh

.. only:: replace-code-above

    ./build-setup.sh -f -v

.. code-block:: shell

    source env.sh

.. code-block:: shell

    cd sims/verilator
    make verilog

.. code-block:: shell

    make

.. code-block:: shell

    ls -alh generated-src
    ls -alh simulator*
