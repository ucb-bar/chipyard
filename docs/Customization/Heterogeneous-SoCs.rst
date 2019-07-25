Heterogeneous SoCs
===============================

The Chipyard framework involves multiple cores and accelerators that can be composed in arbitrary ways.
This discussion will focus on how you combine Rocket, BOOM and Hwacha in particular ways to create a unique SoC.

Creating a Rocket and BOOM System
-------------------------------------------

Instantiating an SoC with Rocket and BOOM cores is all done with the configuration system and two specific mixins.
Both BOOM and Rocket have mixins labelled ``WithNBoomCores(X)`` and ``WithNBigCores(X)`` that automatically create ``X`` copies of the core.
When used together you can create a heterogeneous system.
The following example shows a dual core BOOM with a single core Rocket.

.. code-block:: scala

    class DualBoomAndOneRocketConfig extends Config(
      new WithNormalBoomRocketTop ++
      new WithBootROM ++
      new boom.system.WithRenumberHarts ++
      new boom.common.WithRVC ++
      new boom.common.DefaultBoomConfig ++
      new boom.system.WithNBoomCores(2) ++
      new freechips.rocketchip.subsystem.WithoutTLMonitors ++
      new freechips.rocketchip.subsystem.WithNBigCores(1) ++
      new freechips.rocketchip.system.BaseConfig)

In this example, the ``WithNBoomCores`` and ``WithNBigCores`` mixins set up the default parameters for the multiple BOOM and Rocket cores, respectively.
However, for BOOM, an extra mixin called ``DefaultBoomConfig`` is added to override the default parameters with a different set of more common default parameters.
This mixin applies to all BOOM cores in the system and changes the parameters for each.

Great! Now you have a heterogeneous setup with BOOMs and Rockets.
The final thing you need to make this system work is to renumber the ``hartId``'s of the cores so that each core has a unique ``hartId`` (a ``hartId`` is the hardware thread id of the core).
This is done with ``WithRenumberHarts`` (which can label the Rocket cores first or the BOOM cores first).
The reason this is needed is because by default the ``WithN...Cores(X)`` mixin assumes that there are only BOOM or only Rocket cores in the system.
Thus, without the ``WithRenumberHarts`` mixin, each set of cores is labeled starting from zero causing multiple cores to be assigned the same ``hartId``.

Another alternative option to create a multi heterogeneous core system is to override the parameters yourself so you can specify the core parameters per core.
The mixin to add to your system would look something like the following.

.. code-block:: scala

    // create 6 cores (4 boom and 2 rocket)
    class WithHeterCoresSetup extends Config((site, here, up) => {
      case BoomTilesKey => {
        val boomTile0 = BoomTileParams(...) // params for boom core 0
        val boomTile1 = BoomTileParams(...) // params for boom core 1
        val boomTile2 = BoomTileParams(...) // params for boom core 2
        val boomTile3 = BoomTileParams(...) // params for boom core 3
        boomTile0 ++ boomTile1 ++ boomTile2 ++ boomTile3
      }

      case RocketTilesKey => {
        val rocketTile0 = RocketTileParams(...) // params for rocket core 0
        val rocketTile1 = RocketTileParams(...) // params for rocket core 1
        rocketTile0 ++ rocketTile1
      }
    })

Then you could use this new mixin like the following.

.. code-block:: scala

    class SixCoreConfig extends Config(
      new WithNormalBoomRocketTop ++
      new WithBootROM ++
      new WithHeterCoresSetup ++
      new freechips.rocketchip.system.BaseConfig)

Note, in this setup you need to specify the ``hartId`` of each core in the "TileParams", where each ``hartId`` is unique.

Adding Hwachas
-------------------------------------------

Adding a Hwacha accelerator is as easy as adding the ``DefaultHwachaConfig`` so that it can setup the Hwacha parameters and add itself to the ``BuildRoCC`` parameter.
An example of adding a Hwacha to all tiles in the system is below.

.. code-block:: scala

    class DualBoomAndRocketWithHwachasConfig extends Config(
      new WithNormalBoomRocketTop ++
      new WithBootROM ++
      new hwacha.DefaultHwachaConfig ++
      new boom.system.WithRenumberHarts ++
      new boom.common.WithRVC ++
      new boom.common.DefaultBoomConfig ++
      new boom.system.WithNBoomCores(2) ++
      new freechips.rocketchip.subsystem.WithoutTLMonitors ++
      new freechips.rocketchip.subsystem.WithNBigCores(1) ++
      new freechips.rocketchip.system.BaseConfig)

In this example, Hwachas are added to both BOOM tiles and to the Rocket tile.
All with the same Hwacha parameters.

Assigning Accelerators to Specific Tiles with MultiRoCC
-------------------------------------------------------

Located in ``generators/example/src/main/scala/ConfigMixins.scala`` is a mixin that provides support for adding RoCC accelerators to specific tiles in your SoC.
Named ``MultiRoCCKey``, this key allows you to attach RoCC accelerators based on the ``hartId`` of the tile.
For example, using this allows you to create a 8 tile system with a RoCC accelerator on only a subset of the tiles.
An example is shown below with two BOOM cores, and one Rocket tile with a RoCC accelerator (Hwacha) attached.

.. code-block:: scala

    class DualBoomAndOneHwachaRocketConfig extends Config(
      new WithNormalBoomRocketTop ++
      new WithBootROM ++
      new WithMultiRoCC ++
      new WithMultiRoCCHwacha(0) ++ // put Hwacha just on hart0 which was renumbered to Rocket
      new boom.system.WithRenumberHarts(rocketFirst = true) ++
      new hwacha.DefaultHwachaConfig ++
      new boom.common.WithRVC ++
      new boom.common.DefaultBoomConfig ++
      new boom.system.WithNBoomCores(2) ++
      new freechips.rocketchip.subsystem.WithoutTLMonitors ++
      new freechips.rocketchip.subsystem.WithNBigCores(1) ++
      new freechips.rocketchip.system.BaseConfig)

In this example, the ``WithRenumberHarts`` relabels the ``hartId``'s of all the BOOM/Rocket cores.
Then after that is applied to the parameters, the ``WithMultiRoCCHwacha(0)`` is used to assign to ``hartId`` zero a Hwacha (in this case ``hartId`` zero is Rocket).
Finally, the ``WithMultiRoCC`` mixin is called.
This mixin sets the ``BuildRoCC`` key to use the ``MultiRoCCKey`` instead of the default.
This must be used after all the RoCC parameters are set because it needs to override the ``BuildRoCC`` parameter.
If this is used earlier in the configuration sequence, then MultiRoCC does not work.

This mixin can be changed to put more accelerators on more cores by changing the arguments to cover more ``hartId``'s (i.e. ``WithMultiRoCCHwacha(0,1,3,6,...)``).
