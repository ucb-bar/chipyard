.. _checkpointing:

Architectural Checkpoints
=========================

Chipyard supports generating architectural checkpoints using Spike.
These checkpoints contain a snapshot of the architectural state of a RISC-V SoC at some point in the execution of a program.
The checkpoints include the contents of cacheable memory, core architectural registers, and core CSRs.
RTL simulations of SoCs can resume execution from checkpoints after restoring the architectural state.

.. note::
   Currently, only checkpoints of single-core systems are supported

Generating Checkpoints
------------------------

``scripts/generate-ckpt.sh`` is a script that runs spike with the right commands to generate an architectural checkpoint
``scripts/generate-ckpt.sh -h`` lists options for checkpoint generation.

Example: run the ``hello.riscv`` binary for 1000 instructions before generating a checkpoint.
This should produce a directory named ``hello.riscv.0x80000000.1000.loadarch``

.. code::

   scripts/generate-ckpt.sh -b tests/hello.riscv -i 1000


Loading Checkpoints in RTL Simulation
--------------------------------------

Checkpoints can be loaded in RTL simulations with the ``LOADARCH`` flag.
The target config **MUST** use dmi-based bringup (as opposed to the default TSI-based bringup), and support fast ``LOADMEM``.
The target config should also match the architectural configuration of however spike was configured when generating the checkpoint.

.. code::

   cd sims/vcs
   make CONFIG=dmiRocketConfig run-binary LOADARCH=../../hello.riscv.0x80000000.1000.loadarch
