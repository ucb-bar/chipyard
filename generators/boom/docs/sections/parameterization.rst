Parameterization
================

General Parameters
------------------

Listing :numref:`general-boom-params` lists the top-level parameters that you can manipulate for a BOOM core.
This is taken from ``src/main/scala/common/parameters.scala``.

.. _general-boom-params:
.. literalinclude:: ../../src/main/scala/common/parameters.scala
    :language: scala
    :start-after: DOC include start: BOOM Parameters
    :end-before: DOC include end: BOOM Parameters

Sample Configurations
---------------------

Sample configurations of the core and the parameters used can be seen in ``src/main/scala/common/config-mixins.scala``.
The following code shows an example of the "Large BOOM Configuration".

.. _large-boom-config:
.. literalinclude:: ../../src/main/scala/common/config-mixins.scala
    :language: scala
    :start-after: DOC include start: LargeBoomConfig
    :end-before: DOC include end: LargeBoomConfig

Other Parameters
----------------

You can also manipulate other parameters such as Rocket Chip SoC parameters, Uncore, BTB, BIM, BPU, and more when configuring the SoC!
However, this is done in the top-level project that adds BOOM so this will not be discussed here.
