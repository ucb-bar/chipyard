Shuttle: A Rocket-based Superscalar In-order RISC-V Core
========================================================

Shuttle is a Rocket-based superscalar in-order RISC-V core, supporting the base RV64IMAFDCB instruction set with supervisor and user-mode.
Shuttle is a 7-stage core that can be configured to be dual, three, or quad-issue, although dual-issue is the most sensible design point.
Shuttle is *not* designed to meet any power, performance, or area targets.
It exists purely as a demonstrative example of another RISC-V CPU design point.

The superscalar microarchitecture presents the most advantages for 1) floating-point kernels and 2) RoCC accelerator kernels, as scalar control code can execute concurrently with floating point or RoCC instructions, maintaining high utilization of those units.

Shuttle is tape-out proven, and has similar physical design complexity as Rocket.

## Versioning

Only the latest version will be maintained.

* **1.0**: Initial 6-stage RV64GC Release
* **1.1**: Support integration with vector units
* **1.2**: Support B-extension (Zba/Zbb/Zbs)
* **2.0**: 7-stage pipeline