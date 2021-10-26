Debugging RTL
======================

While the packaged Chipyard configs and RTL have been tested to work,
users will typically want to build custom chips by adding their own
IP, or by modifying existing Chisel generators. Such changes might introduce
bugs. This section aims to run through a typical debugging flow
using Chipyard. We assume the user has a custom SoC configuration,
and is trying to verify functionality by running some software test.
We also assume the software has already been verified on a functional
simulator, such as Spike or QEMU. This section will focus on debugging
hardware.

Waveforms
---------------------------

The default software RTL simulators do not dump waveforms during execution.
To build simulators with wave dump capabilities use must use the ``debug``
make target. For example:

.. code-block:: shell

   make CONFIG=CustomConfig debug

The ``run-binary-debug`` rule will also automatically build a simulator,
run it on a custom binary, and generate a waveform. For example, to run a
test on ``helloworld.riscv``, use

.. code-block:: shell

   make CONFIG=CustomConfig run-binary-debug BINARY=helloworld.riscv

VCS and Verilator also support many additional flags. For example, specifying
the ``+vpdfilesize`` flag in VCS will treat the output file as a circular
buffer, saving disk space for long-running simulations. Refer to the VCS
and Verilator manuals for more information You may use the ``SIM_FLAGS``
make variable to set additional simulator flags:

.. code-block:: shell

   make CONFIG=CustomConfig run-binary-debug BINARY=linux.riscv SIM_FLAGS=+vpdfilesize=1024

.. note::
    In some cases where there is multiple simulator flags, you can write the ``SIM_FLAGS``
    like the following: ``SIM_FLAGS="+vpdfilesize=XYZ +some_other_flag=ABC"``.

Print Output
---------------------------

Both Rocket and BOOM can be configured with varying levels of print output.
For information see the Rocket core source code, or the BOOM `documentation
<https://docs.boom-core.org/en/latest/>`__ website. In addition, developers
may insert arbitrary printfs at arbitrary conditions within the Chisel generators.
See the Chisel documentation for information on this.

Once the cores have been configured with the desired print statements, the
``+verbose`` flag will cause the simulator to print the statements. The following
commands will all generate desired print statements:

.. code-block:: shell

   make CONFIG=CustomConfig run-binary-debug BINARY=helloworld.riscv

   # The below command does the same thing
   ./simv-CustomConfig-debug +verbose helloworld.riscv

Both cores can be configured to print out commit logs, which can then be compared
against a Spike commit log to verify correctness.

Basic tests
---------------------------
``riscv-tests`` includes basic ISA-level tests and basic benchmarks. These
are used in Chipyard CI, and should be the first step in verifying a chip's
functionality. The make rule is

.. code-block:: shell

   make CONFIG=CustomConfig run-asm-tests run-bmark-tests


Torture tests
---------------------------
The RISC-V torture utility generates random RISC-V assembly streams, compiles them,
runs them on both the Spike functional model and the SW simulator, and verifies
identical program behavior. The torture utility can also be configured to run
continuously for stress-testing. The torture utility exists within the ``tools``
directory. To run torture tests, run ``make`` in the simulation directories:

.. code-block:: shell

  make CONFIG=CustomConfig torture

To run overnight tests (repeated random tests), run

.. code-block:: shell

  make CONFIG=CustomConfig TORTURE_ONIGHT_OPTIONS=<overnight options> torture-overnight

You can find the overnight options in `overnight/src/main/scala/main.scala` in the torture repo.  

Firesim Debugging
---------------------------
Chisel printfs, asserts, Dromajo co-simulation, and waveform generation are also available in FireSim
FPGA-accelerated simulation. See the FireSim
`documentation <https://docs.fires.im/en/latest/>`__ for more detail.

