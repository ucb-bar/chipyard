SiFive Generators
==================

Chipyard includes several open-source generators developed and maintained by `SiFive <https://www.sifive.com/>`__.
These are currently organized within two submodules named ``sifive-blocks`` and ``sifive-cache``.

Last-Level Cache Generator
-----------------------------

``sifive-cache`` includes last-level cache geneator. The Chipyard framework uses this last-level cache as an L2 cache. To use this L2 cache, you should add the ``freechips.rocketchip.subsystem.WithInclusiveCache`` config fragment to your SoC configuration.
To learn more about configuring this L2 cache, please refer to the :ref:`memory-hierarchy` section.


Peripheral Devices Overview
----------------------------
``sifive-blocks`` includes multiple peripheral device generators, such as UART, SPI, PWM, JTAG, GPIO and more.

These peripheral devices usually affect the memory map of the SoC, and its top-level IO as well.
All the peripheral blocks comes with a default memory address that would not collide with each other, but if integrating multiple duplicated blocks in the SoC is needed, you will need to explicitly specify an approriate memory address for that device.

Additionally, if the device requires top-level IOs, you will need to define a config fragment to change the top-level configuration of your SoC.
When adding a top-level IO, you should also be aware of whether it interacts with the test-harness.

This example instantiates a top-level module with include GPIO ports, and then ties-off the GPIO port inputs to 0 (``false.B``).

Finally, you add the relevant config fragment to the SoC config. For example:

.. literalinclude:: ../../generators/chipyard/src/main/scala/config/PeripheralDeviceConfigs.scala
    :language: scala
    :start-after: DOC include start: GPIORocketConfig
    :end-before: DOC include end: GPIORocketConfig


General Purpose I/Os (GPIO) Device
----------------------------------

GPIO device is a periphery device provided by ``sifive-blocks``. Each general-purpose I/O port has five 32-bit configuration registers, two 32-bit data registers controlling pin input and output values, and eight 32-bit interrupt control/status register for signal level and edge triggering. In addition, all GPIOs can have two 32-bit alternate function selection registers.


GPIO main features
~~~~~~~~~~~~~~~~~~~~~~~

* Output states: push-pull or open drain with optional pull-up/down resistors

* Output data from output value register (GPIOx_OUTPUT_VAL) or peripheral (alternate function output)

* 3-bit drive strength selection for each I/O

* Input states: floating, pull-up, or pull-down

* Input data to input value register (GPIOx_INPUT_VAL) or peripheral (alternate function input)

* Alternate function selection registers

* Bit invert register (GPIOx_OUTPUT_XOR) for fast output inversion


Including GPIO in the SoC
~~~~~~~~~~~~~~~~~~~~~~~~~

.. code-block:: scala

    class ExampleChipConfig extends Config(
      // ...

      // ==================================
      //   Set up Memory Devices
      // ==================================
      // ...

      // Peripheral section
      new chipyard.config.WithGPIO(address = 0x10010000, width = 32) ++

      // ...
    )


Universal Asynchronous Receiver/Transmitter (UART) Device
----------------------------------------------------------

UART device is a periphery device provided by ``sifive-blocks``. The UART offers a flexible means to perform Full-duplex data exchange with external devices. A very wide range of baud rates can be achieved through a fractional baud rate generator. The UART peripheral does not support other modem control signals, or synchronous serial data transfers.


UART main features
~~~~~~~~~~~~~~~~~~~~~~~

* Full-duplex asynchronous communication

* Baud rate generator systems

* 16× Rx oversampling with 2/3 majority voting per bit

* Two internal FIFOs for transmit and receive data with programmable watermark interrupts

* A common programmable transmit and receive baud rate

* Configurable stop bits (1 or 2 stop bits)

* Separate enable bits for transmitter and receiver

* Interrupt sources with flags

* Configurable hardware flow control signals


Including UART in the SoC
~~~~~~~~~~~~~~~~~~~~~~~~~

.. code-block:: scala

    class ExampleChipConfig extends Config(
      // ...

      // ==================================
      //   Set up Memory Devices
      // ==================================
      // ...

      // Peripheral section
      new chipyard.config.WithUART(address = 0x10020000, baudrate = 115200) ++

      // ...
    )

Inter-Integrated Circuit (I2C) Interface Device
-------------------------------------------------

I2C device is a periphery device provided by ``sifive-blocks``. The I2C (inter-integrated circuit) bus interface handles communications to the serial I2C bus. It provides multi-master capability, and controls all I2C bus-specific sequencing, protocol, arbitration and timing. It supports Standard-mode (Sm), Fast-mode (Fm) and Fast-mode Plus (Fm+).


I2C main features
~~~~~~~~~~~~~~~~~~~~~~~

* I2C bus specification compatibility:

  * Slave and master modes

  * Multimaster capability

  * Standard-mode (up to 100 kHz)

  * Fast-mode (up to 400 kHz)

  * Fast-mode Plus (up to 1 MHz)

  * 7-bit addressing mode


Including I2C in the SoC
~~~~~~~~~~~~~~~~~~~~~~~~~

.. code-block:: scala

    class ExampleChipConfig extends Config(
      // ...

      // ==================================
      //   Set up Memory Devices
      // ==================================
      // ...

      // Peripheral section
      new chipyard.config.WithI2C(address = 0x10040000) ++

      // ...
    )


Serial Peripheral Interface (SPI) Device
-------------------------------------------------

SPI device is a periphery device provided by ``sifive-blocks``. The SPI interface can be used to communicate with external devices using the SPI protocol.

The serial peripheral interface (SPI) protocol supports half-duplex, full-duplex and simplex synchronous, serial communication with external devices. The interface can be configured as master and in this case it provides the communication clock (SCLK) to the external slave device.


SPI main features
~~~~~~~~~~~~~~~~~~~~~~~

* Master operation

* Full-duplex synchronous transfers

* 4 to 16-bit data size selection

* Master mode baud rate prescalers up to fPCLK/2

* NSS management by hardware or software

* Programmable clock polarity and phase

* Programmable data order with MSB-first or LSB-first shifting

* Dedicated transmission and reception flags with interrupt capability

* Two 32-bit embedded Rx and Tx FIFOs with DMA capability


Including SPI in the SoC
~~~~~~~~~~~~~~~~~~~~~~~~~

.. code-block:: scala

    class ExampleChipConfig extends Config(
      // ...

      // ==================================
      //   Set up Memory Devices
      // ==================================
      // ...

      // Peripheral section
      new chipyard.config.WithSPI(address = 0x10031000) ++

      // ...
    )
