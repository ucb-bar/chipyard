Running a Design on Arty
========================

Arty100T Instructions
---------------------

The default Digilent Arty A7-100T harness uses a TSI-over-UART adapter to bringup the FPGA.
A user can connect to the Arty A7-100T target using a special ``uart_tsi`` program that opens a UART TTY.
The interface for the ``uart_tsi`` program provides unique functionality that is useful for bringing up test chips.

To build the design (Vivado should be added to the ``PATH``), run:

.. code-block:: shell

		cd fpga/
		make SUB_PROJECT=arty100t bitstream

To build the UART-based frontend server, run:

.. code-block:: shell

		cd generators/testchipip/uart_tsi
		make

After programming the bitstream, and connecting the Arty's UART to a host PC via the USB cable, the ``uart_tsi`` program can be run to interact with the target.

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

Run a design at a higher baud rate than default (For example, if ``CONFIG=UART921600RocketArty100TConfig`` were built):

.. code-block:: shell

		./uart_tsi +tty=/dev/ttyUSBX +baudrate=921600 dhrystone.riscv


Arty35T Legacy Instructions
---------------------------

The default Digilent Arty A7-35T harness is setup to have JTAG available over the board's PMOD pins, and UART available over its FTDI serial USB adapter. The pin mappings for JTAG signals are identical to those described in the `SiFive Freedom E310 Arty 35T Getting Started Guide <https://static.dev.sifive.com/SiFive-E310-arty-gettingstarted-v1.0.6.pdf>`__.
The JTAG interface allows a user to connect to the core via OpenOCD, run bare-metal applications, and debug these applications with gdb. UART allows a user to communicate with the core over a USB connection and serial console running on a PC.
To extend this design, a user may create their own Chipyard configuration and add the ``WithArtyTweaks`` located in `fpga/src/main/scala/arty/Configs.scala <https://ucb.bar/chipyard/fpga/src/main/scala/arty/Configs.scala>`__.
Adding this config. fragment will enable and connect the JTAG and UART interfaces to your Chipyard design.

.. literalinclude:: ../../fpga/src/main/scala/arty/Configs.scala
    :language: scala
    :start-after: DOC include start: AbstractArty and Rocket
    :end-before: DOC include end: AbstractArty and Rocket

Future peripherals to be supported include the Arty A7-35T SPI Flash EEPROM, and I2C/PWM/SPI over the Arty A7-35T GPIO pins. These peripherals are available as part of sifive-blocks.

Arty35T: Debugging over the USB Cable (BSCAN Tunnel)
----------------------------------------------------

The Arty A7-35T's USB cable carries two FTDI channels: channel A programs the FPGA over the Artix-7's own JTAG TAP, and channel B is the UART.
The stock ``TinyRocketArtyConfig`` above brings the SoC's RISC-V JTAG DTM out on PMOD, so debugging needs a second JTAG adapter.
``TinyRocketArtyBScanConfig`` instead routes the DTM through the FPGA TAP's ``USER4`` register using the ``JTAGTUNNEL`` block from ``fpga-shells``, so the programming cable is also the debug cable.

.. literalinclude:: ../../fpga/src/main/scala/arty/Configs.scala
    :language: scala
    :start-after: DOC include start: Arty BSCAN JTAG
    :end-before: DOC include end: Arty BSCAN JTAG

Build and program as usual:

.. code-block:: shell

		cd fpga/
		make SUB_PROJECT=arty35t CONFIG=TinyRocketArtyBScanConfig bitstream

Then attach OpenOCD with the provided configuration, which selects the Series-7 TAP and enables the nested-TAP BSCAN tunnel (``riscv use_bscan_tunnel 5``, Rocket's DTM IR width):

.. code-block:: shell

		openocd -f fpga/scripts/arty35t_bscan_openocd.cfg

GDB connects to OpenOCD's GDB server on port 3333.
Declare the BootROM as read-only first; otherwise GDB single-steps by planting a software breakpoint at the next PC, which fails inside ROM:

.. code-block:: shell

		riscv64-unknown-elf-gdb
		(gdb) target extended-remote :3333
		(gdb) mem 0x10000 0x1ffff ro
		(gdb) info registers pc

Notes:

* The tunnelled DTM is clocked by the FPGA TCK only while ``USER4`` is selected, which is what OpenOCD's tunnel expects.
* ``openFPGALoader`` and OpenOCD both open FTDI channel A; program first, then start OpenOCD.
* Requires an OpenOCD with the ``riscv`` target and BSCAN tunnel support.

Brief Implementation Description and Guidance for Adding/Changing Xilinx Collateral
-----------------------------------------------------------------------------------

Like the VCU118, the basis for the Arty A7-35T design is the creation of a special test harness that connects the external IO (which exist as Xilinx IP blackboxes) to the Chipyard design.
This is done with the ``ArtyTestHarness`` in the basic default Arty A7-35T target. However, unlike the ``VCU118TestHarness``, the ``ArtyTestHarness`` uses no ``Overlays``, and instead directly connects chip top IO to the ports of the external IO blackboxes, using functions such as ``IOBUF`` provided by ``fpga-shells``.
Unlike the VCU118 and other more complicated test harnesses, the Arty A7-35T Vivado collateral is not generated by ``Overlays``, but rather are a static collection of ``create_ip`` and ``set_properties`` statements located in the files within ``fpga/fpga-shells/xilinx/arty/tcl`` and ``fpga/fpga-shells/xilinx/arty/constraints``.
If the user wishes to re-map FPGA package pins to different harness-level IO, this may be changed within ``fpga/fpga-shells/xilinx/arty/constraints/arty-master.xdc``. The addition of new Xilinx IP blocks may be done in ``fpga-shells/xilinx/arty/tcl/ip.tcl``, mapped to harness-level IOs in ``arty-master.xdc``, and wired through from the test harness to the chip top using ``HarnessBinders`` and ``IOBinders``.
Examples of a simple ``IOBinder`` and ``HarnessBinder`` for routing signals (in this case the debug and JTAG resets) from the core to the test harness are the ``WithResetPassthrough`` and ``WithArtyResetHarnessBinder``.
