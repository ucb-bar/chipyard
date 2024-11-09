VexiiRiscv Core
===================================
`VexiiRiscv <https://github.com/SpinalHDL/VexiiRiscv>`__ is a RV64IMAFDCB in-order superscalar core implemented in `SpinalHDL <https://spinalhdl.github.io/SpinalDoc-RTD/master/index.html#>`__.
VexiiRiscv is Linux-capable and achieves competitive IPC in its design class.
VexiiRiscv implements cache-coherent TileLink L1 data caches and is integrated as a selectable Tile in Chipyard.

The example VexiiRiscv config is ``VexiiRiscvConfig``.
When building this Config, Chipyard will call VexiiRiscv's SpinalHDL RTL generator to generate the core's SystemVerilog, before integrating it as a Chisel blackbox.

