The Register Files and Bypass Network
=====================================

.. _full-boom-pipeline:
.. figure:: /figures/boom-pipeline.svg
    :alt: Multi-Issue Pipeline

    An example multi-issue pipeline. The integer register file needs 6 read ports and 3 write ports for the
    execution units present. The FP register file needs 3 read ports and 2 write ports. FP and memory
    operations share a long latency write port to both the integer and FP
    register file. To make scheduling of the write port trivial, the ALU’s pipeline is lengthened to match
    the FPU latency. The ALU is able to bypass from any of these stages to dependent instructions in the
    Register Read stage.

BOOM is a unified, **Physical Register File (PRF)** design. The register
files hold both the committed and speculative state. Additionally,
there are two register files: one for integer and one for floating point
register values. The **Rename Map Tables** track which physical register corresponds
to which ISA register.

BOOM uses the Berkeley hardfloat floating point units which use an
internal 65-bit operand format
(https://github.com/ucb-bar/berkeley-hardfloat). Therefore, all physical
floating point registers are 65-bits.

Register Read
-------------

The register file statically provisions all of the register read ports
required to satisfy all issued instructions. For example, if *issue port
#0* corresponds to an integer ALU and *issue port #1* corresponds to memory
unit, then the first two register read ports will statically serve the
ALU and the next two register read ports will service the memory unit for four
total read ports.

Dynamic Read Port Scheduling
~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Future designs can improve area-efficiency by provisioning fewer
register read ports and using dynamically scheduling to arbitrate for
them. This is particularly helpful as most instructions need only one
operand. However, it does add extra complexity to the design, which is
often manifested as extra pipeline stages to arbitrate and detect
structural hazards. It also requires the ability to kill issued
:term:`Micro-Ops (UOPs)<Micro-Op (UOP)` and re-issue them from the **Issue Queue** on a later cycle.

Bypass Network
--------------

ALU operations can be issued back-to-back by having the write-back
values forwarded through the **Bypass Network**. Bypassing occurs at the end
of the **Register Read** stage.
