.. _incorporating-verilog-blocks:

Incorporating Verilog Blocks
============================

Working with existing Verilog IP is an integral part of many chip
design flows. Fortunately, both Chisel and Chipyard provide extensive
support for Verilog integration.

Here, we will examine the process of incorporating an MMIO peripheral
(similar to the PWM example from the previous section) that uses a
Verilog implementation of Greatest Common Denominator (GCD)
algorithm. There are a few steps to adding a Verilog peripheral:

* Adding a Verilog resource file to the project
* Defining a Chisel ``BlackBox`` representing the Verilog module
* Instantiating the ``BlackBox`` and interfacing ``RegField`` entries
* Setting up a chip ``Top`` and ``Config`` that use the peripheral

Adding a Verilog Blackbox Resource File
---------------------------------------

As before, it is possible to incorporate peripherals as part of your
own generator project. However, Verilog resource files must go in a
different directory from Chisel (Scala) sources.

.. code-block:: none

    generators/yourproject/
        build.sbt
        src/main/
            scala/
            resources/
                vsrc/
                    YourFile.v

In addition to the steps outlined in the previous section on adding a
project to the ``build.sbt`` at the top level, it is also necessary to
add any projects that contain Verilog IP as dependencies to the
``tapeout`` project. This ensures that the Verilog sources are visible
to the downstream FIRRTL passes that provide utilities for integrating
Verilog files into the build process, which are part of the
``tapeout`` package in ``barstools/tapeout``.

.. code-block:: scala

    lazy val tapeout = conditionalDependsOn(project in file("./tools/barstools/tapeout/"))
      .dependsOn(chisel_testers, example, yourproject)
      .settings(commonSettings)

For this concrete GCD example, we will be using a ``GCDMMIOBlackBox``
Verilog module that is defined in the ``example`` project. The Scala
and Verilog sources follow the prescribed directory layout.

.. code-block:: none

    generators/example/
        build.sbt
        src/main/
            scala/
                GCDMMIOBlackBox.scala
            resources/
                vsrc/
                    GCDMMIOBlackBox.v
      
Defining a Chisel BlackBox
--------------------------

A Chisel ``BlackBox`` module provides a way of instantiating a module
defined by an external Verilog source. The definition of the blackbox
includes several aspects that allow it to be translated to an instance
of the Verilog module:

* An ``io`` field: a bundle with fields corresponding to the portlist of the Verilog module.
* A constructor parameter that takes a ``Map`` from Verilog parameter name to elaborated value
* One or more resources added to indicate Verilog source dependencies

Of particular interest is the fact that parameterized Verilog modules
can be passed the full space of possible parameter values. These
values may depend on elaboration-time values in the Chisel generator,
as the bitwidth of the GCD calculation does in this example.
  
**Verilog GCD port list and parameters**

.. literalinclude:: ../../generators/example/src/main/resources/vsrc/GCDMMIOBlackBox.v
    :language: verilog
    :start-after: DOC include start: GCD portlist
    :end-before: DOC include end: GCD portlist

**Chisel BlackBox Definition**

.. literalinclude:: ../../generators/example/src/main/scala/GCDMMIOBlackBox.scala
    :language: scala
    :start-after: DOC include start: GCD blackbox
    :end-before: DOC include end: GCD blackbox

Instantiating the BlackBox and Defining MMIO
--------------------------------------------

Next, we must instantiate the blackbox. In order to take advantage of
diplomatic memory mapping on the system bus, we still have to
integrate the peripheral at the Chisel level by mixing
peripheral-specific traits into a ``TLRegisterRouter``. The ``params``
member and ``HasRegMap`` base trait should look familiar from the
previous memory-mapped PWM device example.

.. literalinclude:: ../../generators/example/src/main/scala/GCDMMIOBlackBox.scala
    :language: scala
    :start-after: DOC include start: GCD instance regmap
    :end-before: DOC include end: GCD instance regmap

Advanced Features of RegField Entries
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

One significant difference from the PWM example is in the peripheral's
memory map. ``RegField`` exposes polymorphic ``r`` and ``w`` methods
that allow read- and write-only memory-mapped registers to be
interfaced to hardware in multiple ways.

* ``RegField.r(2, status)`` is used to create a 2-bit, read-only register that captures the current value of the ``status`` signal when read.
* ``RegField.r(params.width, gcd)`` "connects" the decoupled handshaking interface ``gcd`` to a read-only memory-mapped register. When this register is read via MMIO, the ``ready`` signal is asserted. This is in turn connected to ``output_ready`` on the Verilog blackbox through the glue logic.
* ``RegField.w(params.width, x)`` exposes a plain register (much like those in the PWM example) via MMIO, but makes it write-only.
* ``RegField.w(params.width, y)`` associates the decoupled interface signal ``y`` with a write-only memory-mapped register, causing ``y.valid`` to be asserted when the register is written.

Since the ready/valid signals of ``y`` are connected to the
``input_ready`` and ``input_valid`` signals of the blackbox,
respectively, this register map and glue logic has the effect of
triggering the GCD algorithm when ``y`` is written. Therefore, the
algorithm is set up by first writing ``x`` and then performing a
triggering write to ``y``. Polling can be used for status checks.

Defining a Chip with a GCD Peripheral
---------------------------------------

As with the PWM example, a few more pieces are needed to tie the system together.

**Composing traits into a complete cake pattern peripheral**

.. literalinclude:: ../../generators/example/src/main/scala/GCDMMIOBlackBox.scala
    :language: scala
    :start-after: DOC include start: GCD cake
    :end-before: DOC include end: GCD cake

Note the differences arising due to the fact that this peripheral has
no top-level IO. To build a complete system, a new ``Top`` and new
``Config`` objects are added in a manner exactly analogous to the PWM
example.

Software Testing
----------------

The GCD module has a slightly more complex interface, so polling is
used to check the status of the device before each triggering read or
write.

.. literalinclude:: ../../tests/gcd.c
    :language: scala
    :start-after: DOC include start: GCD test
    :end-before: DOC include end: GCD test

Support for Verilog Within Chipyard Tool Flows
----------------------------------------------

There are important differences in how Verilog blackboxes are treated
by various flows within the Chipyard framework. Some flows within
Chipyard rely on FIRRTL in order to provide robust, non-invasive
transformations of source code. Since Verilog blackboxes remain
blackboxes in FIRRTL, their ability to be processed by FIRRTL
transforms is limited, and some advanced features of Chipyard may
provide weaker support for blackboxes. Note that the remainder of the
design (the "non-Verilog" part of the design) may still generally be
transformed or augmented by any Chipyard FIRRTL transform.

* Verilog blackboxes are fully supported for generating tapeout-ready RTL
* HAMMER workflows offer robust support for integrating Verilog blackboxes
* FireSim relies on FIRRTL transformations to generate a decoupled
  FPGA simulator. Therefore, support for Verilog blackboxes in FireSim
  is currently limited but rapidly evolving. Stay tuned!
* Custom FIRRTL transformations and analyses may sometimes be able to
  handle blackbox Verilog, depending on the mechanism of the
  particular transform

As mentioned earlier in this section, ``BlackBox`` resource files must
be integrated into the build process, so any project providing
``BlackBox`` resources must be made visible to the ``tapeout`` project
in ``build.sbt``
