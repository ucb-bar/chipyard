NVDLA
====================================

`NVDLA <https://nvdla.org/>`__ is an open-source deep learning accelerator developed by NVIDIA.
The `NVDLA` is attached as a TileLink peripheral so it can be used as a component within the `Rocket Chip SoC generator`.
The accelerator by itself exposes an AXI memory interface, control interface, interrupt line, and other misc. ports.
For more information, please visit their `website <https://nvdla.org/`__.

NVDLA Software
------------------

Located at ``software/nvdla-workload`` is a FireMarshal based workload to boot Linux with the proper NVDLA drivers.
Refer to that ``README.md`` for more information on how to run a simulation.

.. Warning:: Since running NVDLA requires Linux, we recommend using FireSim.
