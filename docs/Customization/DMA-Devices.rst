.. _dma-devices:

Adding a DMA Device
===================

DMA devices are Tilelink widgets which act as masters. In other words,
DMA devices can send their own read and write requests to the chip's memory
system.

For IO devices or accelerators (like a disk or network driver), instead of
having the CPU poll data from the device, we may want to have the device write
directly to the coherent memory system instead. For example, here is a device
that writes zeros to the memory at a configured address.

.. literalinclude:: ../../generators/chipyard/src/main/scala/example/InitZero.scala
    :language: scala

.. literalinclude:: ../../generators/chipyard/src/main/scala/DigitalTop.scala
    :language: scala
    :start-after: DOC include start: DigitalTop
    :end-before: DOC include end: DigitalTop

We use ``TLClientNode`` to create a TileLink client node for us.
We then connect the client node to the memory system through the front bus (fbus).
For more info on creating TileLink client nodes, take a look at :ref:`TileLink-Diplomacy-Reference/NodeTypes:Client Node`.

Once we've created our top-level module including the DMA widget, we can create a configuration for it as we did before.

.. literalinclude:: ../../generators/chipyard/src/main/scala/example/InitZero.scala
    :language: scala
    :start-after: DOC include start: WithInitZero
    :end-before: DOC include end: WithInitZero

.. literalinclude:: ../../generators/chipyard/src/main/scala/config/MMIOAcceleratorConfigs.scala
    :language: scala
    :start-after: DOC include start: InitZeroRocketConfig
    :end-before: DOC include end: InitZeroRocketConfig
