.. _incorporating-verilog-blocks:

Incorporating Verilog Blocks
============================

Working with existing Verilog IP is an integral part of many chip
design flows. Fortunately, both Chisel and Chipyard provide extensive
support for Verilog integration.

Here, we will examine the process of incorporating an MMIO peripheral
that uses a Verilog implementation of Greatest Common Denominator (GCD)
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
Verilog module that is defined in the ``chipyard`` project. The Scala
and Verilog sources follow the prescribed directory layout.

.. code-block:: none

    generators/chipyard/
        build.sbt
        src/main/
            scala/
                example/
                    GCD.scala
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

.. literalinclude:: ../../generators/chipyard/src/main/resources/vsrc/GCDMMIOBlackBox.v
    :language: Verilog
    :start-after: DOC include start: GCD portlist
    :end-before: DOC include end: GCD portlist

**Chisel BlackBox Definition**

.. literalinclude:: ../../generators/chipyard/src/main/scala/example/GCD.scala
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
previous memory-mapped GCD device example.

.. literalinclude:: ../../generators/chipyard/src/main/scala/example/GCD.scala
    :language: scala
    :start-after: DOC include start: GCD instance regmap
    :end-before: DOC include end: GCD instance regmap


Defining a Chip with a BlackBox
---------------------------------------

Since we've parameterized the GCD instantiation to choose between the
Chisel and the Verilog module, creating a config is easy.

.. literalinclude:: ../../generators/chipyard/src/main/scala/config/RocketConfigs.scala
    :language: scala
    :start-after: DOC include start: GCDAXI4BlackBoxRocketConfig
    :end-before: DOC include end: GCDAXI4BlackBoxRocketConfig

You can play with the parameterization of the mixin to choose a TL/AXI4, BlackBox/Chisel
version of the GCD.

Software Testing
----------------

The GCD module has a more complex interface, so polling is
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
