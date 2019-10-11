.. _build-a-chip:

Building A Chip
===============

In this section, we will discuss many of the ASIC-specific transforms and methodologies within Chipyard.
For the full documentation on how to use the VLSI tool flow, see the `Hammer Documentation <https://hammer-vlsi.readthedocs.io/>`__.

Transforming the RTL
--------------------


Mapping technology SRAMs
~~~~~~~~~~~~~~~~~~~~~~~~

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
A list of unique SRAM configurations is output to a ``.conf`` file by FIRRTL, which is used to map technology SRAMs.
Without this transform, FIRRTL will map all ``SeqMem``s to flip-flop arrays with equivalent behavior.
With very high capacity ``SeqMem``s, this may lead to a design that is difficult to route.

The ``.conf`` file is consumed by a tool called MacroCompiler, which is part of the :ref:`Barstools` scala package.
MacroCompiler is also passed an ``.mdf`` file that describes the available list of technology SRAMs or the capabilities of the SRAM compiler, if one is provided by the foundry.
Using a user-customizable cost function, MacroCompiler will select the SRAMs that are the best fit for each dimensionality in the ``.conf`` file.
This may include overprovisioning (e.g. using a 64x1024 SRAM for a requested 60x1024, if the latter is not available) or arraying.
Arraying can be done in both width and depth, as well as to solve masking constraints.
For example, a 128x2048 array could be composed of four 64x1024 arrays, with two macros in parallel to create two 128x1024 virtual SRAMs which are combinationally muxed to add depth.
If this macro requires byte-granularity write masking, but no technology SRAMs support masking, then the tool may choose to use thirty-two 8x1024 arrays in a similar configuration.
For information on writing ``.mdf`` files, look at `MDF on github <https://github.com/ucb-bar/plsi-mdf>`__.

The output of MacroCompiler is a Verilog file containing modules that wrap the technology SRAMs into the specified interface names from the ``.conf``.
If the technology supports an SRAM compiler, then MacroCompiler will also emit HammerIR that can be passed to Hammer to run the compiler itself and generate design collateral.
Documentation for SRAM compilers is forthcoming.

Mapping technology IO cells
~~~~~~~~~~~~~~~~~~~~~~~~~~~

Like technology SRAMs, IO cells are almost always included in digital ASIC designs to allow pin configurability, increase the voltage level of the IO signal, and provide ESD protection.
Unlike SRAMs, there is no corresponding primitive in Chisel or FIRRTL.
However, this problem can be solved similarly to ``SeqMem``s by leveraging the strong typing available in these scala-based tools.
We are actively developing a FIRRTL transform that will automatically configure, map, and connect technology IO cells.
Stay tuned for more information!

In the meantime, it is recommended that you instantiate the IO cells in your Chisel design.
This, unfortunately, breaks the process-agnostic RTL abstraction, so it is recommended that inclusion of these cells be configurable using the ``rocket-chip`` parameterization system.
When simulating chip-specific deisgns, it is important to include the IO cells.
The IO cell behavioral models will often assert if they are connected incorrectly, which is a useful runtime check.
They also keep the IO interface at the chip and test harness boundary (see :ref:`Separating the top module from the test harness`) consistent after synthesis and place-and-route,
which allows the RTL simulation test harness to be reused.

Separating the top module from the test harness
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Unlike the FireSim and Software simulation flows, we need to separate the test harness and the chip (a.k.a. DUT) into separate files.
This is necessary to facilitate post-synthesis and post-place-and-route simulation, as the module names in the RTL and gate-level verilog files would collide.
To do this, there is a FIRRTL ``App`` in :ref:`Barstools` called ``GenerateTopAndHarness``, which runs the appropriate transforms to elaborate the modules separately.
This also renames modules in the test harness so that any modules that are instantiated in both the test harness and the chip are uniquified.

.. Note:: For VLSI projects, this ``App`` is run instead of the normal FIRRTL ``App`` to elaborate Verilog.


Modifying the logical hierarchy
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Something about complex chips -> needs hierarchical
Logical hierarchy is not always easy to describe with the desired physical hierarchy.

TODO finish me


Creating a floorplan
--------------------

An ASIC floorplan is a specification that the place-and-route tools will follow when placing instances in the design.
This includes the top-level chip dimensions, placement of SRAM macros, placement of custom (analog) circuits, IO cell placement, bump or wirebond pad placement, blockages, hierarchical boundaries, and pin placement.

Much of the design effort that goes into building a chip involves developing optimal floorplans for the instance of the design that is being manufactured.
Often this is a highly manual and iterative process which consumes much of the physical designer's time.
This cost becomes increasingly apparent as the parameterization space grows rapidly when using tools like Chisel- cycle times are hampered by the human labor
that is required to floorplan each instance of the design.
The Hammer team is actively developing methods of improving the agility of floorplanning for generator-based designs, like those that use Chisel.
The libraries we are developing will emit Hammer IR that can be passed directly to the Hammer tool without the need for human intervention.
Stay tuned for more information.

In the meantime, see the `Hammer Documentation <https://hammer-vlsi.readthedocs.io/>`__ for information on the Hammer IR floorplan API.
It is possible to write this IR directly, or to generate it using simple python scripts.
While we certainly look forward to having a more featureful toolkit, we have built many chips to date in this way.


Running the VLSI flow
---------------------

For the full documentation on how to use the VLSI tool flow, see the `Hammer Documentation <https://hammer-vlsi.readthedocs.io/>`__.
For an example of how to use the VLSI in the context of Chipyard, see :ref:`ASAP7 Tutorial`.


