The RISC-V ISA Simulator (Spike)
=================================

Spike is the golden reference functional RISC-V ISA simulator.
It provides full system emulation or proxied emulation with the HTIF/FESVR.
It serves as a starting point for running software on a RISC-V target.
Here is a highlight of some of Spikes main features:

* Multiple ISAs: RV32IMAFDQCV extensions
* Multiple memory models: Weak Memory Ordering (WMO) and Total Store Ordering (TSO)
* Priveleged Spec: Machine, Supervisor, User modes (v1.11)
* Debug Spec
* Single-step debugging with support for viewing memory/register contents
* Multiple CPU support
* JTAG support
* Highly extensible (add and test new instructions)
* And much more!

Spike comes pre-packaged in the RISC-V toolchain.
