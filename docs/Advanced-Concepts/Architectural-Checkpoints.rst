.. _checkpointing:

Architectural Checkpoints
=========================

Chipyard supports generating architectural checkpoints using Spike.
These checkpoints contain a snapshot of the architectural state of a RISC-V SoC at some point in the execution of a program.
The checkpoints include the contents of cacheable memory, core architectural registers, and core CSRs.
RTL simulations of SoCs can resume execution from checkpoints after restoring the architectural state.

.. note::
   Currently, only checkpoints of single-core systems are supported.

Generating Checkpoints
------------------------

``scripts/generate-ckpt.sh`` is a script that runs Spike with the right commands to generate an architectural checkpoint.
``scripts/generate-ckpt.sh -h`` lists options for checkpoint generation.

Example: run the ``hello.riscv`` binary for 1000 instructions before generating a checkpoint.
This should produce a directory named ``hello.riscv.*.loadarch``

.. code::

   scripts/generate-ckpt.sh -b tests/hello.riscv -i 1000


Loading Checkpoints in RTL Simulation
--------------------------------------

Checkpoints can be loaded in RTL simulations with the ``LOADARCH`` flag.
The target config needs the following properties:

- **MUST** use DMI-based bringup (as opposed to the default TSI-based bringup)
- **MUST** support fast ``LOADMEM``
- Should match the architectural configuration of however Spike was configured when generating the checkpoint (i.e. same ISA, no PMPs, etc).

.. code::

   cd sims/vcs
   make CONFIG=dmiRocketConfig run-binary LOADARCH=../../hello.riscv.*.loadarch

Checkpointing Linux Binaries
----------------------------

Checkpoints can be used to run Linux binaries with the following caveats:

- The binary must only use the HTIF console and should be non-interactive (i.e no stdin available)
- The target config must be built without a serial device (i.e. the Rocket Chip Blocks UART can't be used)
- The binary must only use an initramfs (i.e. no block device)
- The target config must be built without a block device (i.e. the IceBlk block device can't be used).
- The binary size must be smaller than the size of the target configs memory region (for example if FireMarshal's ``rootfs-size`` is 1GB, and OpenSBI is 350KB then you must have at least 1G + 350KB of space)

This means that you most likely need to do the following:

- By default Spike has a default UART device that is used during most Linux boot's.
  This can be bypassed by creating a DTS without a serial device then passing it to the ``generate-ckpt.sh`` script.
  You can copy the DTS of the design you want to checkpoint into - located in Chipyards ``sims/<simulator>/generated-src/`` - and modify it to pass to the checkpointing script (needs to be stripped down of extra devices and nodes).
  An example of a config made for checkpointing is ``dmiCospikeCheckpointingRocketConfig`` or ``dmiCheckpointingSpikeUltraFastConfig``.
- Additionally, you need to change your Linux config in FireMarshal to default to only use HTIF during OpenSBI and force Linux to use the OpenSBI HTIF console.
  This can be done by the following in the ``linux-config``: changing to ``CONFIG_CMDLINE="console=hvc0 earlycon=sbi"``, adding ``CONFIG_RISCV_SBI_V01=y``, adding ``CONFIG_HVC_RISCV_SBI=y``, and adding ``CONFIG_SERIAL_EARLYCON_RISCV_SBI=y``.
  An example workload with these changes can be found at ``<firemarshal>/example-workloads/br-base-htif-only-serial.yaml``.
