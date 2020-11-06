FPGA Prototyping
==============================

FPGA Prototyping
-----------------------

Chipyard supports FPGA prototyping for local FPGAs supported under ``fpga-shells`` <LINK>.
This include popular FPGAs such as the Xilinx VCU118 and the Xilinx Arty board.
FPGA prototyping allows RTL-level simulation at orders-of-magnitude faster speeds than software RTL simulators at the cost of slower compile times.

Setup
-----

All FPGA related collateral is located in the ``fpga`` top-level Chipyard folder.
To initialize the ``fpga-shells`` repository, run the included submodule script:

.. code-block:: shell

    # in the chipyard top level folder
    ./scripts/init-fpga.sh

Making a Bitstream
------------------

Making a bitstream for any FPGA is similar to building RTL for a software RTL simulation.
Similar to the :ref:`Simulating A Custom Project` section in the :ref:`Software RTL Simulation` section you can run the following command in the ``fpga`` directory.

.. code-block:: shell

    make SBT_PROJECT=... MODEL=... VLOG_MODEL=... MODEL_PACKAGE=... CONFIG=... CONFIG_PACKAGE=... GENERATOR_PACKAGE=... TB=... TOP=... bit

    # or

    make SUB_PROJECT=<sub_project> bit

By default a couple of ``SUB_PROJECT``'s are already defined for use, including ``vcu118`` and ``arty``.
These default ``SUB_PROJECT``'s setup the necessary test harnesses, packages, and more.
In most cases, you will just need to run a command with a ``SUB_PROJECT`` and an overridden ``CONFIG`` to point to.
For example, building the BOOM configuration on the VCU118:

.. code-block:: shell

    make SUB_PROJECT=vcu118 CONFIG=BoomVCU118Config

Running a Design on Arty
------------------------

Running a Design on VCU118
--------------------------

Basic Design
~~~~~~~~~~~~

The default VCU118 design is setup to run RISC-V Linux from an SDCard while piping the terminal over UART.
To change the design, you can create your own configuration and add the ``AbstractVCU118Config`` located in ``fpga/src/main/scala/vcu118/Configs.scala``.
Adding this config. fragment will enable and connect the UART, SPI SDCard, and DDR backing memory.
Notice that the majority of config. fragments in ``AbstractVCU118Config`` are shared with a normal Chipyard config.

.. literalinclude:: ../../fpga/src/main/scala/vcu118/Configs.scala
    :language: scala
    :start-after: DOC include start: AbstractVCU118 and Rocket
    :end-before: DOC include end: AbstractVCU118 and Rocket

fpga-shells / Overlays / HarnessBinders
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

To make meaningful VCU118 changes (adding new IOs, connecting to different VCU118 ports, etc), the ``VCU118TestHarness`` must change.
The ``VCU118TestHarness`` uses ``fpga-shells`` to add ``Overlays`` that connect to the VCU118 external IOs.
``fpga/src/main/scala/vcu118/TestHarness.scala`` shows an example of using these ``Overlays``.
First ``Overlays`` must be "placed" which adds them to the design.
For example, the following shows a UART overlay being placed into the design.

.. literalinclude:: ../../fpga/src/main/scala/vcu118/TestHarness.scala
    :language: scala
    :start-after: DOC include start: UartOverlay
    :end-before: DOC include end: UartOverlay

Here the ``UARTOverlayKey`` is referenced and used to "place" the necessary connections (and collateral) to connect to the UART.
The ``UARTDesignInput`` is used to pass in the UART signals used to connect to the external UART IO.
This is similar to all the other ``Overlays``.
They must be "placed" and given a set of inputs (IOs, parameters).

Once you add the wanted ``Overlays`` and place them into a new ``TestHarness``, you can add a new set of harness/io binders to connect to them.
This is shown in ``fpga/src/main/scala/vcu118/HarnessBinders.scala``.
For more information on harness and IO binders, refer to :ref:`IOBinders and HarnessBinders`.

An example of a more complicated design using new ``Overlays`` can be viewed in ``fpga/src/main/scala/vcu118/bringup/``.
