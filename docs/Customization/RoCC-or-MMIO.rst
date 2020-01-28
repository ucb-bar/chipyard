.. _rocc-vs-mmio:

RoCC vs MMIO
------------

Accelerators or custom IO devices can be added to your SoC in several ways:

* MMIO Peripheral (a.k.a TileLink-Attached Accelerator)
* Tightly-Coupled RoCC Accelerator

These approaches differ in the method of the communication between the processor and the custom block.

With the TileLink-Attached approach, the processor communicates with MMIO peripherals through memory-mapped registers.

In contrast, the processor communicates with a RoCC accelerators through a custom protocol and custom non-standard ISA instructions reserved in the RISC-V ISA encoding space.
Each core can have up to four accelerators that are controlled by custom instructions and share resources with the CPU.
RoCC coprocessor instructions have the following form.

.. code-block:: none

    customX rd, rs1, rs2, funct

The X will be a number 0-3, and determines the opcode of the instruction, which controls which accelerator an instruction will be routed to.
The ``rd``, ``rs1``, and ``rs2`` fields are the register numbers of the destination register and two source registers.
The ``funct`` field is a 7-bit integer that the accelerator can use to distinguish different instructions from each other.

Note that communication through a RoCC interface requires a custom software toolchain, whereas MMIO peripherals can use that standard toolchain with appropriate driver support.
