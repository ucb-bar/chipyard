SiFive Generators
==================

Chipyard includes several open-source generators developed and maintained by `SiFive <https://www.sifive.com/>`__. 
These are currently organized within two submodules named ``sifive-blocks`` and ``sifive-cache``.

Last-Level Cache Generator
-----------------------------

``sifive-cache`` includes last-level cache geneator. The Chipyard framework uses this last-level cache as an L2 cache. To use this L2 cache, you should add the ``freechips.rocketchip.subsystem.WithInclusiveCache`` mixin to your SoC configuration.
To learn more about configuring this L2 cache, please refer to the :ref:`memory-hierarchy` section.


Peripheral Devices
-------------------
``sifive-blocks`` includes multiple peripheral device generators, such as UART, SPI, PWM, JTAG, GPIO and more.

These peripheral devices usually affect the memory map of the SoC, and its top-level IO as well.
To integrate one of these devices in your SoC, you will need to define a custom mixin with the approriate address for the device using the Rocket Chip parameter system. As an example, for a GPIO device you could add the following mixin to set the GPIO address to ``0x10012000``. This address is the start address for the GPIO configuration registers. 

.. literalinclude:: ../../generators/example/src/main/scala/ConfigMixins.scala
    :language: scala
    :start-after: DOC include start: WithGPIO
    :end-before: DOC include end: WithGPIO

Additionally, if the device requires top-level IOs, you will need to define a mixin to change the top-level configuration of your SoC.
When adding a top-level IO, you should also be aware of whether it interacts with the test-harness.
For example, a GPIO device would require a GPIO pin, and therefore we would write a mixin to augment the top level as follows:

.. literalinclude:: ../../generators/example/src/main/scala/ConfigMixins.scala
    :language: scala
    :start-after: DOC include start: WithGPIOTop
    :end-before: DOC include end: WithGPIOTop

This example instantiates a top-level module with include GPIO ports (``TopWithGPIO``), and then ties-off the GPIO port inputs to 0 (``false.B``).


Finally, you add the relevant config mixin to the SoC config. For example:

.. literalinclude:: ../../generators/example/src/main/scala/RocketConfigs.scala
    :language: scala
    :start-after: DOC include start: GPIORocketConfig
    :end-before: DOC include end: GPIORocketConfig

Some of the devices in ``sifive-blocks`` (such as GPIO) may already have pre-defined mixins within the Chipyard example project. You may be able to use these config mixins directly, but you should be aware of their addresses within the SoC address map. 
