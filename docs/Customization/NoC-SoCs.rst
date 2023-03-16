.. _noc-socs:

SoCs with NoC-based Interconnects
==================================

The primary way to integrate a network-on-chip into a Chipyard SoC is to map one of the standard TileLink crossbar-based buses (System Bus, Memory Bus, Control Bus, etc.) to a Constellation-generated NoC.

The interconnect can be mapped as a "private" interconnect for the TileLink bus, in which case a dedicated interconnect to carry the bus traffic will be generated.
Alternatively, the interconnect can be mapped to a shared global interconnect, in which case multiple TileLink buses can be transported over a single shared interconnect.

Private Interconnects
---------------------
An example of integrating dedicated private interconnects for the System Bus, Memory Bus, and Control Bus can be seen in the ``MultiNoCConfig`` of `generators/chipyard/src/main/scala/config/NoCConfigs.scala <https://github.com/ucb-bar/chipyard/blob/main/generators/chipyard/src/main/scala/config/NoCConfigs.scala>`__.

.. literalinclude:: ../../generators/chipyard/src/main/scala/config/NoCConfigs.scala
    :language: scala
    :start-after: DOC include start: MultiNoCConfig
    :end-before: DOC include end: MultiNoCConfig

Note that for each bus (``Sbus`` / ``Mbus`` / ``Cbus``), the configuration fragment provides both a parameterization of the private NoC, as well as a mapping between TileLink agents and physical NoC nodes.

For more information on how to construct the NoC parameters, see the `Constellation documentation <http://constellation.readthedocs.io>`__.


Shared Global Interconnect
---------------------------
An example of integrating a single global interconnect that supports transporting multiple TileLink buses can be seen in the ``SharedNoCConfig`` of `generators/chipyard/src/main/scala/config/NoCConfigs.scala <https://github.com/ucb-bar/chipyard/blob/main/generators/chipyard/src/main/scala/config/NoCConfigs.scala>`__.

.. literalinclude:: ../../generators/chipyard/src/main/scala/config/NoCConfigs.scala
    :language: scala
    :start-after: DOC include start: SharedNoCConfig
    :end-before: DOC include end: SharedNoCConfig

Note that for each bus, the configuration fragment provides only the mapping between TileLink agents and physical NoC nodes, while a separate fragement provides the configuration for the global interconnect.

For more information on how to construct the NoC parameters, see the `Constellation documentation <http://constellation.readthedocs.io>`__.
