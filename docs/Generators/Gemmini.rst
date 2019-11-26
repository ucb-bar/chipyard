Gemmini
====================================

The Gemmini project is developing a systolic-array based matrix multipication acceleration generation for the investigration of SoC integration of such accelerators. It is inspired by recent trends in machine learning accelerators for edge and mobile SoCs.

Gemmini is implemented as a RoCC accelerator with non-standard RISC-V custom instructions.

To add a Gemmini unit to an SoC, you should add the ``gemmini.DefaultGemminiConfig`` config mixin to the SoC configurations. The Gemmini unit uses the RoCC port of a Rocket or BOOM `tile`, and by default connects to the memory system through the `System Bus` (i.e., directly to the L2 cache). 

To change the configuration of the Hwacha vector unit, you can write a custom configuration to replace the ``DefaultGemminiConfig``. You can view the ``DefaultGemminiConfig`` under `generators/gemmini/src/main/scala/configs.scala <https://github.com/ucb-bar/gemmini/blob/master/src/main/scala/gemmini/configs.scala>`__ to see the possible configuration parameters.


TODO: Block Diagram Figure


Software
------------------

The Gemmini accelerator includes a C matrix multipication library which wraps the calls to the custom Gemmini instructions.
This library can be found under the ``software`` directory of the generator. 

The Gemmini generator generates a C header file based on the generator parameters. This header files gets compiled together with the matrix multipcation library to tune library performance.




The Gemmini generator includes a custom implementation of the Spike functional ISA simulator. This implementation is based on the ``esp-tools`` Spike implementation that is mixed with the Hwacha vector accelerator


Generator Parameters
--------------------------

Major parameters of interest include:

* Systolic array dimensions (``tileRows``, ``tileColumns``, ``meshRows``, ``meshColumns``) - The systolic array is composed of a 2-level hierarchy, in which each tile is fully combinational, while a mesh of tiles


* Access-execute queue parameters (``ld_queue_length``, ``st_queue_length``, ``ex_queue_length``) - To implement access-execute decoupling, a Gemmini accelerator has a load instruction queue, and store instruction queue and an execute instruction queue. The relative sizes of these queue determines the level of access-execute decoupling.

*

* Scratchpad memory parameters - 
