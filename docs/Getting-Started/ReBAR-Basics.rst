

ReBAR Basics
===============================


Generators
-------------------------------------------
Generators are parametrized programs written as RTL code, designed to generate verilog code based on configuration specifications.
Generators can be used to generate Systems-on-Chip (SoCs) using a collection of system components organized in unique generator projects.
The ReBAR Framework currently consists of the following generators:

Processor Cores
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
**Rocket**
  An in-order RISC-V core.  

**BOOM (Berkeley Out-of-Order Machine)**
  An out-of-order RISC-V core. 

Data-Parallel Accelerators
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
**Hwacha**
  A decoupled vector architecture co-processor. Hwacha currently implements a non-standard RISC-V extension, using a vector architecture programming model.
  Hwacha integrates with a Rocket or BOOM core using the RoCC (Rocket Custom Co-processor) interface


System Components:
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
**icenet**
  A Network Interface Controller (NIC) designed to achieve up to 200 Gbps. 

**sifive-blocks**
  System components implemented by SiFive and used by SiFive projects, designed to be integrated with the Rocket chip generator. These system and peripheral components include UART, SPI, JTAG, I2C, PWM, and other peripheral and interface devices.

**AWL (Analog Widget Library)**
  Digital components required for integration with  high speed serial links. 

**testchipip**
  A collection of utilites used for testing chips and interfacing them with larger test environments.


Fixed Function Accelerators:
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
TBD



Tools
-------------------------------------------
**Chisel**
  A hardware description library embedded in Scala. Chisel is used to write RTL generators using meta-programming, by emdedding hardware generation primitives in the Scala programming language. The Chisel compilter elaborate the generator into a FIRRTL output.

**FIRRTL**
  An intermediate representation library for RTL description of digital designs. FIRRTL is used as a formalized digital circuit representation between Chisel and Verilog. FIRRTL enables digital circuits manipulation between Chisel elaboration and Verilog generation.

**BARSTOOLS**
  A collection of common FIRRTL transformations used to manipulate a digital circuit without changing the generator source RTL.


Toolchains
-------------------------------------------
**riscv-tools**
  A collection of software toolchains used to develope and execute software on the RISC-V ISA. The include compiler and assembler toolchains, functional ISA simulator (spike), the Berkeley Boot Loader (BBL) and proxy kernel. The riscv-tools repository was previously required to run any RISC-V software, however, many of the riscv-tools components have since been upstreamed to their respective open-source projects (Linux, GNU, etc.). Nevertheless, for consistent versioning, as well as software design flexibility for custom hardware, we include the riscv-tools repository and installation in the ReBAR framework. 


**esp-tools**
  A fork of riscv-tools, designed to work with the Hwacha non-standard RISC-V extension. This fork can also be used as an example demonstrating how to add additional RoCC accelerators to the ISA-level simulation (Spike) and the higher-level software toolchain (GNU binutils, riscv-opcodes, etc.)


Sims
-------------------------------------------
**verisim (Verilator wrapper)**
  Verilator is an open source Verilog simulator. The verisim directory provides wrappers which construct verilator-based simulators from relevant generated RTL, allowing for execution of test RISC-V programs on the simulator (including vcd waveform files).

**vsim (VCS wrapper)**
  VCS is a proprietary Verilog simulator. Assuming the user has valid VCS licenses and installations, the vsim directory provides wrappers which construct VCS-based simulators from relevant generated RTL, allowing for execution of test RISC-V programs on the simulator (including vcd/vpd waveform files).

**FireSim**
  FireSim is an open-source FPGA-accelerated simulation platform, using Amazon Web Services (AWS) EC2 F1 instances on the public cloud. FireSim automatically transforms and instruments open-hardware designs into fast (10s-100s MHz), deterministic, FPGA-based simulators that enable productive pre-silicon verification and performance validation. To model I/O, FireSim includes synthesizeable and timing-accurate models for standard interfaces like DRAM, Ethernet, UART, and others. The use of the elastic public clound enable FireSim to scale simulations up to thousands of nodes. In order to use FireSim, the repository must be cloned and executed on AWS instances. 


VLSI
-------------------------------------------
**HAMMER**
  HAMMER is a VLSI flow designed to provide a layer of abstraction between general physical design concepts to vendor-specific EDA tool commands. The HAMMER flow provide automated scripts which generate relevant tool commands based on a higher level description of physical desing contraints. The HAMMER flow also allows for re-use of process technology knowledge by enabling the construction of process-technology-specific plug-ins, which describe particular contraints relating to that process technology (obsolete standard cells, metal layer routing contraints, etc.). The HAMMER flow requires access to proprietry EDA tools and process technology libraries. 
