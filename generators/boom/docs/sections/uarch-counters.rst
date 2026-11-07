Micro-architectural Event Tracking
==================================

Version 1.9.1 of the RISC-V Privileged Architecture adds support for
**Hardware Performance Monitor (HPM)** counters. [1]_ The HPM support allows
a nearly infinite number of micro-architectural events (called **Hardware
Performance Events (HPEs)**) to be multiplexed onto up to multiple physical counters
(called **Hardware Performance Counters (HPCs)**).

Setup HPM events to track
-------------------------

The available HPE's are split into *event sets* and *events*.
*Event sets* are groupings of similar microarchitectural *events* (branch prediction events,
memory events, etc). To access an *HPE* you must choose the correct *event set* and
*event* bit and write to the proper HPC register for that event. An example of event set
numbers and the event bit for a particular event is given below.

.. _uarch-counter-table:
    :caption: UArch Event Sets and Events

+-------------+-----------+--------------------------------+
| Event Set # | Event Bit | Description                    |
+=============+===========+================================+
| 1           | 1         | I$ Blocked                     |
+-------------+-----------+--------------------------------+
| 1           | 2         | NOP                            |
+-------------+-----------+--------------------------------+
| 1           | 4         | Control Flow Target Mispredict |
+-------------+-----------+--------------------------------+

To access an HPC, you must first set up the privilege access level
of the particular HPC using ``mcounteren`` and ``scounteren``. Afterwards,
you write to the particular HPC register to setup which event(s) you want to
track. Bits [7:0] of the HPC register correspond to the event set while bits [?:8]
correspond to the event bitmask. Note that the bitmask can be a
singular event **or** multiple events.

.. _enable-uarch-counters:
.. code-block:: c
    :caption: Enable Hardware Performance Monitor Counters

    write_csr(mcounteren, -1); // Enable supervisor use of all perf counters
    write_csr(scounteren, -1); // Enable user use of all perf counters

    write_csr(mhpmevent3, 0x101); // read I$ Blocked event
    write_csr(mhpmevent4, 0x801); // read Ctrl Flow Target Mispred. event
    ...

Reading HPM counters in software
--------------------------------

The Code Example :numref:`read-csr` demonstrates how to read the value of
any HPC from software. Note that HPCs need to be "zero'd" out
by first reading the value at the beginning of the program, then reading the
counter again the end, and then subtracting the initial value from the second
read. However, this only applies to the HPC's not ``cycle``, ``instret``, and
``time``.

.. _read-csr:
.. code-block:: c
    :caption: Read CSR Register

    #define read_csr_safe(reg) ({ register long __tmp asm("a0"); \
            asm volatile ("csrr %0, " #reg : "=r"(__tmp)); \
            __tmp; })

    // read cycle and instruction counts in user mode
    uint64_t csr_cycle  = read_csr_safe(cycle);
    uint64_t csr_instr  = read_csr_safe(instret);

    // read initial value of HPMC's in user mode
    uint64_t start_hpmc3  = read_csr_safe(hpmcounter3);
    ...
    uint64_t start_hpmc31 = read_csr_safe(hpmcounter31);

    // program to monitor

    // read final value of HPMC's and substract initial in user mode
    printf("Value of Event (zero'd): %d\n", read_csr_safe(hpmcounter3) - start_hpmc3);


Adding your own HPE
-------------------

To add your own HPE, you modify the event set and particular event in
``src/main/scala/exu/core.scala``. Note that the 1st item in the ``Seq`` corresponds
to the first bit in the event set.

External Resources
------------------

Information in this section was adapted from https://static.dev.sifive.com/U54-MC-RVCoreIP.pdf
which details more about HPE/C's from RocketChip's perspective. Note: The HPE's supported by
Rocket and BOOM differ, but the mechanism to access them is the same!

.. [1]
   Future efforts may add some counters into a memory-mapped access
   region. This will open up the ability to track events that, for
   example, may not be tied to any particular core (like last-level
   cache misses).
