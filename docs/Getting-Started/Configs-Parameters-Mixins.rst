Configs, Parameters, Mix-ins, and Everything In Between
========================================================

A significant portion of generators in the ReBAR framework use the Rocket chip parameter system.
This parameter system enables for the flexible configuration of the SoC without invasive RTL changes.
In order to use the parameter system correctly, we will use several terms and conventions:

**Parameter**

TODO: Need to explain up, site, field, and other stuff from Henry's thesis.

It is important to note that a significant challenge with the Rocket parameter system is being able to identify the correct parameter to use, and the impact that parameter has on the overall system. We are still investigating methods to facilitate parameter exploration and discovery.


**Config**
A `Config` is a collection of multiple parameters being set to specific values.
Configs are additive, and can override each other.
A Config can be composed of other configs.  
The naming convetion for an additive config is ``With<YourConfig>``, while the naming convention for a non-additive config will be ``<YourConfig>``.
Configs can take arguments which will in-turn set parameters in the specific configs.

Example config:

.. code-block:: scala

  class WithMyAcceleratorParams extends Config((site, here, up) => {
    case MyAcceleratorKey =>
      MyAcceleratorConfig(
        Rows = 2,
        rowBits = 64,
        Columns = 16,
        hartId = 1,
        some_length = 256,
      )
  })

Example config which uses a higher level config:

.. code-block:: scala

  class WithMyMoreComplexAcceleratorConfig extends Config((site, here, up) => {
    case MyAcceleratorKey =>
      MyAcceleratorConfig(
        Rows = 2,
        rowBits = site(SystemBusKey).beatBits,
        hartId = up(RocketTilesKey, site).length,
      )
  })

Example of additive configs:

.. code-block:: scala

  class SomeAdditiveConfig extends Config(
    new WithMyMoreComplexAcceleratorConfig ++
    new WithMyAcceleratorParams ++
    new DefaultExampleConfig
  )


**Cake Pattern**
The cake pattern is a scala programming pattern, which enable "mixing" of multiple traits or interface definitions (sometimes refered to as dependancy injection). It is used in the Rocket chip SoC library and ReBAR framework in merging multiple system components and IO interfaces into a large system component.

Example of using the cake pattern to merge multiple system components into a single top-level design, extending a basic Rocket SoC:

.. code-block:: scala

  class MySoC(implicit p: Parameters) extends RocketSubsystem
    with CanHaveMisalignedMasterAXI4MemPort
    with HasPeripheryBootROM
    with HasNoDebug
    with HasPeripherySerial
    with HasPeripheryUART
    with HasPeripheryIceNIC
  {
     //Additional top-level specific instantiations or wiring
  }


**Mix-in**
A mix-in is a scala trait, which sets parameters for specific system components, as well as enabling instantiation and wiring of the relevant system components to system buses. 
The naming convetion for an additive mix-in is ``Has<YourMixin>``.

