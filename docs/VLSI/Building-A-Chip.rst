.. _build-a-chip:

Building A Chip
===============

In this section, we will discuss many of the ASIC-specific transforms and methodologies within Chipyard.
For the full documentation on how to use the VLSI tool flow, see the `Hammer Documentation <https://hammer-vlsi.readthedocs.io/>`__.

Transforming the RTL
--------------------

Building a chip requires specializing the generic verilog emitted by FIRRTL to adhere to the constraints imposed by the technology used for fabrication.
This includes mapping Chisel memories to available technology macros such as SRAMs, mapping the input and output of your chip to connect to technology IO cells, see :ref:`Tools/Barstools:Barstools`.
In addition to these required transformations, it may also be beneficial to transform the RTL to make it more amenable to hierarchical physical design easier.
This often includes modifying the logical hierarchy to match the physical hierarchy through grouping components together or flattening components into a single larger module.


Modifying the logical hierarchy
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Building a large or complex chip often requires using hierarchical design to place and route sections of the chip separately.
In addition, the design as written in Chipyard may not have a hierarchy that matches the physical hierarchy that would work best in the place and route tool.
In order to reorganize the design to have its logical hierarchy match its physical hierarchy there are several FIRRTL transformations that can be run.
These include grouping, which pull several modules into a larger one, and flattening, which dissolves a modules boundary leaving its components in its containing module.
These transformations can be applied repeatedly to different parts of the design to arrange it as the physical designer sees fit.
More details on how to use these transformations to reorganize the design hierarchy are forthcoming.


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


Running the VLSI tool flow
--------------------------

For the full documentation on how to use the VLSI tool flow, see the `Hammer Documentation <https://hammer-vlsi.readthedocs.io/>`__.
For an example of how to use the VLSI in the context of Chipyard, see :ref:`VLSI/ASAP7-Tutorial:ASAP7 Tutorial`.


