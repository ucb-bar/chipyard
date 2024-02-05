.. _mmio-accelerators:

MMIO Peripherals
==================

The easiest way to create a MMIO peripheral is to follow the GCD TileLink MMIO example. Since Chipyard and Rocket Chip SoCs primarily use Tilelink as the on-chip interconnect protocol, this section will primarily focus on designing Tilelink-based peripherals. However, see ``generators/chipyard/src/main/scala/example/GCD.scala`` for how an example AXI4 based peripheral is defined and connected to the Tilelink graph through converters.

To create a MMIO-mapped peripheral, you will need to specify a ``LazyModule`` wrapper containing the TileLink port as a Diplomacy Node, as well as an internal ``LazyModuleImp`` class that defines the MMIO's implementation and any non-TileLink I/O.

For this example, we will show how to connect a MMIO peripheral which computes the GCD.
The full code can be found in ``generators/chipyard/src/main/scala/example/GCD.scala``.

In this case we use a submodule ``GCDMMIOChiselModule`` to actually perform the GCD. The ``GCDTL`` and ``GCDAXI4`` classes are the ``LazyModule`` classes which construct the TileLink or AXI4 ports, wrapping the inner ``GCDMMIOChiselModule``.
The ``node`` object is a Diplomacy node, which connects the peripheral to the Diplomacy interconnect graph.

.. literalinclude:: ../../generators/chipyard/src/main/scala/example/GCD.scala
    :language: scala
    :start-after: DOC include start: GCD chisel
    :end-before: DOC include end: GCD chisel

.. literalinclude:: ../../generators/chipyard/src/main/scala/example/GCD.scala
    :language: scala
    :start-after: DOC include start: GCD router
    :end-before: DOC include end: GCD router


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

.. literalinclude:: ../../generators/chipyard/src/main/scala/example/GCD.scala
    :language: scala
    :start-after: DOC include start: GCD instance regmap
    :end-before: DOC include end: GCD instance regmap

.. note::
   In older versions of Chipyard and Rocket-Chip, a ``TLRegisterRouter`` abstrat
   class was used to abstract away the construction of the ``TLRegisterNode`` and
   ``LazyModule`` classes necessary to construct MMIO peripherals. This was removed,
   in favor of requiring users to explicitly construct the necessary classes.

   This matches more closely how standard ``Modules`` and ``LazyModules`` are
   constructed, making it clearer how a MMIO peripheral fits into the ``Module``
   and ``LazyModule`` design patterns.


Connecting by TileLink
----------------------

The key to connecting to the TileLink Diplomatic graph is the construction of the TileLink node for this peripheral.
In this case, since the peripheral acts as a manager of some register-mapped address space, it uses the ``TLRegisterNode`` object.
The parameters to the ``TLRegisterNode`` object specify the size of the managed space, the base address, and the port width.

Within the register-mapped peripheral, the control registers can be mapped using the ``node.regmap`` function, as described above.
A similar procedure is followed for both AXI4 and TileLin peripherals.

.. literalinclude:: ../../generators/chipyard/src/main/scala/example/GCD.scala
    :language: scala
    :start-after: DOC include start: GCD router
    :end-before: DOC include end: GCD router



Top-level Traits
----------------

After creating the module, we need to hook it up to our SoC.
The ``LazyModule`` abstract class containst the TileLink node representing the peripheral's I/O.
For a simple memory-mapped peripheral, connecting the peripheral's TileLink node must be connected to the relevant bu.

.. literalinclude:: ../../generators/chipyard/src/main/scala/example/GCD.scala
    :language: scala
    :start-after: DOC include start: GCD lazy trait
    :end-before: DOC include end: GCD lazy trait

Also observe how we have to place additional AXI4 buffers and converters for the AXI4 version of this peripheral.

Peripherals which expose I/O can use `InModuleBody` to punch their I/O to the `DigitalTop` module.
In this example, the GCD module's ``gcd_busy`` signal is exposed as a I/O of DigitalTop.

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
