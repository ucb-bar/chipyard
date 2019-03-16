Open Source Simulators
==============================

Verilator
-----------------------
Verilator is an open-source LGPL-Licensed simulator maintained by `Veripool <https://www.veripool.org/>`__
The ReBAR framework can download, build, and execute simulations using Verilator.

To run a simulation using verilator, perform the following steps:

To compile the example design, run make in the ``sims/verisim`` directory.
This will elaborate the DefaultExampleConfig in the example project.

An executable called simulator-example-DefaultExampleConfig will be produced.
This executable is a simulator that has been compiled based on the design that was built.
You can then use this executable to run any compatible RV64 code. For instance,
to run one of the riscv-tools assembly tests.

::
    ./simulator-example-DefaultExampleConfig $RISCV/riscv64-unknown-elf/share/riscv-tests/isa/rv64ui-p-simple

If you later create your own project, you can use environment variables to
build an alternate configuration.

::
    make PROJECT=yourproject CONFIG=YourConfig
    ./simulator-yourproject-YourConfig ...


If you would like to extract waveforms from the simulation, run the command ``make debug`` instead of just ``make``. This will generate a vcd file (vcd is a standard waveform representation file format) that can be loaded to any common waveform viewer. An open-source vcd-capable waveform viewer is `GTKWave <http://gtkwave.sourceforge.net/>__ 



