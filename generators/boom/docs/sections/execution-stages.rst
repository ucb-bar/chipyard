The Execute Pipeline
====================

.. _dual-issue-pipeline:
.. figure:: /figures/execution-pipeline-2w.png
    :alt: Dual Issue Pipeline

    An example pipeline for a dual-issue BOOM. The first issue port schedules :term:`UOP<Micro-Op (UOP)`s onto
    Execute Unit #0, which can accept ALU operations, FPU operations, and integer multiply instructions.
    The second issue port schedules ALU operations, integer divide instructions (unpipelined), and load/store
    operations. The ALU operations can bypass to dependent instructions. Note that the ALU in Execution Unit #0 is
    padded with pipeline registers to match latencies with the FPU and iMul units to make scheduling for the
    write-port trivial. Each :term:`Execution Unit` has a single issue-port dedicated to it but contains within it a number
    of lower-level :term:`Functional Unit`s.

The **Execution Pipeline** covers the execution and write-back of :term:`Micro-Ops (UOPs)<Micro-Op (UOP)>`.
Although the :term:`UOPs<Micro-Op (UOP)` will travel down the pipeline one after the other
(in the order they have been issued), the :term:`UOPs<Micro-Op (UOP)` themselves are
likely to have been issued to the Execution Pipeline out-of-order.
:numref:`dual-issue-pipeline` shows an example Execution Pipeline for a
dual-issue BOOM.

Execution Units
---------------

.. _example-fu:
.. figure:: /figures/execution-unit.png
    :alt: Example :term:`Execution Unit`

    An example :term:`Execution Unit`. This particular example shows an integer ALU (that can bypass
    results to dependent instructions) and an unpipelined divider that becomes busy during operation. Both
    :term:`Functional Unit`s share a single write-port. The :term:`Execution Unit` accepts both kill signals and branch resolution
    signals and passes them to the internal :term:`Functional Unit` s as required.


An :term:`Execution Unit` is a module that a single issue port will schedule
:term:`UOPs<Micro-Op (UOP)` onto and contains some mix of :term:`Functional Unit` s. Phrased in
another way, each issue port from the **Issue Queue** talks to one and only
one :term:`Execution Unit`. An :term:`Execution Unit` may contain just a single simple
integer ALU, or it could contain a full complement of floating point
units, a integer ALU, and an integer multiply unit.

The purpose of the :term:`Execution Unit` is to provide a flexible abstraction
which gives a lot of control over what kind of :term:`Execution Unit` s the
architect can add to their pipeline

Scheduling Readiness
~~~~~~~~~~~~~~~~~~~~

An :term:`Execution Unit` provides a bit-vector of the :term:`Functional Unit` s it has
available to the issue scheduler. The issue scheduler will only schedule
:term:`UOPs<Micro-Op (UOP)` that the :term:`Execution Unit` supports. For :term:`Functional Unit` s that
may not always be ready (e.g., an un-pipelined divider), the appropriate
bit in the bit-vector will be disabled (See :numref:`dual-issue-pipeline`).

Functional Unit
----------------

.. _abstract-fu:
.. figure:: /figures/abstract-functional-unit.png
    :alt: Abstract :term:`Functional Unit`

    The abstract Pipelined :term:`Functional Unit` class. An expert-written, low-level :term:`Functional Unit`
    is instantiated within the :term:`Functional Unit`. The request and response ports are abstracted and bypass and
    branch speculation support is provided. :term:`UOPs<Micro-Op (UOP)` are individually killed by gating off their response as they
    exit the low-level :term:`Functional Unit` .

:term:`Functional Unit` s are the muscle of the CPU, computing the necessary
operations as required by the instructions. :term:`Functional Unit` s typically
require a knowledgable domain expert to implement them correctly and
efficiently.

For this reason, BOOM uses an abstract :term:`Functional Unit` class to "wrap"
expert-written, low-level :term:`Functional Unit` s from the Rocket repository
(see :ref:`Rocket Chip SoC Generator`). However, the expert-written :term:`Functional Unit` s
created for the Rocket in-order processor make assumptions about
in-order issue and commit points (namely, that once an instruction has
been dispatched to them it will never need to be killed). These
assumptions break down for BOOM.

However, instead of re-writing or forking the :term:`Functional Unit` s, BOOM
provides an abstract :term:`Functional Unit` class (see :numref:`abstract-fu`)
that “wraps" the lower-level functional
units with the parameterized auto-generated support code needed to make
them work within BOOM. The request and response ports are abstracted,
allowing :term:`Functional Unit` s to provide a unified, interchangeable
interface.

Pipelined Functional Units
~~~~~~~~~~~~~~~~~~~~~~~~~~

A pipelined :term:`Functional Unit` can accept a new :term:`UOP<Micro-Op (UOP)` every cycle. Each
:term:`UOP<Micro-Op (UOP)` will take a known, fixed latency.

Speculation support is provided by auto-generating a pipeline that
passes down the :term:`UOP<Micro-Op (UOP)` meta-data and *branch mask* in parallel with
the :term:`UOP<Micro-Op (UOP)` within the expert-written :term:`Functional Unit` . If a :term:`UOP<Micro-Op (UOP)` is
misspeculated, it’s response is de-asserted as it exits the functional
unit.

An example pipelined :term:`Functional Unit` is shown in :numref:`abstract-fu`.

Un-pipelined Functional Units
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Un-pipelined :term:`Functional Unit` s (e.g., a divider) take an variable (and
unknown) number of cycles to complete a single operation. Once occupied,
they de-assert their ready signal and no additional :term:`UOPs<Micro-Op (UOP)` may be
scheduled to them.

Speculation support is provided by tracking the **branch mask** of the
:term:`UOP<Micro-Op (UOP)` in the :term:`Functional Unit`.

The only requirement of the expert-written un-pipelined :term:`Functional Unit`
is to provide a *kill* signal to quickly remove misspeculated
:term:`UOPs<Micro-Op (UOP)`. [1]_

.. _fu-hierarchy:
.. figure:: /figures/functional-unit-hierarchy.png
    :alt: Functional Unit Hierarchy

    The dashed ovals are the low-level :term:`Functional Unit` s written by experts, the squares are
    concrete classes that instantiate the low-level :term:`Functional Unit` s, and the octagons are abstract classes that
    provide generic speculation support and interfacing with the BOOM pipeline. The floating point divide
    and squart-root unit doesn’t cleanly fit either the ``Pipelined`` nor ``Unpipelined`` abstract class, and so directly
    inherits from the ``FunctionalUnit`` super class.

Branch Unit & Branch Speculation
--------------------------------

The :term:`Branch Unit` handles the resolution of all branch and jump
instructions.

All :term:`UOPs<Micro-Op (UOP)` that are "inflight" in the pipeline (have an allocated ROB
entry) are given a branch mask, where each bit in the branch mask
corresponds to an un-executed, inflight branch that the :term:`UOP<Micro-Op (UOP)` is
speculated under. Each branch in *Decode* is allocated a branch tag,
and all following :term:`UOPs<Micro-Op (UOP)` will have the corresponding bit in the
branch mask set (until the branch is resolved by the :term:`Branch Unit`).

If the branches (or jumps) have been correctly speculated by the
:term:`Front-end`, then the :term:`Branch Unit` s only action is to broadcast the
corresponding branch tag to *all* inflight :term:`UOPs<Micro-Op (UOP)` that the branch has
been resolved correctly. Each :term:`UOP<Micro-Op (UOP)` can then clear the corresponding
bit in its branch mask, and that branch tag can then be allocated to a
new branch in the *Decode* stage.

If a branch (or jump) is misspeculated, the :term:`Branch Unit` must redirect
the PC to the correct target, kill the :term:`Front-end` and :term:`Fetch Buffer`, and
broadcast the misspeculated branch tag so that all dependent, inflight
:term:`UOPs<Micro-Op (UOP)` may be killed. The PC redirect signal goes out immediately, to
decrease the misprediction penalty. However, the *kill* signal is
delayed a cycle for critical path reasons.

The :term:`Front-end` must pass down the pipeline the appropriate branch
speculation meta-data, so that the correct direction can be reconciled
with the prediction. Jump Register instructions are evaluated by
comparing the correct target with the PC of the next instruction in the
ROB (if not available, then a misprediction is assumed). Jumps are
evaluated and handled in the :term:`Front-end` (as their direction and target
are both known once the instruction can be decoded).

BOOM (currently) only supports having one :term:`Branch Unit` .

Load/Store Unit
---------------

The **Load/Store Unit (LSU)** handles the execution of load, store, atomic,
and fence operations.

BOOM (currently) only supports having one LSU (and thus can only send
one load or store per cycle to memory). [2]_

See :ref:`The Load/Store Unit (LSU)` for more details on the LSU.

Floating Point Units
--------------------

.. _fp-fu:
.. figure:: /figures/functional-unit-fpu.png
    :scale: 15 %
    :align: center
    :alt: Functional Unit for FPU

    The class hierarchy of the FPU is shown. The expert-written code is contained within
    the hardfloat and rocket repositories. The "FPU" class instantiates the Rocket components, which itself
    is further wrapped by the abstract :term:`Functional Unit` classes (which provides the out-of-order speculation
    support).

The low-level floating point units used by BOOM come from the Rocket
processor (https://github.com/chipsalliance/rocket-chip) and hardfloat
(https://github.com/ucb-bar/berkeley-hardfloat) repositories. Figure
:numref:`fp-fu` shows the class hierarchy of the FPU.

To make the scheduling of the write-port trivial, all of the pipelined
FP units are padded to have the same latency. [3]_

Floating Point Divide and Square-root Unit
------------------------------------------

BOOM fully supports floating point divide and square-root operations
using a single **FDiv/Sqrt** (or fdiv for short). BOOM accomplishes this by
instantiating a double-precision unit from the hardfloat repository. The
unit comes with the following features/constraints:

-  expects 65-bit recoded double-precision inputs

-  provides a 65-bit recoded double-precision output

-  can execute a divide operation and a square-root operation
   simultaneously

-  operations are unpipelined and take an unknown, variable latency

-  provides an *unstable* FIFO interface

Single-precision operations have their operands upscaled to
double-precision (and then the output downscaled). [4]_

Although the unit is unpipelined, it does not fit cleanly into the
Pipelined/Unpipelined abstraction used by the other :term:`Functional Unit` s
(see :numref:`fu-hierarchy`). This is because the unit provides
an unstable FIFO interface: although the unit may provide a *ready*
signal on Cycle ``i``, there is no guarantee that it will continue
to be *ready* on Cycle ``i+1``, even if no operations are enqueued.
This proves to be a challenge, as the Issue Queue may attempt to issue
an instruction but cannot be certain the unit will accept it once it
reaches the unit on a later cycle.

The solution is to add extra buffering within the unit to hold
instructions until they can be released directly into the unit. If the
buffering of the unit fills up, back pressure can be safely applied to
the **Issue Queue**. [5]_

Parameterization
----------------

BOOM provides flexibility in specifying the issue width and the mix of
:term:`Functional Unit` s in the execution pipeline. See ``src/main/scala/exu/execution-units.scala``
for a detailed view on how to instantiate the execution pipeline in BOOM.

Additional parameterization, regarding things like the latency of the FP
units can be found within the configuration settings (``src/main/common/config-mixins.scala``).

Control/Status Register Instructions
------------------------------------

A set of **Control/Status Register (CSR)** instructions allow the atomic
read and write of the Control/Status Registers. These architectural
registers are separate from the integer and floating registers, and
include the cycle count, retired instruction count, status, exception
PC, and exception vector registers (and many more!). Each CSR has its
own required privilege levels to read and write to it and some have
their own side-effects upon reading (or writing).

BOOM (currently) does not rename *any* of the CSRs, and in addition to
the potential side-effects caused by reading or writing a CSR, **BOOM
will only execute a CSR instruction non-speculatively.** [6]_ This is
accomplished by marking the CSR instruction as a "unique" (or
"serializing") instruction - the ROB must be empty before it may proceed
to the Issue Queue (and no instruction may follow it until it has
finished execution and been committed by the ROB). It is then issued by
the Issue Queue, reads the appropriate operands from the Physical
Register File, and is then sent to the CSRFile. [7]_ The CSR instruction
executes in the CSRFile and then writes back data as required to the
Physical Register File. The CSRFile may also emit a PC redirect and/or
an exception as part of executing a CSR instruction (e.g., a syscall).

The Rocket Custom Co-Processor Interface (RoCC)
-----------------------------------------------

The **RoCC interface** accepts a RoCC command and up to two register inputs
from the Control Processor’s scalar register file. The RoCC command is
actually the entire RISC-V instruction fetched by the Control Processor
(a "RoCC instruction"). Thus, each RoCC queue entry is at least
``2\*XPRLEN + 32`` bits in size (additional RoCC instructions may use the
longer instruction formats to encode additional behaviors).

As BOOM does not store the instruction bits in the ROB, a separate data
structure (A "RoCC Shim") holds the
instructions until the RoCC instruction can be committed and the RoCC
command sent to the co-processor.

The source operands will also require access to BOOM’s register file.
RoCC instructions are dispatched to the Issue Window, and scheduled
so that they may access the read ports of the register file once the
operands are available. The operands are then written into the RoCC
Shim, which stores the operands and the instruction
bits until they can be sent to the co-processor. This requires
significant state.

After issue to RoCC, we track a queue of in-flight RoCC instructions,
since we need to translate the logical destination register identifier
from the RoCC response into the previously renamed physical destination
register identifier.

Currently the RoCC interface does not support interrupts, exceptions,
reusing the BOOM FPU, or direct access to the L1 data cache. This should
all be straightforward to add, and will be completed as demand arises.

.. [1]
   This constraint could be relaxed by waiting for the un-pipelined unit
   to finish before de-asserting its busy signal and suppressing the
   *valid* output signal.

.. [2]
   Relaxing this constraint could be achieved by allowing multiple LSUs
   to talk to their own bank(s) of the data-cache, but the added
   complexity comes in allocating entries in the LSU before knowing the
   address, and thus which bank, a particular memory operation pertains
   to.

.. [3]
   Rocket instead handles write-port scheduling by killing and
   refetching the offending instruction (and all instructions behind it)
   if there is a write-port hazard detected. This would be far more
   heavy-handed to do in BOOM.

.. [4]
   It is cheaper to perform the SP-DP conversions than it is to
   instantiate a single-precision fdivSqrt unit.

.. [5]
   It is this ability to hold multiple inflight instructions within the
   unit simultaneously that breaks the “only one instruction at a time"
   assumption required by the UnpipelinedFunctionalUnit abstract class.

.. [6]
   There is a lot of room to play with regarding the CSRs. For example,
   it is probably a good idea to rename the register (dedicated for use
   by the supervisor) as it may see a lot of use in some kernel code and
   it causes no side-effects.

.. [7]
   The CSRFile is a Rocket component.

