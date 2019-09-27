The RISC-V ISA Simulator (Spike)
=================================

Spike is the golden reference functional RISC-V ISA C++ sofware simulator.
It provides full system emulation or proxied emulation with the HTIF/FESVR.
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

Spike comes pre-packaged in the RISC-V toolchain and is available on the path as ``spike``.
More information can be found in the `Spike repository <https://github.com/riscv/riscv-isa-sim>`__.
