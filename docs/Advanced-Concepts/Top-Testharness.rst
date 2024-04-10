Tops, Test-Harnesses, and the Test-Driver
===========================================

The three highest levels of hierarchy in a Chipyard
SoC are the ``ChipTop`` (DUT), ``TestHarness``, and the ``TestDriver``.
The ``ChipTop`` and ``TestHarness`` are both emitted by Chisel generators.
The ``TestDriver`` serves as our testbench, and is a Verilog
file in Rocket Chip.


ChipTop/DUT
-------------------------

``ChipTop`` is the top-level module that instantiates the ``System`` submodule, usually an instance of the concrete class ``DigitalTop``.
The vast majority of the design resides in the ``System``.
Other components that exist inside the ``ChipTop`` layer are generally IO cells, clock receivers and multiplexers, reset synchronizers, and other analog IP that needs to exist outside of the ``System``.
The ``IOBinders`` are responsible for instantiating the IO cells for ``ChipTop`` IO that correspond to IO of the ``System``.
The ``HarnessBinders`` are responsible for instantiating test harness collateral that connects to the ``ChipTop`` ports.
Most types of devices and testing collateral can be instantiated using custom ``IOBinders`` and ``HarnessBinders``.

Custom ChipTops
^^^^^^^^^^^^^^^^^^^^^^^^^

The default standard ``ChipTop`` provides a mimimal, barebones template for ``IOBinders`` to generate IOCells around ``DigitalTop`` traits.
For tapeouts, integrating Analog IP, or other non-standard use cases, Chipyard supports specifying a custom ``ChipTop`` using the ``BuildTop`` key.
An example of a custom ChipTop which uses non-standard IOCells is provided in `generators/chipyard/src/main/scala/example/CustomChipTop.scala <https://github.com/ucb-bar/chipyard/blob/main/generators/chipyard/src/main/scala/example/CustomChipTop.scala>`__


System/DigitalTop
-------------------------

The system module of a Rocket Chip SoC is composed via cake-pattern.
Specifically, ``DigitalTop`` extends a ``System``, which extends a ``Subsystem``, which extends a ``BaseSubsystem``.


BaseSubsystem
^^^^^^^^^^^^^^^^^^^^^^^^^

The ``BaseSubsystem`` is defined in ``generators/rocketchip/src/main/scala/subsystem/BaseSubsystem.scala``.
Looking at the ``BaseSubsystem`` abstract class, we see that this class instantiates the top-level buses
(frontbus, systembus, peripherybus, etc.), but does not specify a topology.
We also see this class define several ``ElaborationArtefacts``, files emitted after Chisel elaboration
(e.g. the device tree string, and the diplomacy graph visualization GraphML file).

Subsystem
^^^^^^^^^^^^^^^^^^^^^^^^^

Looking in `generators/chipyard/src/main/scala/Subsystem.scala <https://github.com/ucb-bar/chipyard/blob/main/generators/chipyard/src/main/scala/Subsystem.scala>`__, we can see how Chipyard's ``Subsystem``
extends the ``BaseSubsystem`` abstract class. ``Subsystem`` mixes in the ``HasBoomAndRocketTiles`` trait that
defines and instantiates BOOM or Rocket tiles, depending on the parameters specified.
We also connect some basic IOs for each tile here, specifically the hartids and the reset vector.

System
^^^^^^^^^^^^^^^^^^^^^^^^^

``generators/chipyard/src/main/scala/System.scala`` completes the definition of the ``System``.

- ``HasHierarchicalBusTopology`` is defined in Rocket Chip, and specifies connections between the top-level buses
- ``HasAsyncExtInterrupts`` and ``HasExtInterruptsModuleImp`` adds IOs for external interrupts and wires them appropriately to tiles
- ``CanHave...AXI4Port`` adds various Master and Slave AXI4 ports, adds TL-to-AXI4 converters, and connects them to the appropriate buses
- ``HasPeripheryBootROM`` adds a BootROM device

Tops
^^^^^^^^^^^^^^^^^^^^^^^^^

A SoC Top then extends the ``System`` class with traits for custom components.
In Chipyard, this includes things like adding a NIC, UART, and GPIO as well as setting up the hardware for the bringup method.
Please refer to :ref:`Advanced-Concepts/Chip-Communication:Communicating with the DUT` for more information on these bringup methods.

TestHarness
-------------------------

The wiring between the ``TestHarness`` and the Top are performed in methods defined in traits added to the Top.
When these methods are called from the ``TestHarness``, they may instantiate modules within the scope of the harness,
and then connect them to the DUT. For example, the ``connectSimAXIMem`` method defined in the
``CanHaveMasterAXI4MemPortModuleImp`` trait, when called from the ``TestHarness``, will instantiate ``SimAXIMems``
and connect them to the correct IOs of the top.

While this roundabout way of attaching to the IOs of the top may seem to be unnecessarily complex, it allows the designer to compose
custom traits together without having to worry about the details of the implementation of any particular trait.

TestDriver
-------------------------

The ``TestDriver`` is defined in ``generators/rocketchip/src/main/resources/vsrc/TestDriver.v``.
This Verilog file executes a simulation by instantiating the ``TestHarness``, driving the clock and reset signals, and interpreting the success output.
This file is compiled with the generated Verilog for the ``TestHarness`` and the ``Top`` to produce a simulator.
