Barstools
===============================

Barstools is a collection of useful FIRRTL transformations and compilers to help the build process.
Included in the tools are a MacroCompiler (used to map Chisel memory constructs to vendor SRAMs), FIRRTL transforms (to separate harness and top-level SoC files), and more.

Mapping technology SRAMs (MacroCompiler)
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

If you are planning on building a real chip, it is likely that you will plan on using some amount of static random access memory, or SRAM.
SRAM macros offer superior storage density over flip-flop arrays at the cost of restricting the number of read or write transactions that can happen in a cycle.
Unlike in Verilog, these types of sequential memory elements are first-class primitives in Chisel and FIRRTL (``SeqMem`` elements).
This allows Chisel designs to contain abstract instantiations of sequential memory elements without knowing the underlying implementation or process technology.

Modern CAD tools typically cannot synthesize SRAMs from a high-level RTL description.
This, unfortunately, requires the designer to include the SRAM instantiation in the source RTL, which removes its process portability.
In Verilog-entry designs, it is possible to create a layer of abstraction that allows a new process technology to implement a specific sequential memory block in a wrapper module.
However, this method can be fragile and laborious.

The FIRRTL compiler contains a transformation to replace the ``SeqMem`` primitives called ``ReplSeqMem``.
This simply converts all ``SeqMem`` instances above a size threshold into external module references.
An external module reference is a FIRRTL construct that enables a design to reference a module without describing its contents, only its inputs and outputs.
A list of unique SRAM configurations is output to a ``.conf`` file by FIRRTL, which is used to map technology SRAMs.
Without this transform, FIRRTL will map all ``SeqMem`` s to flip-flop arrays with equivalent behavior, which may lead to a design that is difficult to route.

The ``.conf`` file is consumed by a tool called MacroCompiler, which is part of the :ref:`Tools/Barstools:Barstools` scala package.
MacroCompiler is also passed an ``.mdf`` file that describes the available list of technology SRAMs or the capabilities of the SRAM compiler, if one is provided by the foundry.
Typically a foundry SRAM compiler will be able to generate a set of different SRAMs collateral based on some requirements on size, aspect ratio, etc. (see :ref:`Tools/Barstools:SRAM MDF Fields`).
Using a user-customizable cost function, MacroCompiler will select the SRAMs that are the best fit for each dimensionality in the ``.conf`` file.
This may include over provisioning (e.g. using a 64x1024 SRAM for a requested 60x1024, if the latter is not available) or arraying.
Arraying can be done in both width and depth, as well as to solve masking constraints.
For example, a 128x2048 array could be composed of four 64x1024 arrays, with two macros in parallel to create two 128x1024 virtual SRAMs which are combinationally muxed to add depth.
If this macro requires byte-granularity write masking, but no technology SRAMs support masking, then the tool may choose to use thirty-two 8x1024 arrays in a similar configuration.
You may wish to create a cache of your available SRAM macros either manually, or via a script. A reference script for creating a JSON of your SRAM macros is in the `asap7 technology library folder <https://github.com/ucb-bar/hammer/blob/8fd1486499b875d56f09b060f03a62775f0a6aa7/src/hammer-vlsi/technology/asap7/sram-cache-gen.py>`__.
For information on writing ``.mdf`` files, look at `MDF on github <https://github.com/ucb-bar/plsi-mdf>`__ and a brief description in :ref:`Tools/Barstools:SRAM MDF Fields` section.

The output of MacroCompiler is a Verilog file containing modules that wrap the technology SRAMs into the specified interface names from the ``.conf``.
If the technology supports an SRAM compiler, then MacroCompiler will also emit HammerIR that can be passed to Hammer to run the compiler itself and generate design collateral.
Documentation for SRAM compilers is forthcoming.

MacroCompiler Options
+++++++++++++++++++++
MacroCompiler accepts many command-line parameters which affect how it maps ``SeqMem`` s to technology specific macros.
This highest level option ``--mode`` specifies in general how MacroCompiler should map the input ``SeqMem`` s to technology macros.
The ``strict`` value forces MacroCompiler to map all memories to technology macros and error if it is unable to do so.
The ``synflops`` value forces MacroCompiler to map all memories to flip flops.
The ``compileandsynflops`` value instructs MacroCompiler to use the technology compiler to determine sizes of technology macros used but to then create mock versions of these macros with flip flops.
The ``fallbacksynflops`` value causes MacroCompiler to compile all possible memories to technology macros but when unable to do so to use flip flops to implement the remaining memories.
The final and default value, ``compileavailable``, instructs MacroCompiler to compile all memories to the technology macros and do nothing if it is unable to map them.

Most of the rest of the options are used to control where different inputs and outputs are expected and produced.
The option ``--macro-conf`` is the file that contains the set of input ``SeqMem`` configurations to map in the ``.conf`` format described above.
The option ``--macro-mdf`` also describes the input ``SeqMem`` s but is instead in the ``.mdf`` format.
The option ``--library`` is an ``.mdf`` description of the available technology macros that can be mapped to.
This file could be a list of fixed size memories often referred to as a cache of macros, or a description of what size memories could be made available through some technology specific process (usually an SRAM compiler), or a mix of both.
The option ``--use-compiler`` instructs MacroCompiler that it is allowed to use any compilers listed in the ``--library`` specification.
If this option is not set MacroCompiler will only map to macros directly listed in the ``--library`` specification.
The ``--verilog`` option specifies where MacroCompiler will write the verilog containing the new technology mapped memories.
The ``--firrtl`` option similarly specifies where MacroCompiler will write the FIRRTL that will be used to generate this verilog.
This option is optional and no FIRRTL will be emitted if it is not specified.
The ``--hammer-ir`` option specifies where MacroCompiler will write the details of which macros need to be generated from a technology compiler.
This option is not needed if ``--use-compiler`` is not specified.
This file can then be passed to HAMMER to have it run the technology compiler producing the associated macro collateral.
The ``--cost-func`` option allows the user to specify a different cost function for the mapping task.
Because the mapping of memories is a multi-dimensional space spanning performance, power, and area, the cost function setting of MacroCompiler allows the user to tune the mapping to their preference.
The default option is a reasonable heuristic that attempts to minimize the number of technology macros instantiated per ``SeqMem`` without wasting too many memory bits.
There are two ways to add additional cost functions.
First, you can simply write another one in scala and call `registerCostMetric` which then enables you to pass its name to this command-line flag.
Second, there is a pre-defined `ExternalMetric` which will execute a program (passed in as a path) with the MDF description of the memory being compiled and the memory being proposed as a mapping.
The program should print a floating point number which is the cost for this mapping, if no number is printed MacroCompiler will assume this is an illegal mapping.
The ``--cost-param`` option allows the user to specify parameters to pass to the cost function if the cost function supports that.
The ``--force-synflops [mem]`` options allows the user to override any heuristics in MacroCompiler and force it to map the given memory to flip-flops.
Likewise, the ``--force-compile [mem]`` option allows the user to force MacroCompiler to map the given ``mem`` to a technology macro.

SRAM MDF Fields
+++++++++++++++
Technology SRAM macros described in MDF can be defined at three levels of detail.
A single instance can be defined with the `SRAMMacro` format.
A group of instances that share the number and type of ports but vary in width and depth can be defined with the `SRAMGroup` format.
A set of groups of SRAMs that can be generated together from a single source like a compiler can be defined with the `SRAMCompiler` format.

At the most concrete level the `SRAMMAcro` defines a particular instance of an SRAM.
That includes its functional attributes such as its width, depth, and number of access ports.
These ports can be read, write, or read and write ports, and the instance can have any number.
In order to correctly map these functional ports to the physical instance, each port is described in a list of sub-structures, in the parent instance's structure.
Each port is only required to have an address and data field, but can have many other optional fields.
These optional fields include a clock, write enable, read enable, chip enable, mask and its granularity.
The mask field can have a different granularity than the data field, e.g. it could be a bit mask or a byte mask.
Each field must also specify its polarity, whether it is active high or active low.

The specific JSON file format described above is `here <https://github.com/ucb-bar/plsi-mdf/blob/4be9b173647c77f990a542f4eb5f69af01d77316/macro_format.json>`_. A reference cache of SRAMs from the nangate45 technology library is `available here <https://github.com/ucb-bar/hammer/blob/8fd1486499b875d56f09b060f03a62775f0a6aa7/src/hammer-vlsi/technology/nangate45/sram-cache.json>`_.

In addition to these functional descriptions of the SRAM there are also other fields that specify physical/implementation characteristics.
These include the threshold voltage, the mux factor, as well as a list of extra non-functional ports.

The next level of detail, an `SRAMGroup` includes a range of depths and widths, as well as a set of threshold voltages.
A range has a lower bound, upper bound, and a step size.
The least concrete level, an `SRAMCompiler` is simply a set of `SRAMGroups`.

Separating the Top module from the TestHarness module
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Unlike the FireSim and Software simulation flows, a VLSI flow needs to separate the test harness and the chip (a.k.a. DUT) into separate files.
This is necessary to facilitate post-synthesis and post-place-and-route simulation, as the module names in the RTL and gate-level verilog files would collide.
Simulations, after your design goes through a VLSI flow, will use the verilog netlist generated from the flow and will need an untouched test harness to drive it.
Separating these components into separate files makes this straightforward.
Without the separation the file that included the test harness would also redefine the DUT which is often disallowed in simulation tools.
To do this, there is a FIRRTL ``App`` in :ref:`Tools/Barstools:Barstools` called ``GenerateTopAndHarness``, which runs the appropriate transforms to elaborate the modules separately.
This also renames modules in the test harness so that any modules that are instantiated in both the test harness and the chip are uniquified.

.. Note:: For VLSI projects, this ``App`` is run instead of the normal FIRRTL ``App`` to elaborate Verilog.

Macro Description Format
~~~~~~~~~~~~~~~~~~~~~~~~

The SRAM technology macros and IO cells are described in a json format called Macro Description Format (MDF).
MDF is specialized for each type of macro it supports.
The specialization is defined in their respective sections.



Mapping technology IO cells
~~~~~~~~~~~~~~~~~~~~~~~~~~~

Like technology SRAMs, IO cells are almost always included in digital ASIC designs to allow pin configurability, increase the voltage level of the IO signal, and provide ESD protection.
Unlike SRAMs, there is no corresponding primitive in Chisel or FIRRTL.
However, this problem can be solved similarly to ``SeqMems`` by leveraging the strong typing available in these scala-based tools.
We are actively developing a FIRRTL transform that will automatically configure, map, and connect technology IO cells.
Stay tuned for more information!

In the meantime, it is recommended that you instantiate the IO cells in your Chisel design.
This, unfortunately, breaks the process-agnostic RTL abstraction, so it is recommended that inclusion of these cells be configurable using the ``rocket-chip`` parameterization system.
The simplest way to do this is to have a config fragment that when included updates instantiates the IO cells and connects them in the test harness.
When simulating chip-specific designs, it is important to include the IO cells.
The IO cell behavioral models will often assert if they are connected incorrectly, which is a useful runtime check.
They also keep the IO interface at the chip and test harness boundary (see :ref:`Tools/Barstools:Separating the Top module from the TestHarness module`) consistent after synthesis and place-and-route,
which allows the RTL simulation test harness to be reused.
