The RISC-V ISA
==============

The `RISC-V ISA <riscv.org>`__ is a widely adopted open-source ISA suited for a variety of applications.
It includes a base ISA as well as multiple optional extensions that implement different features.
BOOM implements the RV64GC variant of the RISC-V ISA (otherwise known as IMAFDC) [1]_. This includes the
MAFDC extensions and the privileged specification (multiply/divide, AMOs,
load-reserve/store-conditional, single-precision and double-precision IEEE
754-2008 floating point).

RISC-V provides the following features which make it easy to target with
high-performance designs:

* **Relaxed memory model**

    * This greatly simplifies the **Load/Store Unit (LSU)**, which does not need to
      have loads snoop other loads nor does coherence traffic need to snoop
      the LSU, as required by sequential consistency.

* **Accrued Floating Point (FP) exception flags**

    * The FP status register does not need to be renamed, nor can FP
      instructions throw exceptions themselves.

* **No integer side-effects**

    * All integer ALU operations exhibit no side-effects, other than the writing
      of the destination register. This prevents the need to rename
      additional condition state.

* **No cmov or predication**

    * Although predication can lower the branch predictor complexity of
      small designs, it greatly complicates out-of-order pipelines, including the
      addition of a third read port for integer operations.

* **No implicit register specifiers**

    * Even JAL requires specifying an explicit register. This simplifies rename
      logic, which prevents either the need to know the instruction first
      before accessing the rename tables, or it prevents adding more ports
      to remove the instruction decode off the critical path.

* **Registers rs1, rs2, rs3, rd are always in the same place**

    * This allows decode and rename to proceed in parallel.

More information about the RISC-V ISA can be found at https://riscv.org.

.. [1] Currently, BOOM does not implement the proposed "V" vector extension.
