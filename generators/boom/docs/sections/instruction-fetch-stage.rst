Instruction Fetch
=================

.. _front-end:
.. figure:: /figures/front-end.svg
    :alt: BOOM :term:`Front-end`

    The BOOM :term:`Front-end`


BOOM instantiates its own :term:`Front-end` , similar to how the Rocket core(s)
instantiates its own :term:`Front-end` . This :term:`Front-end` fetches instructions and
makes predictions throughout the Fetch stage to redirect the instruction
stream in multiple fetch cycles (**F0**, **F1**...). If a misprediction is detected in BOOM’s
:term:`Back-end` (execution pipeline), or one of BOOM’s own predictors wants to redirect the pipeline in
a different direction, a request is sent to the :term:`Front-end` and it begins
fetching along a new instruction path. See :ref:`Branch Prediction` for
more information on how branch prediction fits into the Fetch Stage’s pipeline.

Since superscalar fetch is supported, the :term:`Front-end` retrieves a :term:`Fetch Packet`
of instructions from instruction memory and puts them into the
:term:`Fetch Buffer` to give to the rest of the pipeline. The :term:`Fetch Packet` also
contains other meta-data, such as a valid mask (which instructions in the
packet are valid?) and some branch prediction information that is used
later in the pipeline. Additionally, the PC and branch prediction information
is stored inside of the :term:`Fetch Target Queue` which holds this information
for the rest of the pipeline.

The Rocket Core I-Cache
-----------------------

BOOM instantiates the i-cache taken from the Rocket processor source code.
The i-cache is a virtually indexed, physically tagged set-associative cache.

To save power, the i-cache reads out a fixed number of bytes (aligned)
and stores the instruction bits into a register. Further instruction
fetches can be managed by this register. The i-cache is only fired up
again once the fetch register has been exhausted (or a branch prediction
directs the PC elsewhere).

The i-cache does not (currently) support fetching across cache-lines,
nor does it support fetching unaligned relative to the superscalar fetch
address. [1]_

The i-cache does not (currently) support hit-under-miss. If an i-cache
miss occurs, the i-cache will not accept any further requests until the
miss has been handled. This is less than ideal for scenarios in which
the pipeline discovers a branch mispredict and would like to redirect
the i-cache to start fetching along the correct path.

Fetching Compressed Instructions
--------------------------------

This section describes how the `RISC-V Compressed ISA extension <https://riscv.org/specifications/>`__
was implemented in BOOM. The Compressed ISA Extension, or RVC enables smaller, 16
bit encodings of common instructions to decrease the static and dynamic
code size. "RVC" comes with a number of features that are of particular
interest to micro-architects:

-  32b instructions have no alignment requirement, and may start on a
   half-word boundary.

-  All 16b instructions map directly into a longer 32b instruction.

During the :term:`Front-end` stages, BOOM retrieves a :term:`Fetch Packet` from the
i-cache, quickly decodes the instructions for branch
prediction, and pushes the :term:`Fetch Packet` into the :term:`Fetch Buffer`. However,
doing this brings up a particular set of issues to manage:

-  Increased decoding complexity (e.g., operands can now move around).

-  Finding *where* the instruction begins.

-  Removing ``+4`` assumptions throughout the code base,
   particularly with branch handling.

-  Unaligned instructions, in particular, running off cache lines and
   virtual pages.

The last point requires some additional "statefulness" in the :term:`Fetch Unit` ,
as fetching all of the pieces of an instruction may take multiple cycles.

The following describes the implementation of RVC in BOOM by describing
the lifetime of a instruction.

-  The :term:`Front-end` returns :term:`Fetch Packet` s of :term:`fetchWidth<Fetch Width>` \*16 bits wide. This
   was supported inherently in the BOOM :term:`Front-end` .

-  Maintain statefulness in **F3**, in the cycle where :term:`Fetch Packet` s
   are dequeued from the i-cache response queue and enqueued onto the
   :term:`Fetch Buffer` .

-  **F3** tracks the trailing 16b, PC, and instruction boundaries of the
   last :term:`Fetch Packet` . These bits are combined with the current
   :term:`Fetch Packet` and expanded to :term:`fetchWidth<Fetch Width>` \*32 bits for enqueuing onto the
   :term:`Fetch Buffer` . Predecode determines the start address of every
   instruction in this :term:`Fetch Packet` and masks the :term:`Fetch Packet` for the
   :term:`Fetch Buffer` .

-  The :term:`Fetch Buffer` now compacts away invalid, or misaligned instructions
   when storing to its memory.

The following section describes miscellaneous implementation details.

-  A challenging problem is dealing with instructions that cross a
   :term:`Fetch Boundary`. We track these instructions as belonging to the
   :term:`Fetch Packet` that contains their higher-order 16 bits. We have to
   be careful when determining the PC of these instructions, by tracking
   all instructions which were initially misaligned across a :term:`Fetch Boundary` .

-  The pipeline must also track whether an instruction was originally
   16b or 32b, for calculating ``PC+4`` or ``PC+2``.

The Fetch Buffer
----------------

:term:`Fetch Packet` s coming from the i-cache are placed into a :term:`Fetch Buffer` . The :term:`Fetch Buffer` helps to decouple the instruction
fetch :term:`Front-end` from the execution pipeline in the :term:`Back-end` .

The :term:`Fetch Buffer` is parameterizable. The number of entries can be
changed and whether the buffer is implemented as a “flow-through"
queue [2]_ or not can be toggled.

The Fetch Target Queue
----------------------

The :term:`Fetch Target Queue` is a queue that holds the PC
received from the i-cache and the branch prediction info associated
with that address. It holds this information for the pipeline to
reference during the executions of its :term:`Micro-Ops (UOPs)<Micro-Op (UOP)>` . It is dequeued by
the ROB once an instruction is committed and is updated during pipeline
redirection/mispeculation.

.. [1] This constraint is due to the fact that a cache-line is not stored
    in a single row of the memory bank, but rather is striped across a
    single bank to match the refill size coming from the uncore.
    Fetching unaligned would require modification of the underlying
    implementation, such as banking the i-cache such that consecutive
    chunks of a cache-line could be accessed simultaneously.

.. [2] A flow-through queue allows entries being enqueued to be
    immediately dequeued if the queue is empty and the consumer is
    requesting (the packet "flows through" instantly).
