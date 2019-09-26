Configs, Parameters, Mix-ins, and Everything In Between
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

A *Config* is a collection of multiple generator parameters being set to specific values.
Configs are additive, can override each other, and can be composed of other Configs.
The naming convention for an additive Config is ``With<YourConfigName>``, while the naming convention for a non-additive Config will be ``<YourConfig>``.
Configs can take arguments which will in-turn set parameters in the design or reference other parameters in the design (see :ref:`Parameters`).

This example shows a basic additive Config class that takes in zero arguments and instead uses hardcoded values to set the RTL design parameters.
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

This next example shows a "higher-level" additive Config that uses prior parameters that were set to derive other parameters.

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

The following example shows a non-additive Config that combines the prior two additive Configs using ``++``.
The additive Configs are applied from the right to left in the list (or bottom to top in the example).
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

Cake Pattern
-------------------------

A cake pattern is a Scala programming pattern, which enable "mixing" of multiple traits or interface definitions (sometimes referred to as dependency injection).
It is used in the Rocket Chip SoC library and Chipyard framework in merging multiple system components and IO interfaces into a large system component.

This example shows a Rocket Chip based SoC that merges multiple system components (BootROM, UART, etc) into a single top-level design.

.. _cake-example:
.. code-block:: scala

  class MySoC(implicit p: Parameters) extends RocketSubsystem
    with CanHaveMasterAXI4MemPort
    with HasPeripheryBootROM
    with HasNoDebug
    with HasPeripherySerial
    with HasPeripheryUART
    with HasPeripheryIceNIC
  {
     lazy val module = new MySoCModuleImp(this)
  }

  class MySoCModuleImp(outer: MySoC) extends RocketSubsystemModuleImp(outer)
    with CanHaveMasterAXI4MemPortModuleImp
    with HasPeripheryBootROMModuleImp
    with HasNoDebugModuleImp
    with HasPeripherySerialModuleImp
    with HasPeripheryUARTModuleImp
    with HasPeripheryIceNICModuleImp

There are two "cakes" here. One for the lazy module (ex. ``HasPeripherySerial``) and one for the lazy module
implementation (ex. ``HasPeripherySerialModuleImp`` where ``Imp`` refers to implementation). The lazy module defines
all the logical connections between generators and exchanges configuration information among them, while the
lazy module implementation performs the actual Chisel RTL elaboration.

In the MySoC example class, the "outer" ``MySoC`` instantiates the "inner"
``MySoCModuleImp`` as a lazy module implementation. This delays immediate elaboration
of the module until all logical connections are determined and all configuration information is exchanged.
The ``RocketSubsystem`` outer base class, as well as the
``HasPeripheryX`` outer traits contain code to perform high-level logical
connections. For example, the ``HasPeripherySerial`` outer trait contains code
to lazily instantiate the ``SerialAdapter``, and connect the ``SerialAdapter``'s
TileLink node to the Front bus.

The ``ModuleImp`` classes and traits perform elaboration of real RTL.
For example, the ``HasPeripherySerialModuleImp`` trait physically connects
the ``SerialAdapter`` module, and instantiates queues.

In the test harness, the SoC is elaborated with
``val dut = Module(LazyModule(MySoC))``.
After elaboration, the result will be a MySoC module, which contains a
SerialAdapter module (among others).

From a high level, classes which extend LazyModule *must* reference
their module implementation through ``lazy val module``, and they
*may* optionally reference other lazy modules (which will elaborate
as child modules in the module hierarchy). The "inner" modules
contain the implementation for the module, and may instantiate
other normal modules OR lazy modules (for nested Diplomacy
graphs, for example).

 Mix-in
---------------------------

A mix-in is a Scala trait, which sets parameters for specific system components, as well as enabling instantiation and wiring of the relevant system components to system buses.
The naming convention for an additive mix-in is ``Has<YourMixin>``.
This is shown in the MySoC class where things such as ``HasPeripherySerial`` connect a RTL component to a bus and expose signals to the top-level.

Additional References
---------------------------

A brief explanation of some of these topics is given in the following video: https://www.youtube.com/watch?v=Eko86PGEoDY.
