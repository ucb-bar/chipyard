SiFive Generators
==================

Chipyard includes several open-source generators developed and maintained by `SiFive <https://www.sifive.com/>`__.
These are currently organized within two submodules named ``sifive-blocks`` and ``sifive-cache``.

Last-Level Cache Generator
-----------------------------

``sifive-cache`` includes last-level cache geneator. The Chipyard framework uses this last-level cache as an L2 cache. To use this L2 cache, you should add the ``freechips.rocketchip.subsystem.WithInclusiveCache`` config fragment to your SoC configuration.
To learn more about configuring this L2 cache, please refer to the :ref:`memory-hierarchy` section.


Peripheral Devices
-------------------
``sifive-blocks`` includes multiple peripheral device generators, such as UART, SPI, PWM, JTAG, GPIO and more.

These peripheral devices usually affect the memory map of the SoC, and its top-level IO as well.
To integrate one of these devices in your SoC, you will need to define a custom config fragment with the approriate address for the device using the Rocket Chip parameter system. As an example, for a GPIO device you could add the following config fragment to set the GPIO address to ``0x10012000``. This address is the start address for the GPIO configuration registers.

.. literalinclude:: ../../generators/chipyard/src/main/scala/config/fragments/PeripheralFragments.scala
    :language: scala
    :start-after: DOC include start: gpio config fragment
    :end-before: DOC include end: gpio config fragment

Additionally, if the device requires top-level IOs, you will need to define a config fragment to change the top-level configuration of your SoC.
When adding a top-level IO, you should also be aware of whether it interacts with the test-harness.

This example instantiates a top-level module with include GPIO ports, and then ties-off the GPIO port inputs to 0 (``false.B``).


Finally, you add the relevant config fragment to the SoC config. For example:

.. literalinclude:: ../../generators/chipyard/src/main/scala/config/RocketConfigs.scala
    :language: scala
    :start-after: DOC include start: GPIORocketConfig
    :end-before: DOC include end: GPIORocketConfig

Some of the devices in ``sifive-blocks`` (such as GPIO) may already have pre-defined config fragments within the Chipyard example project. You may be able to use these config fragments directly, but you should be aware of their addresses within the SoC address map.
