Berkeley Out-of-Order Machine (BOOM)
==============================================

.. image:: ../_static/images/boom-pipeline-detailed.png

The `Berkeley Out-of-Order Machine (BOOM) <https://boom-core.org/>`__ is a synthesizable and parameterizable open source RV64GC RISC-V core written in the Chisel hardware construction language.
It serves as a drop-in replacement to the Rocket core given by Rocket Chip (replaces the RocketTile with a BoomTile).
BOOM is heavily inspired by the MIPS R10k and the Alpha 21264 out-of-order processors.
Like the R10k and the 21264, BOOM is a unified physical register file design (also known as “explicit register renaming”).
Conceptually, BOOM is broken up into 10 stages: Fetch, Decode, Register Rename, Dispatch, Issue, Register Read, Execute, Memory, Writeback and Commit.
However, many of those stages are combined in the current implementation, yielding seven stages: Fetch, Decode/Rename, Rename/Dispatch, Issue/RegisterRead, Execute, Memory and Writeback (Commit occurs asynchronously, so it is not counted as part of the “pipeline”).

Additional information about the BOOM micro-architecture can be found in the `BOOM documentation pages <https://docs.boom-core.org/>`__.
