Gemmini
====================================

The Gemmini project is developing a systolic-array based matrix multiplication acceleration generation for the investigation of SoC integration of such accelerators. It is inspired by recent trends in machine learning accelerators for edge and mobile SoCs.

Gemmini is implemented as a RoCC accelerator with non-standard RISC-V custom instructions.

To add a Gemmini unit to an SoC, you should add the ``gemmini.DefaultGemminiConfig`` config mixin to the SoC configurations. The Gemmini unit uses the RoCC port of a Rocket or BOOM `tile`, and by default connects to the memory system through the `System Bus` (i.e., directly to the L2 cache). 

To change the configuration of the Gemmini accelerator unit, you can write a custom configuration to replace the ``DefaultGemminiConfig``. You can view the ``DefaultGemminiConfig`` under `generators/gemmini/src/main/scala/configs.scala <https://github.com/ucb-bar/gemmini/blob/master/src/main/scala/gemmini/configs.scala>`__ to see the possible configuration parameters.

.. image:: ../_static/images/gemmini-system.png

Generator Parameters
--------------------------

Major parameters of interest include:

* Systolic array dimensions (``tileRows``, ``tileColumns``, ``meshRows``, ``meshColumns``) - The systolic array is composed of a 2-level hierarchy, in which each tile is fully combinational, while a mesh of tiles

.. image:: ../_static/images/gemmini-systolic-array.png

* Access-execute queue parameters (``ld_queue_length``, ``st_queue_length``, ``ex_queue_length``, ``rob_entries``) - To implement access-execute decoupling, a Gemmini accelerator has a load instruction queue, and store instruction queue and an execute instruction queue. The relative sizes of these queue determine the level of access-execute decoupling. Gemmini also implements a reorder buffer (ROB) - the number of entries in the ROB determines possible dependency management limitations.

* DMA parameters (``dma_maxbytes``, ``dma_buswidth``, ``mem_pipeline``) - Gemmini implements a DMA to move data from main memory to the Gemmini scratchpad, and from the Gemmini accumulators to main memory. The size of these DMA transactions is determined by the DMA parameters. These DMA parameters are tightly coupled with Rocket Chip SoC system parameters: in particular ``dma_buswidth`` is associated with the ``SystemBusKey`` ``beatBytes`` parameter, and ``dma_maxbytes`` is associated with ``cacheblockbytes`` Rocket Chip parameters.

* Scratchpad and accumulator memory parameters (``sp_banks``, ``sp_capacity``, ``acc_capacity``) - Determine the properties of the Gemmini scratchpad memory: overall capacity of the scratchpad or accumulators (in KiB), and the number of banks the scratchpad is divided into.

* Type parameters (``inputType``, ``outputType``, ``accType``) - Determine the data-types flowing through different parts of a Gemmini accelerator.

* Other system parameter (``shifter_banks``, ``dataflow``, ``aligned_to``)


Software
------------------

The Gemmini non-standard ISA extension is specified in the `Gemmini repository <https://github.com/ucb-bar/gemmini/blob/master/README.md>`__
The instruction included configuration instructions, data movement instructions (from main memory to the Gemmini scratchpad, and from the Gemmini accumulators to main memory), and matrix multiplication execution instructions. 

Since Gemmini instructions are not exposed through the GNU binutils assembler, several C macros are provided in order to construct the instruction encodings to call these instructions.

The Gemmini generator includes a C matrix multiplication library which wraps the calls to the custom Gemmini instructions.
The ``software`` directory of the generator includes the aforementioned library and macros, as well as bare-metal tests, and some FireMarshal workloads to run the tests in a Linux environment. In particular, the matrix multiplication C library can be found in the ``software/gemmini-rocc-tests/include/systolic.h`` file. 

The Gemmini generator generates a C header file based on the generator parameters. This header files gets compiled together with the matrix multiplication library to tune library performance. The generated header file can be found under ``software/gemmini-rocc-tests/include/systolic_params.h``

The Gemmini generator implements a custom non-standard version of the Spike functional ISA simulator. This implementation is based on the ``esp-tools`` Spike implementation that is mixed with the Hwacha vector accelerator. Is is currently as separate branch within the ``esp-tools`` Spike repository, but it is in the process of upstreaming to the main ``esp-tools`` branch.


