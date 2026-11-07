The Next-Line Predictor (NLP)
=============================

BOOM core's :term:`Front-end` fetches
instructions and predicts every cycle where to fetch the next
instructions. If a misprediction is detected in BOOM's :term:`Back-end`, or
BOOM’s own :term:`Backing Predictor (BPD)` wants to redirect the pipeline in a
different direction, a request is sent to the :term:`Front-end` and it begins
fetching along a new instruction path.

The :term:`Next-Line Predictor (NLP)` takes in the current PC being used to
fetch instructions (the :term:`Fetch PC`) and predicts combinationally
where the next instructions should be fetched for the next cycle. If
predicted correctly, there are no pipeline bubbles.

The :term:`NLP<Next-Line Predictor (NLP)>` is an amalgamation of a fully-associative **Branch
Target Buffer (BTB)**, :term:`Bi-Modal Table (BIM)` and a **Return Address Stack (RAS)** which work together
to make a fast, but reasonably accurate prediction.

NLP Predictions
---------------

The :term:`Fetch PC` first performs a tag match to find a uniquely
matching BTB entry. If a hit occurs, the BTB entry will make a
prediction in concert with the RAS as to whether there is a
branch, jump, or return found in the :term:`Fetch Packet` and which
instruction in the :term:`Fetch Packet` is to blame. The :term:`BIM<Bi-Modal Table (BIM)>` is used to
determine if that prediction made was a branch taken or not taken.
The BTB entry also contains a predicted PC target, which is used
as the :term:`Fetch PC` on the next cycle.

.. _nlp-predictor-unit:
.. figure:: /figures/btb.png
    :scale: 35 %
    :align: center
    :alt: The :term:`Next-Line Predictor`

    The :term:`Next-Line Predictor (NLP)` Unit. The :term:`Fetch PC` scans the BTB’s "PC tags" for a match.
    If a match is found (and the entry is valid), the :term:`Bi-Modal Table (BIM)` and RAS are consulted for the final verdict. If the entry
    is a "ret" (return instruction), then the target comes from the RAS. If the entry is a unconditional "jmp"
    (jump instruction), then the :term:`BIM<Bi-Modal Table (BIM)>` is not consulted. The "bidx", or branch index, marks which instruction
    in a superscalar :term:`Fetch Packet` is the cause of the control flow prediction. This is necessary to mask off the
    other instructions in the :term:`Fetch Packet` that come over the taken branch

The hysteresis bits in the :term:`BIM<Bi-Modal Table (BIM)>` are only used
on a BTB entry *hit* and if the predicting instruction is a branch.

If the BTB entry contains a *return* instruction, the RAS stack is
used to provide the predicted return PC as the next :term:`Fetch PC`. The
actual RAS management (of when to or the stack) is governed externally.

For area-efficiency, the high-order bits of the PC tags and PC targets
are stored in a compressed file.

NLP Updates
-----------

Each branch passed down the pipeline remembers not only its own PC, but
also its :term:`Fetch PC` (the PC of the head instruction of its :term:`Fetch Packet` ). [2]_

BTB Updates
^^^^^^^^^^^

The BTB is updated *only* when the :term:`Front-end` is redirected to
*take* a branch or jump by either the :term:`Branch Unit` (in the
Execute stage) or the :term:`BPD<Backing Predictor (BPD)>` (later in the **Fetch** stages). [3]_

If there is no BTB entry corresponding to the taken branch or jump, an
new entry is allocated for it.

RAS Updates
^^^^^^^^^^^

The RAS is updated during the Fetch stages once the
instructions in the :term:`Fetch Packet` have been decoded. If the taken
instruction is a call [4]_ , the return address is pushed onto the RAS. If
the taken instruction is a return, then the RAS is popped.

Superscalar Predictions
^^^^^^^^^^^^^^^^^^^^^^^

When the :term:`NLP<Next-Line Predictor (NLP)>` makes a prediction, it is actually using the BTB to tag
match against the predicted branch’s :term:`Fetch PC`, and not the PC of
the branch itself. The :term:`NLP<Next-Line Predictor (NLP)>` must predict across the entire :term:`Fetch Packet`
which of the many possible branches will be the dominating
branch that redirects the PC. For this reason, we use a given branch’s
:term:`Fetch PC` rather than its own PC in the BTB tag match. [5]_

.. [2] In reality, only the very lowest bits must be saved, as the
    higher-order bits will be the same.

.. [3] The BTB relies on a little cleverness - when redirecting the
    PC on a misprediction, this new :term:`Fetch PC` is the same as the
    update PC that needs to be written into a new BTB entry’s
    target PC field. This "coincidence" allows the PC compression
    table to use a single search port - it is simultaneously reading the
    table for the next prediction while also seeing if the new Update
    PC already has the proper high-order bits allocated for it.

.. [4] While RISC-V does not have a dedicated call instruction, it can be
    inferred by checking for a JAL or JALR instruction with a writeback
    destination to x1 (aka, the return address register).

.. [5] Each BTB entry corresponds to a single :term:`Fetch PC`, but it is
    helping to predict across an entire :term:`Fetch Packet`. However, the
    BTB entry can only store meta-data and target-data on a single
    control-flow instruction. While there are certainly pathological
    cases that can harm performance with this design, the assumption is
    that there is a correlation between which branch in a :term:`Fetch Packet`
    is the dominating branch relative to the :term:`Fetch PC`,
    and - at least for narrow fetch designs - evaluations of this design
    has shown it is very complexity-friendly with no noticeable loss in
    performance. Some other designs instead choose to provide a whole
    bank of BTBs for each possible instruction in the :term:`Fetch Packet` .

