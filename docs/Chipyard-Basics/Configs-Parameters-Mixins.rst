Configs, Parameters, Mixins, and Everything In Between
========================================================

A significant portion of generators in the Chipyard framework use the Rocket Chip parameter system.
This parameter system enables for the flexible configuration of the SoC without invasive RTL changes.
In order to use the parameter system correctly, we will use several terms and conventions:

Parameters
--------------------

It is important to note that a significant challenge with the Rocket parameter system is being able to identify the correct parameter to use, and the impact that parameter has on the overall system.
We are still investigating methods to facilitate parameter exploration and discovery.

Configs
---------------------

A *config* is a collection of multiple generator parameters being set to specific values.
Configs are additive, can override each other, and can be composed of other configs (sometimes referred to as config fragments).
The naming convention for an additive config or config fragment is ``With<YourConfigName>``, while the naming convention for a non-additive config will be ``<YourConfig>``.
Configs can take arguments which will in-turn set parameters in the design or reference other parameters in the design (see :ref:`Chipyard-Basics/Configs-Parameters-Mixins:Parameters`).

This example shows a basic config fragment class that takes in zero arguments and instead uses hardcoded values to set the RTL design parameters.
In this example, ``MyAcceleratorConfig`` is a Scala case class that defines a set of variables that the generator can use when referencing the ``MyAcceleratorKey`` in the design.

.. _basic-config-example:
.. code-block:: scala

  class WithMyAcceleratorParams extends Config((site, here, up) => {
    case BusWidthBits => 128
    case MyAcceleratorKey =>
      MyAcceleratorConfig(
        rows = 2,
        rowBits = 64,
        columns = 16,
        hartId = 1,
        someLength = 256)
  })

This next example shows a "higher-level" additive config fragment that uses prior parameters that were set to derive other parameters.

.. _complex-config-example:
.. code-block:: scala

  class WithMyMoreComplexAcceleratorConfig extends Config((site, here, up) => {
    case BusWidthBits => 128
    case MyAcceleratorKey =>
      MyAcceleratorConfig(
        Rows = 2,
        rowBits = site(SystemBusKey).beatBits,
        hartId = up(RocketTilesKey, site).length)
  })

The following example shows a non-additive config that combines or "assembles" the prior two config fragments using ``++``.
The additive config fragments are applied from the right to left in the list (or bottom to top in the example).
Thus, the order of the parameters being set will first start with the ``DefaultExampleConfig``, then ``WithMyAcceleratorParams``, then ``WithMyMoreComplexAcceleratorConfig``.

.. _top-level-config:
.. code-block:: scala

  class SomeAdditiveConfig extends Config(
    new WithMyMoreComplexAcceleratorConfig ++
    new WithMyAcceleratorParams ++
    new DefaultExampleConfig
  )

The ``site``, ``here``, and ``up`` objects in ``WithMyMoreComplexAcceleratorConfig`` are maps from configuration keys to their definitions.
The ``site`` map gives you the definitions as seen from the root of the configuration hierarchy (in this example, ``SomeAdditiveConfig``).
The ``here`` map gives the definitions as seen at the current level of the hierarchy (i.e. in ``WithMyMoreComplexAcceleratorConfig`` itself).
The ``up`` map gives the definitions as seen from the next level up from the current (i.e. from ``WithMyAcceleratorParams``).

Cake Pattern / Mixin
-------------------------

A cake pattern or mixin is a Scala programming pattern, which enable "mixing" of multiple traits or interface definitions (sometimes referred to as dependency injection).
It is used in the Rocket Chip SoC library and Chipyard framework in merging multiple system components and IO interfaces into a large system component.

This example shows the Chipyard default top that composes multiple traits together into a fully-featured SoC with many optional components.


.. literalinclude:: ../../generators/chipyard/src/main/scala/DigitalTop.scala
    :language: scala
    :start-after: DOC include start: DigitalTop
    :end-before: DOC include end: DigitalTop


There are two "cakes" or mixins here. One for the lazy module (ex. ``CanHavePeripheryTLSerial``) and one for the lazy module
implementation (ex. ``CanHavePeripheryTLSerialModuleImp`` where ``Imp`` refers to implementation). The lazy module defines
all the logical connections between generators and exchanges configuration information among them, while the
lazy module implementation performs the actual Chisel RTL elaboration.

In the ``DigitalTop`` example class, the "outer" ``DigitalTop`` instantiates the "inner"
``DigitalTopModule`` as a lazy module implementation. This delays immediate elaboration
of the module until all logical connections are determined and all configuration information is exchanged.
The ``System`` outer base class, as well as the
``CanHavePeriphery<X>`` outer traits contain code to perform high-level logical
connections. For example, the ``CanHavePeripheryTLSerial`` outer trait contains code
to optionally lazily instantiate the ``TLSerdesser``, and connect the ``TLSerdesser`` 's
TileLink node to the Front bus.

The ``ModuleImp`` classes and traits perform elaboration of real RTL.

In the test harness, the SoC is elaborated with
``val dut = p(BuildTop)(p)``.

After elaboration, the system submodule of ``ChipTop`` will be a ``DigitalTop`` module, which contains a
``TLSerdesser`` module (among others), if the config specified for that block to be instantiated.

From a high level, classes which extend ``LazyModule`` *must* reference
their module implementation through ``lazy val module``, and they
*may* optionally reference other lazy modules (which will elaborate
as child modules in the module hierarchy). The "inner" modules
contain the implementation for the module, and may instantiate
other normal modules OR lazy modules (for nested Diplomacy
graphs, for example).

The naming convention for an additive mixin or trait is ``CanHave<YourMixin>``.
This is shown in the ``Top`` class where things such as ``CanHavePeripheryTLSerial`` connect a RTL component to a bus and expose signals to the top-level.

Additional References
---------------------------

Another description of traits/mixins and config fragments is given in :ref:`Customization/Keys-Traits-Configs:Keys, Traits, and Configs`.
Additionally, a brief explanation of some of these topics (with slightly different naming) is given in the following video: https://www.youtube.com/watch?v=Eko86PGEoDY.

.. Note:: Chipyard uses the name "config fragments" over "config mixins" to avoid confusion between a mixin applying to a config or to the system ``Top`` (even though both are technically Scala mixins).
