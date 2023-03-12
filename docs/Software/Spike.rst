The RISC-V ISA Simulator (Spike)
=================================

Spike is the golden reference functional RISC-V ISA C++ sofware simulator.
It provides full system emulation or proxied emulation with `HTIF/FESVR <https://github.com/riscv/riscv-isa-sim/tree/master/fesvr>`__.
It serves as a starting point for running software on a RISC-V target.
Here is a highlight of some of Spikes main features:

* Multiple ISAs: RV32IMAFDQCV extensions
* Multiple memory models: Weak Memory Ordering (WMO) and Total Store Ordering (TSO)
* Privileged Spec: Machine, Supervisor, User modes (v1.11)
* Debug Spec
* Single-step debugging with support for viewing memory/register contents
* Multiple CPU support
* JTAG support
* Highly extensible (add and test new instructions)

In most cases, software development for a Chipyard target will begin with functional simulation using Spike
(usually with the addition of custom Spike models for custom accelerator functions), and only later move on to
full cycle-accurate simulation using software RTL simulators or FireSim.

Spike comes pre-packaged in the RISC-V toolchain and is available on the path as ``spike``.
More information can be found in the `Spike repository <https://github.com/riscv/riscv-isa-sim>`__.

Spike-as-a-Tile
-----------------

Chipyard contains experimental support for simulating a Spike processor model with the uncore, similar to a virtual-platform.
In this configuration, Spike is cache-coherent, and communicates with the uncore through a C++ TileLink private cache model.

.. code-block:: shell

    make CONFIG=SpikeConfig run-binary BINARY=hello.riscv


