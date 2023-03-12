Sodor Core
====================================

`Sodor <https://github.com/ucb-bar/riscv-sodor>`__ is a collection of 5 simple RV32MI cores designed for educational purpose.
The `Sodor core` is wrapped in an tile during generation so it can be used as a component within the `Rocket Chip SoC generator`.
The cores contain a small scratchpad memory to which the program are loaded through a TileLink slave port, and the cores **DO NOT**
support external memory.

The five available cores and their corresponding generator configuration are:

* 1-stage (essentially an ISA simulator) - ``Sodor1StageConfig``
* 2-stage (demonstrates pipelining in Chisel) - ``Sodor2StageConfig``
* 3-stage (uses sequential memory; supports both Harvard (``Sodor3StageConfig``) and Princeton (``Sodor3StageSinglePortConfig``) versions)
* 5-stage (can toggle between fully bypassed or fully interlocked) - ``Sodor5StageConfig``
* "bus"-based micro-coded implementation - ``SodorUCodeConfig``

For more information, please refer to the `GitHub repository <https://github.com/ucb-bar/riscv-sodor>`__.
