Shuttle RISC-V Core
===================

Shuttle is a Rocket-based superscalar in-order RISC-V core, supporting the base RV64IMAFDC instruction set with supervisor and user-mode. Shuttle is a 6-stage core that can be configured to be dual, three, or quad-issue, although dual-issue is the most sensible design point. Shuttle is not designed to meet any power, performance, or area targets. It exists purely as a demonstrative example of another RISC-V CPU design point.

The superscalar microarchitecture presents the most advantages for 1) floating-point kernels and 2) RoCC accelerator kernels, as scalar control code can execute concurrently with floating point or RoCC instructions, maintaining high utilization of those units.

Shuttle is tape-out proven, and has similar physical design complexity as Rocket.
