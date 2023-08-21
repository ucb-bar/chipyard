Using the Baremetal Integrated Development Environment
======================================================

Configuring ChipConfig
---------------------------

We will be using the default ExampleChipConfig generated from the Chipyard GUI.

The config will include a single Rocket core, a JTAG debug module, a UART periperhal device, a backing scratchpad, and serial TileLink connection to the simulation harness. For more detailed specification of this config, see ExampleChip Specification.

Using Chipyard Baremetal IDE
----------------------------

To compile a baremetal program, run the following command in the Chipyard base directory:

.. code-block:: shell

    source ./env.sh
    cd ./software/baremetal-ide/workspace
    make CHIP=examplechip USE_HTIF=1

The output should look something like:

.. code-block:: shell

    /home/tk/Desktop/chipyard-ide/.conda-env/riscv-tools/bin/../lib/gcc/riscv64-unknown-elf/12.2.0/../../../../riscv64-unknown-elf/bin/ld: warning: build/firmware.elf has a LOAD segment with RWX permissions
    riscv64-unknown-elf-size build/firmware.elf
       text    data     bss     dec     hex filename
      18700     264  255492  274456   43018 build/firmware.elf
    [Build] build/firmware.elf built for target "examplechip"

Simulating the Default Example
--------------------------------------

.. code-block:: shell

    cd ../../../
    cd ./sims/verilator/
    make run-binary CONFIG=ExampleChipConfig LOADMEM=1 BINARY=../../software/baremetal-ide/workspace/build/firmware.elf TIMEOUT_CYCLES=1000000

Follow the instructions from :ref:`simulation/Software-RTL-Simulation:Software RTL Simulation` for more detailed configurations.
