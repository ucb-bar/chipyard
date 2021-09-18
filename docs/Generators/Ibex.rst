Ibex Core
====================================

`Ibex <https://github.com/lowRISC/ibex>`__ is a parameterizable RV32IMC embedded core written in SystemVerilog, currently maintained by `lowRISC <https://lowrisc.org>`__.
The `Ibex core` is wrapped in an `Ibex tile` so it can be used with the `Rocket Chip SoC generator`.
The core exposes a custom memory interface, interrupt ports, and other misc. ports that are connected from within the tile to TileLink buses and other parameterization signals.

.. Warning:: The Ibex mtvec register is 256 byte aligned. When writing/running tests, ensure that the trap vector is also 256 byte aligned.

.. Warning:: The Ibex reset vector is located at BOOT_ADDR + 0x80.

While the core itself is not a generator, we expose the same parameterization that the Ibex core provides so that all supported Ibex configurations are available.

For more information, see the `GitHub repository for Ibex <https://github.com/lowRISC/ibex>`__.