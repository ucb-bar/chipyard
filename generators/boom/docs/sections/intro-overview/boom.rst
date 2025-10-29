The Berkeley Out-of-Order Machine (BOOM)
========================================

.. figure:: /figures/boom-pipeline-detailed.png
    :scale: 25 %
    :align: center
    :alt: Detailed BOOM Pipeline

    Detailed BOOM Pipeline. \*'s denote where the core can be configured.

The Berkeley Out-of-Order Machine (BOOM) is heavily inspired by the MIPS R10000 [1]_ and the Alpha 21264 [2]_ out–of–order processors.
Like the MIPS R10000 and the Alpha 21264, BOOM is a unified physical register file design (also known as “explicit register renaming").

BOOM implements the open-source `RISC-V ISA <https://riscv.org>`__ and utilizes the `Chisel <https://chisel-lang.org>`__ hardware construction language to construct a generator for the core.
A generator can be thought of a generalized RTL design.
A standard RTL design can be viewed as a single instance of a generator design.
Thus, BOOM is a family of out-of-order designs rather than a single instance of a core.
Additionally, to build an SoC with a BOOM core, BOOM utilizes the `Rocket Chip <https://github.com/chipsalliance/rocket-chip>`__ SoC generator as a library to reuse different micro-architecture structures (TLBs, PTWs, etc).

.. [1] Yeager, Kenneth C. "The MIPS R10000 superscalar microprocessor." IEEE micro 16.2 (1996): 28-41.

.. [2] Kessler, Richard E. "The alpha 21264 microprocessor." IEEE micro 19.2 (1999): 24-36.
