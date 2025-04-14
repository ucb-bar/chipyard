The RISC-V ISA Simulator (Spike)
=================================

Spike is the golden reference functional RISC-V ISA C++ sofware simulator.
It provides full system emulation or proxied emulation with `HTIF/FESVR <https://github.com/riscv/riscv-isa-sim/tree/master/fesvr>`__.
It serves as a starting point for running software on a RISC-V target.
Here is a highlight of some of Spikes main features:

* Multiple ISAs: RV32IMAFDQCV extensions
* Multiple memory models: Weak Memory Ordering (WMO) and Total Store Ordering (TSO)
* Privileged Spec: Machine, Supervisor, User modes (v1.11)
* Debug Spec
* Single-step debugging with support for viewing memory/register contents
* Multiple CPU support
* JTAG support
* Highly extensible (add and test new instructions)

In most cases, software development for a Chipyard target will begin with functional simulation using Spike
(usually with the addition of custom Spike models for custom accelerator functions), and only later move on to
full cycle-accurate simulation using software RTL simulators or FireSim.

Spike comes pre-packaged in the RISC-V toolchain and is available on the path as ``spike``.
More information can be found in the `Spike repository <https://github.com/riscv/riscv-isa-sim>`__.

Spike-as-a-Tile
-----------------

Chipyard contains experimental support for simulating a Spike processor model with the uncore, similar to a virtual-platform.
In this configuration, Spike is cache-coherent, and communicates with the uncore through a C++ TileLink private cache model.

.. code-block:: shell

    make CONFIG=SpikeConfig run-binary BINARY=hello.riscv

Spike-as-a-Tile also supports Tightly-Coupled-Memory (TCM) for the SpikeTile, in which the main system memory is entirely modeled
within the Spike tile, allowing for very fast simulatoin performance.

.. code-block:: shell

    make CONFIG=SpikeUltraFastConfig run-binary BINARY=hello.riscv

Spike-as-a-Tile can be configured with custom IPC, commit logging, and other behaviors. Spike-specific flags can be added as plusargs to ``EXTRA_SIM_FLAGS``

..  code-block:: shell

    make CONFIG=SpikeUltraFastConfig run-binary BINARY=hello.riscv EXTRA_SIM_FLAGS="+spike-ipc=10000 +spike-fast-clint +spike-debug" LOADMEM=1


* ``+spike-ipc=``: Sets the maximum number of instructions Spike can retire in a single "tick", or cycle of the uncore simulation.
* ``+spike-fast-clint``: Enables fast-forwarding through WFI stalls by generating fake timer interrupts
* ``+spike-debug``: Enables debug Spike logging
* ``+spike-verbose``: Enables Spike commit-log generation

Adding a new spike device model
-------------------------------

Spike comes with a few functional device models such as UART, CLINT, and PLIC.
However, you may want to add custom device models into Spike such as a block device.
Example devices are in the ``toolchains/riscv-tools/riscv-spike-devices`` directory.
These devices are compiled as a shared library that can be dynamically linked to Spike.

To compile these plugins, run ``make`` inside ``toolchains/riscv-tools/riscv-spike-devices``. This will generate a ``libspikedevices.so``.

To hook up a block device to spike and provide a default image to initialize the block device, run

.. code-block:: shell

   spike --extlib=libspikedevices.so --device="iceblk,img=<path to Linux image>" <path to kernel binary>

.

The ``--device`` option consists of the device name and arguments.
In the above example ``iceblk`` is the device name and ``img=<path to Linux image>`` is the argument passed on to the plugin device.
