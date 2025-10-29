Future Work
===========

This chapter lays out some of the potential future directions that BOOM
can be taken. To help facilitate such work, the preliminary design
sketches are described below.

The BOOM Custom Co-processor Interface (BOCC)
---------------------------------------------

Some accelerators may wish to take advantage of speculative instructions
(or even out-of-order issue) to begin executing instructions earlier to
maximize de-coupling. Speculation can be handled by either by epoch tags
(if in-order issue is maintained to the co-processor) or by allocating
mask bits (to allow for fine-grain killing of instructions).

The Vector (“V") ISA Extension
------------------------------

Implementing the Vector Extension in BOOM would open up the ability to
leverage performance (or energy-efficiency) improvements in running
data-level parallel codes (DLP). While it would be relatively easy to
add vector arithmetic operations to BOOM, the significant challenges lie
in the vector load/store unit.

Perhaps unexpectedly, a simple but very efficient implementation could
be very small. The smallest possible vector register file (four 64-bit
elements per vector) weighs in at 1024 bytes. A reasonable out-of-order
implementation could support 8 elements per vector and 16 inflight
vector registers (for a total of 48 physical vector registers) which
would only be 3 kilobytes. Following the temporal vector design of the
Cray I, the vector unit can re-use the expensive scalar functional units
by trading off space for time. This also opens up the vector register
file to being implemented using 1 read/1 write ports, fitting it in very
area-efficient SRAMs. As a point of comparison, one of the most
expensive parts of a synthesizable BOOM is its flip-flop based scalar
register file. While a 128-register scalar register file comes in at
1024 bytes, it must be highly ported to fully exploit scalar
instruction-level parallelism (a three-issue BOOM with one FMA unit is 7
read ports and 3 write ports).

