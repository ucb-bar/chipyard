.. _hetero_socs_:

Heterogeneous SoCs
===============================

The Chipyard framework involves multiple cores and accelerators that can be composed in arbitrary ways.
This discussion will focus on how you combine Rocket, BOOM and Hwacha in particular ways to create a unique SoC.

Creating a Rocket and BOOM System
-------------------------------------------

Instantiating an SoC with Rocket and BOOM cores is all done with the configuration system and two specific config fragments.
Both BOOM and Rocket have config fragments labelled ``WithN{Small|Medium|Large|etc.}BoomCores(X)`` and ``WithNBigCores(X)`` that automatically create ``X`` copies of the core/tile [1]_.
When used together you can create a heterogeneous system.

The following example shows a dual core BOOM with a single core Rocket.

.. literalinclude:: ../../generators/chipyard/src/main/scala/config/HeteroConfigs.scala
    :language: scala
    :start-after: DOC include start: DualBoomAndSingleRocket
    :end-before: DOC include end: DualBoomAndSingleRocket


Adding Hwachas
-------------------------------------------

Adding a Hwacha accelerator is as easy as adding the ``DefaultHwachaConfig`` so that it can setup the Hwacha parameters and add itself to the ``BuildRoCC`` parameter.
An example of adding a Hwacha to all tiles in the system is below.

.. literalinclude:: ../../generators/chipyard/src/main/scala/config/HeteroConfigs.scala
    :language: scala
    :start-after: DOC include start: BoomAndRocketWithHwacha
    :end-before: DOC include end: BoomAndRocketWithHwacha

In this example, Hwachas are added to both BOOM tiles and to the Rocket tile.
All with the same Hwacha parameters.

Assigning Accelerators to Specific Tiles with MultiRoCC
-------------------------------------------------------

Located in ``generators/chipyard/src/main/scala/config/fragments/RoCCFragments.scala`` is a config fragment that provides support for adding RoCC accelerators to specific tiles in your SoC.
Named ``MultiRoCCKey``, this key allows you to attach RoCC accelerators based on the ``hartId`` of the tile.
For example, using this allows you to create a 8 tile system with a RoCC accelerator on only a subset of the tiles.
An example is shown below with two BOOM cores, and one Rocket tile with a RoCC accelerator (Hwacha) attached.

.. literalinclude:: ../../generators/chipyard/src/main/scala/config/HeteroConfigs.scala
    :language: scala
    :start-after: DOC include start: DualBoomAndRocketOneHwacha
    :end-before: DOC include end: DualBoomAndRocketOneHwacha

The ``WithMultiRoCCHwacha`` config fragment assigns a Hwacha accelerator to a particular ``hartId`` (in this case, the ``hartId`` of ``0`` corresponds to the Rocket core).
Finally, the ``WithMultiRoCC`` config fragment is called.
This config fragment sets the ``BuildRoCC`` key to use the ``MultiRoCCKey`` instead of the default.
This must be used after all the RoCC parameters are set because it needs to override the ``BuildRoCC`` parameter.
If this is used earlier in the configuration sequence, then MultiRoCC does not work.

This config fragment can be changed to put more accelerators on more cores by changing the arguments to cover more ``hartId``'s (i.e. ``WithMultiRoCCHwacha(0,1,3,6,...)``).

Since config fragments are applied from right-to-left (or bottom-to-top as they are formatted here), the right-most config fragment specifying a core (which is ``freechips.rocketchip.subsystem.WithNBigCores`` in the example above) gets the first hart ID.
Consider this config:

.. code-block:: scala

    class RocketThenBoomHartIdTestConfig extends Config(
      new boom.common.WithNLargeBooms(2) ++
      new freechips.rocketchip.subsystem.WithNBigCores(3) ++
      new chipyard.config.AbstractConfig)

This specifies an SoC with three Rocket cores and two BOOM cores.
The Rocket cores would have hart IDs 0, 1, and 2, while the BOOM cores would have hard IDs 3 and 4.
On the other hand, consider this config which reverses the order of those two fragments:

.. code-block:: scala

    class BoomThenRocketHartIdTestConfig extends Config(
      new freechips.rocketchip.subsystem.WithNBigCores(3) ++
      new boom.common.WithNLargeBooms(2) ++
      new chipyard.config.AbstractConfig)

This also specifies an SoC with three Rocket cores and two BOOM cores, but because the BOOM config fragment is evaluated before the Rocket config fragment, the hart IDs are reversed.
The BOOM cores would have hart IDs 0 and 1, while the Rocket cores would have hard IDs 2, 3, and 4.

.. [1] Note, in this section "core" and "tile" are used interchangeably but there is subtle distinction between a "core" and "tile" ("tile" contains a "core", L1D/I$, PTW).
    For many places in the documentation, we usually use "core" to mean "tile" (doesn't make a large difference but worth the mention).
