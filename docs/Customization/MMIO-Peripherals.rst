.. _mmio-accelerators:

MMIO Peripherals
==================

The easiest way to create a MMIO peripheral is to use the ``TLRegisterRouter`` or ``AXI4RegisterRouter`` widgets, which abstracts away the details of handling the interconnect protocols and provides a convenient interface for specifying memory-mapped registers. Since Chipyard and Rocket Chip SoCs primarily use Tilelink as the on-chip interconnect protocol, this section will primarily focus on designing Tilelink-based peripherals. However, see ``generators/chipyard/src/main/scala/example/GCD.scala`` for how an example AXI4 based peripheral is defined and connected to the Tilelink graph through converters.

To create a RegisterRouter-based peripheral, you will need to specify a parameter case class for the configuration settings, a bundle trait with the extra top-level ports, and a module implementation containing the actual RTL.

For this example, we will show how to connect a MMIO peripheral which computes the GCD.
The full code can be found in ``generators/chipyard/src/main/scala/example/GCD.scala``.

In this case we use a submodule ``GCDMMIOChiselModule`` to actually perform the GCD. The ``GCDModule`` class only creates the registers and hooks them up using ``regmap``.

.. literalinclude:: ../../generators/chipyard/src/main/scala/example/GCD.scala
    :language: scala
    :start-after: DOC include start: GCD chisel
    :end-before: DOC include end: GCD chisel

.. literalinclude:: ../../generators/chipyard/src/main/scala/example/GCD.scala
    :language: scala
    :start-after: DOC include start: GCD instance regmap
    :end-before: DOC include end: GCD instance regmap

Advanced Features of RegField Entries
-------------------------------------

``RegField`` exposes polymorphic ``r`` and ``w`` methods
that allow read- and write-only memory-mapped registers to be
interfaced to hardware in multiple ways.

* ``RegField.r(2, status)`` is used to create a 2-bit, read-only register that captures the current value of the ``status`` signal when read.
* ``RegField.r(params.width, gcd)`` "connects" the decoupled handshaking interface ``gcd`` to a read-only memory-mapped register. When this register is read via MMIO, the ``ready`` signal is asserted. This is in turn connected to ``output_ready`` on the GCD module through the glue logic.
* ``RegField.w(params.width, x)`` exposes a plain register via MMIO, but makes it write-only.
* ``RegField.w(params.width, y)`` associates the decoupled interface signal ``y`` with a write-only memory-mapped register, causing ``y.valid`` to be asserted when the register is written.

Since the ready/valid signals of ``y`` are connected to the
``input_ready`` and ``input_valid`` signals of the GCD module,
respectively, this register map and glue logic has the effect of
triggering the GCD algorithm when ``y`` is written. Therefore, the
algorithm is set up by first writing ``x`` and then performing a
triggering write to ``y``. Polling can be used for status checks.


Connecting by TileLink
----------------------

Once you have these classes, you can construct the final peripheral by extending the ``TLRegisterRouter`` and passing the proper arguments.
The first set of arguments determines where the register router will be placed in the global address map and what information will be put in its device tree entry.
The second set of arguments is the IO bundle constructor, which we create by extending ``TLRegBundle`` with our bundle trait.
The final set of arguments is the module constructor, which we create by extends ``TLRegModule`` with our module trait.
Notice how we can create an analogous AXI4 version of our peripheral.

.. literalinclude:: ../../generators/chipyard/src/main/scala/example/GCD.scala
    :language: scala
    :start-after: DOC include start: GCD router
    :end-before: DOC include end: GCD router



Top-level Traits
----------------

After creating the module, we need to hook it up to our SoC.
Rocket Chip accomplishes this using the cake pattern.
This basically involves placing code inside traits.
In the Rocket Chip cake, there are two kinds of traits: a ``LazyModule`` trait and a module implementation trait.

The ``LazyModule`` trait runs setup code that must execute before all the hardware gets elaborated.
For a simple memory-mapped peripheral, this just involves connecting the peripheral's TileLink node to the MMIO crossbar.

.. literalinclude:: ../../generators/chipyard/src/main/scala/example/GCD.scala
    :language: scala
    :start-after: DOC include start: GCD lazy trait
    :end-before: DOC include end: GCD lazy trait

Note that the ``GCDTL`` class we created from the register router is itself a ``LazyModule``.
Register routers have a TileLink node simply named "node", which we can hook up to the Rocket Chip bus.
This will automatically add address map and device tree entries for the peripheral.
Also observe how we have to place additional AXI4 buffers and converters for the AXI4 version of this peripheral.

For peripherals which instantiate a concrete module, or which need to be connected to concrete IOs or wires, a matching concrete trait is necessary. We will make our GCD example output a ``gcd_busy`` signal as a top-level port to demonstrate. In the concrete module implementation trait, we instantiate the top level IO (a concrete object) and wire it to the IO of our lazy module.


.. literalinclude:: ../../generators/chipyard/src/main/scala/example/GCD.scala
    :language: scala
    :start-after: DOC include start: GCD imp trait
    :end-before: DOC include end: GCD imp trait

Constructing the DigitalTop and Config
--------------------------------------

Now we want to mix our traits into the system as a whole.
This code is from ``generators/chipyard/src/main/scala/DigitalTop.scala``.

.. literalinclude:: ../../generators/chipyard/src/main/scala/DigitalTop.scala
    :language: scala
    :start-after: DOC include start: DigitalTop
    :end-before: DOC include end: DigitalTop

Just as we need separate traits for ``LazyModule`` and module implementation, we need two classes to build the system.
The ``DigitalTop`` class contains the set of traits which parameterize and define the ``DigitalTop``. Typically these traits will optionally add IOs or peripherals to the ``DigitalTop``.
The ``DigitalTop`` class includes the pre-elaboration code and also a ``lazy val`` to produce the module implementation (hence ``LazyModule``).
The ``DigitalTopModule`` class is the actual RTL that gets synthesized.



And finally, we create a configuration class in ``generators/chipyard/src/main/scala/config/MMIOAcceleratorConfigs.scala`` that uses the ``WithGCD`` config fragment defined earlier.

.. literalinclude:: ../../generators/chipyard/src/main/scala/example/GCD.scala
    :language: scala
    :start-after: DOC include start: GCD config fragment
    :end-before: DOC include end: GCD config fragment

.. literalinclude:: ../../generators/chipyard/src/main/scala/config/MMIOAcceleratorConfigs.scala
    :language: scala
    :start-after: DOC include start: GCDTLRocketConfig
    :end-before: DOC include end: GCDTLRocketConfig

Testing
-------

Now we can test that the GCD is working. The test program is in ``tests/gcd.c``.

.. literalinclude:: ../../tests/gcd.c
    :language: c

This just writes out to the registers we defined earlier.
The base of the module's MMIO region is at 0x2000 by default.
This will be printed out in the address map portion when you generate the Verilog code.
You can also see how this changes the emitted ``.json`` addressmap files in ``generated-src``.

Compiling this program with ``make`` produces a ``gcd.riscv`` executable.

Now with all of that done, we can go ahead and run our simulation.

.. code-block:: shell

    cd sims/verilator
    make CONFIG=GCDTLRocketConfig BINARY=../../tests/gcd.riscv run-binary
