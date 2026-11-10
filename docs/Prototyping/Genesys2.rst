Running a Design on Genesys 2
=============================

Genesys 2 Instructions
----------------------

The default Digilent Genesys 2 harness uses a TSI-over-UART adapter to bring up the FPGA and DDR for backing memory.
A user can connect to the Genesys 2 target using the ``uart_tsi`` program that opens a UART TTY.

An example tweaks + Rocket config fragment can be found below:

.. literalinclude:: ../../fpga/src/main/scala/genesys2/Configs.scala
    :language: scala
    :prepend: // https://ucb.bar/chipyard/fpga/src/main/scala/genesys2/Configs.scala
    :start-after: DOC include start: WithGenesys2Tweaks and Rocket
    :end-before: DOC include end: WithGenesys2Tweaks and Rocket

To build the design (Vivado should be added to the ``PATH``), run:

.. code-block:: shell

		cd fpga/
		make SUB_PROJECT=genesys2 bitstream

To build the UART-based frontend server, run:

.. code-block:: shell

		cd generators/testchipip/uart_tsi
		make

After programming the bitstream and connecting the Genesys 2 UART to a host PC via the USB cable, the ``uart_tsi`` program can be run to interact with the target.

Running a program:

.. code-block:: shell

		./uart_tsi +tty=/dev/ttyUSBX dhrystone.riscv

Probe an address on the target system:

.. code-block:: shell

		./uart_tsi +tty=/dev/ttyUSBX +init_read=0x10000 none

Write an address before running a program:

.. code-block:: shell

		./uart_tsi +tty=/dev/ttyUSBX +init_write=0x80000000:0xdeadbeef none

Self-check that binary loading proceeded correctly:

.. code-block:: shell

		./uart_tsi +tty=/dev/ttyUSBX +selfcheck dhrystone.riscv

Optional RGMII Ethernet designs are provided by ``RocketGenesys2VerilogEthConfig`` and ``RocketGenesys2ChiselEthConfig``; example firmware is in ``software/ethernet``.
