.. _hetero_socs_:

Heterogeneous SoCs
===============================

The Chipyard framework involves multiple cores and accelerators that can be composed in arbitrary ways.
This discussion will focus on how you combine Rocket and BOOM in particular ways to create a unique SoC.

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
