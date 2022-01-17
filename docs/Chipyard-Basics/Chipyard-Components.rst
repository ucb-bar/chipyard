.. _chipyard-components:

Chipyard Components
===============================

Generators
-------------------------------------------

The Chipyard Framework currently consists of the following RTL generators:


Processor Cores
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

**Rocket Core**
  An in-order RISC-V core.
  See :ref:`Generators/Rocket:Rocket Core` for more information.

**BOOM (Berkeley Out-of-Order Machine)**
  An out-of-order RISC-V core.
  See :ref:`Generators/BOOM:Berkeley Out-of-Order Machine (BOOM)` for more information.

**CVA6 Core**
  An in-order RISC-V core written in System Verilog. Previously called Ariane.
  See :ref:`Generators/CVA6:CVA6 Core` for more information.

**Ibex Core**
  An in-order 32 bit RISC-V core written in System Verilog.
  See :ref:`Generators/Ibex:Ibex Core` for more information.

Accelerators
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

**Hwacha**
  A decoupled vector architecture co-processor.
  Hwacha currently implements a non-standard RISC-V extension, using a vector architecture programming model.
  Hwacha integrates with a Rocket or BOOM core using the RoCC (Rocket Custom Co-processor) interface.
  See :ref:`Generators/Hwacha:Hwacha` for more information.

**Gemmini**
  A matrix-multiply accelerator targeting neural-networks

**SHA3**
  A fixed-function accelerator for the SHA3 hash function. This simple accelerator is used as a demonstration for some of the
  Chipyard integration flows using the RoCC interface.

System Components:
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

**icenet**
  A Network Interface Controller (NIC) designed to achieve up to 200 Gbps.

**sifive-blocks**
  System components implemented by SiFive and used by SiFive projects, designed to be integrated with the Rocket Chip generator.
  These system and peripheral components include UART, SPI, JTAG, I2C, PWM, and other peripheral and interface devices.

**AWL (Analog Widget Library)**
  Digital components required for integration with high speed serial links.

**testchipip**
  A collection of utilities used for testing chips and interfacing them with larger test environments.


Tools
-------------------------------------------

**Chisel**
  A hardware description library embedded in Scala.
  Chisel is used to write RTL generators using meta-programming, by embedding hardware generation primitives in the Scala programming language.
  The Chisel compiler elaborates the generator into a FIRRTL output.
  See :ref:`Tools/Chisel:Chisel` for more information.

**FIRRTL**
  An intermediate representation library for RTL description of digital designs.
  FIRRTL is used as a formalized digital circuit representation between Chisel and Verilog.
  FIRRTL enables digital circuits manipulation between Chisel elaboration and Verilog generation.
  See :ref:`Tools/FIRRTL:FIRRTL` for more information.

**Barstools**
  A collection of common FIRRTL transformations used to manipulate a digital circuit without changing the generator source RTL.
  See :ref:`Tools/Barstools:Barstools` for more information.

**Dsptools**
  A Chisel library for writing custom signal processing hardware, as well as integrating custom signal processing hardware into an SoC (especially a Rocket-based SoC).

**Dromajo**
  A RV64GC emulator primarily used for co-simulation and was originally developed by Esperanto Technologies.
  See :ref:`Tools/Dromajo:Dromajo` for more information.

Toolchains
-------------------------------------------

**riscv-tools**
  A collection of software toolchains used to develop and execute software on the RISC-V ISA.
  The include compiler and assembler toolchains, functional ISA simulator (spike), the Berkeley Boot Loader (BBL) and proxy kernel.
  The riscv-tools repository was previously required to run any RISC-V software, however, many of the riscv-tools components have since been upstreamed to their respective open-source projects (Linux, GNU, etc.).
  Nevertheless, for consistent versioning, as well as software design flexibility for custom hardware, we include the riscv-tools repository and installation in the Chipyard framework.

**esp-tools**
  A fork of riscv-tools, designed to work with the Hwacha non-standard RISC-V extension.
  This fork can also be used as an example demonstrating how to add additional RoCC accelerators to the ISA-level simulation (Spike) and the higher-level software toolchain (GNU binutils, riscv-opcodes, etc.)

Software
-------------------------------------------

**FireMarshal**
  FireMarshal is the default workload generation tool that Chipyard uses to create software to run on its platforms.
  See :ref:`fire-marshal` for more information.

Sims
-------------------------------------------

**Verilator**
  Verilator is an open source Verilog simulator.
  The ``verilator`` directory provides wrappers which construct Verilator-based simulators from relevant generated RTL, allowing for execution of test RISC-V programs on the simulator (including vcd waveform files).
  See :ref:`Simulation/Software-RTL-Simulation:Verilator (Open-Source)` for more information.

**VCS**
  VCS is a proprietary Verilog simulator.
  Assuming the user has valid VCS licenses and installations, the ``vcs`` directory provides wrappers which construct VCS-based simulators from relevant generated RTL, allowing for execution of test RISC-V programs on the simulator (including vcd/vpd waveform files).
  See :ref:`Simulation/Software-RTL-Simulation:Synopsys VCS (License Required)` for more information.

**FireSim**
  FireSim is an open-source FPGA-accelerated simulation platform, using Amazon Web Services (AWS) EC2 F1 instances on the public cloud.
  FireSim automatically transforms and instruments open-hardware designs into fast (10s-100s MHz), deterministic, FPGA-based simulators that enable productive pre-silicon verification and performance validation.
  To model I/O, FireSim includes synthesizeable and timing-accurate models for standard interfaces like DRAM, Ethernet, UART, and others.
  The use of the elastic public cloud enable FireSim to scale simulations up to thousands of nodes.
  In order to use FireSim, the repository must be cloned and executed on AWS instances.
  See :ref:`Simulation/FPGA-Accelerated-Simulation:FireSim` for more information.

Prototyping
-------------------------------------------

**FPGA Prototyping**
  FPGA prototyping is supported in Chipyard using SiFive's ``fpga-shells``.
  Some examples of FPGAs supported are the Xilinx Arty 35T and VCU118 boards.
  For a fast and deterministic simulation with plenty of debugging tools, please consider using the :ref:`Simulation/FPGA-Accelerated-Simulation:FireSim` platform.
  See :ref:`Prototyping/index:Prototyping Flow` for more information on FPGA prototypes.

VLSI
-------------------------------------------

**Hammer**
  Hammer is a VLSI flow designed to provide a layer of abstraction between general physical design concepts to vendor-specific EDA tool commands.
  The HAMMER flow provide automated scripts which generate relevant tool commands based on a higher level description of physical design constraints.
  The Hammer flow also allows for re-use of process technology knowledge by enabling the construction of process-technology-specific plug-ins, which describe particular constraints relating to that process technology (obsolete standard cells, metal layer routing constraints, etc.).
  The Hammer flow requires access to proprietary EDA tools and process technology libraries.
  See :ref:`VLSI/Hammer:Core HAMMER` for more information.
