Running a Design on Nexys Video
===============================

Nexys Video Instructions
------------------------

The default Digilent Nexys Video harness uses a TSI-over-UART adapter to bringup the FPGA.
A user can connect to the Nexys Video target using a special ``uart_tsi`` program that opens a UART TTY.
The interface for the ``uart_tsi`` program provides unique functionality that is useful for bringing up test chips. 

An example tweaks + Rocket config fragment can be found below:

.. literalinclude:: ../../fpga/src/main/scala/nexysvideo/Configs.scala
    :language: scala
    :prepend: // https://ucb.bar/chipyard/fpga/src/main/scala/nexysvideo/Configs.scala
    :start-after: DOC include start: WithNexysVideoTweaks and Rocket
    :end-before: DOC include end: WithNexysVideoTweaks and Rocket

To build the design (Vivado should be added to the ``PATH``), run:

.. code-block:: shell

		cd fpga/
		make SUB_PROJECT=nexysvideo bitstream

To build the UART-based frontend server, run:

.. code-block:: shell

		cd generators/testchipip/uart_tsi
		make

After programming the bitstream, and connecting the Nexys Video's UART to a host PC via the USB cable, the ``uart_tsi`` program can be run to interact with the target.

Running a program:

.. code-block:: shell

		./uart_tsi +tty=/dev/ttyUSBX dhrystone.riscv

Probe an address on the target system:

.. code-block:: shell

		./uart_tsi +tty=/dev/ttyUSBX +init_read=0x10000 none

Write some address before running a program:

.. code-block:: shell

		./uart_tsi +tty=/dev/ttyUSBX +init_write=0x80000000:0xdeadbeef none

Self-check that binary loading proceeded correctly:

.. code-block:: shell

		./uart_tsi +tty=/dev/ttyUSBX +selfcheck dhrystone.riscv
