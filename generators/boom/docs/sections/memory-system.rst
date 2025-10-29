The Memory System
=================

.. Note:: This section is out-of-date as of 8/26/19 due to a new DCache implementation.

BOOM uses the Rocket Chip non-blocking cache ("Hellacache"). Designed for use
in in-order processors, a "shim" is used to connect BOOM to the
data cache. The source code for the cache can be found in
``nbdcache.scala`` in the `Rocket Chip repository <https://github.com/chipsalliance/rocket-chip>`.

The contract with the cache is that it may execute all memory operations
sent to it (barring structural hazards). As BOOM will send speculative
load instructions to the cache, the shim (``dcacheshim.scala``) must
track all "inflight load requests" and their status. If an inflight load
is discovered to be misspeculated, it is marked as such in the shim.
Upon return from the data cache, the load’s response to the pipeline is
suppressed and it is removed from the inflight load queue.

The Hellacache does not ack store requests; the absence of a nack is
used to signal a success.

All memory requests to the Hellacache may be killed the cycle after
issuing the request (while the request is accessing the data arrays).

The current data cache design accesses the SRAMs in a single-cycle.

The cache has a three-stage pipeline and can accept a new request every cycle.
The stages do the following:

* S0: Send request address

* S1: Access SRAM

* S2: Perform way-select and format response data

The data cache is also cache coherent which is helpful even in uniprocessor configurations
for allowing a host machine or debugger to read BOOM’s memory.
