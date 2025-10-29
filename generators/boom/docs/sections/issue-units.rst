The Issue Unit
==============

The **Issue Queue** s hold dispatched :term:`Micro-Ops (UOPs) <Micro-Op (UOP)>` that have not yet executed.
When all of the operands for the :term:`UOP<Micro-Op (UOP)` are ready, the issue slot sets
its "request" bit high. The issue select logic then chooses to issue a
slot which is asserting its "request" signal. Once a :term:`UOP<Micro-Op (UOP)` is issued,
it is removed from the Issue Queue to make room for more dispatched
instructions.

BOOM uses a split Issue Queues - instructions of specific types are placed
into a unique Issue Queue (integer, floating point, memory).

Speculative Issue
-----------------

Although not yet supported, future designs may choose to speculatively
issue :term:`UOPs<Micro-Op (UOP)` for improved performance (e.g., speculating that a load
instruction will hit in the cache and thus issuing dependent :term:`UOPs<Micro-Op (UOP)`
assuming the load data will be available in the bypass network). In such
a scenario, the Issue Queue cannot remove speculatively issued
:term:`UOPs<Micro-Op (UOP)` until the speculation has been resolved. If a
speculatively-issued :term:`UOP<Micro-Op (UOP)` failure occurs, then all issued :term:`UOPs<Micro-Op (UOP)`
that fall within the speculated window must be killed and retried from
the Issue Queue. More advanced techniques are also available.

Issue Slot
----------

:numref:`single-issue-slot` shows a single **issue slot** from the
Issue Queue. [1]_

Instructions are *dispatched* into the Issue Queue. From here, they
wait for all of their operands to be ready ("p" stands for *presence*
bit, which marks when an operand is *present* in the register file).

Once ready, the issue slot will assert its "request" signal, and wait
to be *issued*.

Issue Select Logic
------------------

.. _single-issue-slot:
.. figure:: /figures/issue_slot.png
    :alt: Single Issue Slot

    A single issue slot from the Issue Queue.

Each issue select logic port is a static-priority encoder that picks
that first available :term:`UOP<Micro-Op (UOP)` in the Issue Queue. Each port will only
schedule a :term:`UOP<Micro-Op (UOP)` that its port can handle (e.g., floating point
:term:`UOPs<Micro-Op (UOP)` will only be scheduled onto the port governing the Floating
Point Unit). This creates a cascading priority encoder for ports that
can schedule the same :term:`UOPs<Micro-Op (UOP)` as each other.

If a **Functional Unit** is unavailable, it de-asserts its available signal
and instructions will not be issued to it (e.g., an un-pipelined
divider).

Un-ordered Issue Queue
-----------------------

There are two scheduling policies available in BOOM.

The first is a MIPS R10K-style Un-ordered Issue
Queue. Dispatching instructions are placed
into the first available Issue Queue slot and remain there until they
are *issued*. This can lead to pathologically poor performance,
particularly in scenarios where unpredictable branches are placed into
the lower priority slots and are unable to be issued until the ROB fills
up and the Issue Window starts to drain. Because instructions following
branches are only *implicitly* dependent on the branch, there is no
other forcing function that enables the branches to issue earlier,
except the filling of the ROB.

Age-ordered Issue Queue
------------------------

The second available policy is an Age-ordered Issue Queue. Dispatched
instructions are placed into the bottom of the Issue Queue (at lowest
priority). Every cycle, every instruction is shifted upwards (the Issue
queue is a “collapsing queue"). Thus, the oldest instructions will have
the highest issue priority. While this increases performance by
scheduling older branches and older loads as soon as possible, it comes
with a potential energy penalty as potentially every Issue Queue slot
is being read and written to on every cycle.

Wake-up
-------

There are two types of wake-up in BOOM - *fast* wakeup and *slow*
wakeup (also called a long latency wakeup). Because ALU :term:`UOPs<Micro-Op (UOP)` can send their write-back data through the
bypass network, issued ALU :term:`UOPs<Micro-Op (UOP)` will broadcast their wakeup to the
Issue Queue as they are issued.

However, floating-point operations, loads, and variable latency
operations are not sent through the bypass network, and instead the
wakeup signal comes from the register file ports during the *write-back*
stage.

.. [1]
   Conceptually, a bus is shown for implementing the driving of the
   signals sent to the **Register Read** Stage. In reality BOOM actually
   uses muxes.
