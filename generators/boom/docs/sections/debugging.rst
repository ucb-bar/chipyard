Debugging
=========

FireSim Debugging
-----------------

In addition to Verilator and VCS software simulation testing, one can use
the FireSim tool to debug faster using an FPGA. This tools comes out of the
UC Berkeley Architecture Research group and is still a work in progress. You
can find the documentation and website at https://fires.im/.

Chicken Bits
------------

BOOM supports a chicken-bit to delay all instructions from issue until the
pipeline clears. This effectively turns BOOM into a unpipelined in-order
core. The chicken bit is controlled by the third bit of the CSR at ``0x7c1``.
Writing this CSR with `csrwi 0x7c1, 0x8` will turn off all out-of-orderiness
in the core. High-performance can be re-enabled with `csrwi 0x7c1, 0x0`.
