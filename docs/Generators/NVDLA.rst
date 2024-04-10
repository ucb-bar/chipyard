NVDLA
====================================

`NVDLA <http://nvdla.org/>`_ is an open-source deep learning accelerator developed by NVIDIA.
The `NVDLA` is attached as a TileLink peripheral so it can be used as a component within the `Rocket Chip SoC generator`.
The accelerator by itself exposes an AXI memory interface (or two if you use the "Large" configuration), a control interface, and an interrupt line.
The main way to use the accelerator in Chipyard is to use the `NVDLA SW repository <https://github.com/ucb-bar/nvdla-sw>`_ that was ported to work on FireSim Linux.
However, you can also use the accelerator in baremetal simulations (refer to ``tests/nvdla.c``).

For more information on both the HW architecture and the SW, please visit their `website <http://nvdla.org/>`_.

NVDLA Software with FireMarshal
-------------------------------

Located at ``software/nvdla-workload`` is a FireMarshal-based workload to boot Linux with the proper NVDLA drivers.
Refer to that ``README.md`` for more information on how to run a simulation.
