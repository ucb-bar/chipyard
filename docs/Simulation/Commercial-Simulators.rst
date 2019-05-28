Commericial Software RTL Simulators
==============================

VCS
-----------------------

`VCS <https://www.synopsys.com/verification/simulation/vcs.html>`__ is a commercial RTL simulator developed by Synopsys.
It requires commerical licenses.
The REBAR framework can compile and execute simulations using VCS.
VCS simulation will generally compile faster than Verilator simulations.

To run a simulation using VCS, perform the following steps:

Make sure that the VCS simulator is on your ``PATH``.

To compile the example design, run make in the ``sims/vsim`` directory.
This will elaborate the ``DefaultRocketConfig`` in the example project.

An executable called ``simulator-example-DefaultRocketConfig`` will be produced.
This executable is a simulator that has been compiled based on the design that was built.
You can then use this executable to run any compatible RV64 code.
For instance, to run one of the riscv-tools assembly tests.

.. code-block:: shell

    ./simulator-example-DefaultRocketConfig $RISCV/riscv64-unknown-elf/share/riscv-tests/isa/rv64ui-p-simple

If you later create your own project, you can use environment variables to build an alternate configuration.

.. code-block:: shell

    make SUB_PROJECT=yourproject
    ./simulator-<yourproject>-<yourconfig> ...

If you would like to extract waveforms from the simulation, run the command ``make debug`` instead of just ``make``.
This will generate a vpd file (this is a proprietry waveform representation format used by Synopsys) that can be loaded to vpd-supported waveform viewers.
If you have Synopsys licenses, we recommend using the DVE waveform viewer.

Please refer to :ref:`Running A Simulation` for a step by step tutorial on how to get a simulator up and running.
