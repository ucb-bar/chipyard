Physical Realization
====================

This chapter provides information useful for physically realizing the
BOOM processor. Although BOOM VLSI work is very preliminary, it has been
synthesized at 1 GHz on a high-end mobile 28 nm process. Unfortunately,
while VLSI flows are difficult to share or make portable (and encumbered
with proprietary libraries and tools), an enterprising individual may
want to visit the https://github.com/ucb-bar/plsi portable "Palmer’s
VLSI Scripts" repository which describes one way to push BOOM through a
VLSI flow.

Register Retiming
-----------------

Many VLSI tools require the designer to manually specify which modules
need to be analyzed for retiming.

In BOOM, the floating point units and the pipelined integer multiply
unit are described combinationally and then padded to the requested
latency with registers. In order to meet the desired clock frequency,
**the floating point units and the pipelined integer multiply unit must
be register-retimed**.

.. code-block:: scala

    val mul_result = lhs.toSInt * rhs.toSInt

    val mul_output_mux = MuxCase(
       UInt(0, 64), Array(
          FN(DW_64, FN_MUL)    -> mul_result(63,0),
          FN(DW_64, FN_MULH)   -> mul_result(127,64),
          FN(DW_64, FN_MULHU)  -> mul_result(127,64),
          FN(DW_64, FN_MULHSU) -> mul_result(127,64),
          FN(DW_32, FN_MUL)    -> Cat(Fill(32, mul_result(31)), mul_result(31,0)),
          FN(DW_32, FN_MULH)   -> Cat(Fill(32, mul_result(63)), mul_result(63,32)),
          FN(DW_32, FN_MULHU)  -> Cat(Fill(32, mul_result(63)), mul_result(63,32)),
          FN(DW_32, FN_MULHSU) -> Cat(Fill(32, mul_result(63)), mul_result(63,32))
    ))

    io.out := ShiftRegister(mul_output_mux, imul_stages, io.valid)

Pipelining Configuration Options
--------------------------------

Although BOOM does not provide high-level configurable-latency pipeline
stages, BOOM does provide a few configuration options to help the
implementor trade off CPI performance for cycle-time.

EnableFetchBufferFlowThrough
^^^^^^^^^^^^^^^^^^^^^^^^^^^^

The :term:`Front-end` fetches instructions and places them into a :term:`Fetch Buffer`.
The :term:`Back-end` pulls instructions out of the :term:`Fetch Buffer` and
then decodes, renames, and dispatches the instructions into the *Issue
Queue*. This :term:`Fetch Buffer` can be optionally set to be a *flow-through*
queue – instructions enqueued into the buffer can be immediately
dequeued on the other side on the same clock cycle. Turning this option
**off** forces all instructions to spend at least one cycle in the queue
but decreases the critical path between instruction fetch and dispatch.

EnableBrResolutionRegister
^^^^^^^^^^^^^^^^^^^^^^^^^^

The branch unit resolves branches, detects mispredictions, fans out the
branch kill signal to *all* inflight :term:`Micro-Ops (UOPs)<Micro-Op (UOP)>`, redirects the PC select
stage to begin fetching down the correct path, and sends snapshot
information to the branch predictor to reset its state properly so it
can begin predicting down the correct path. Turning this option **on**
delays the branch resolution by a cycle. In particular, this adds a
cycle to the branch misprediction penalty (which is hopefully a rare
event).

Functional Unit Latencies
^^^^^^^^^^^^^^^^^^^^^^^^^

The latencies of the pipelined floating point units and the pipelined
integer multiplier unit can be modified. Currently, all floating point
unit latencies are set to the latency of the longest floating point unit
(i.e., the DFMA unit). This can be changed by setting the *dfmaLatency*
in the *FPUConfig* class. Likewise, the integer multiplier is also set
to the *dfmaLatency*. [1]_

.. [1]
   The reason for this is that the imul unit is most likely sharing a
   write port with the DFMA unit and so must be padded out to the same
   length. However, this isn’t fundamental and there’s no reason an imul
   unit not sharing a write port with the FPUs should be constrained to
   their latencies.
