Ethernet
========

The `Rivet Ethernet MAC generator <https://github.com/ucb-bar/ethernet>`__
provides parameterizable Ethernet MACs and a lightweight ARP, IPv4, and UDP
offload stack. Chipyard integrates DMA-backed RGMII and GMII peripherals with
Verilog-backed or Chisel-native MACs, plus XGMII for RTL simulation. MDIO PHY
management and UDP offload are optional.

See ``generators/ethernet/docs/Rivet.rst`` for the generator architecture,
interfaces, parameters, RTL generation, and generator-level testing.

Initialize the Chipyard checkout using the initial repository setup instructions
before building. Source this checkout's ``env.sh`` in every new shell before
running Make or SBT.

Configuration
-------------

``DigitalTop`` includes the optional Ethernet periphery traits. Add the desired
configuration fragment to instantiate one Ethernet peripheral. For example,
the following fragment adds the Verilog-backed RGMII MAC:

.. literalinclude:: ../../generators/ethernet/src/main/scala/wrapper/EthRgmiiConfigs.scala
   :language: scala
   :start-after: DOC include start: WithEthernetRGMII
   :end-before: DOC include end: WithEthernetRGMII

The following fragments configure Ethernet peripherals:

* ``rivet.wrapper.WithEthernetRGMII`` and
  ``rivet.wrapper.WithEthernetGMII`` provide Verilog-backed MACs and are
  defined in `EthRgmiiConfigs.scala <https://github.com/ucb-bar/ethernet/blob/master/src/main/scala/wrapper/EthRgmiiConfigs.scala>`__
  and `EthGmiiConfigs.scala <https://github.com/ucb-bar/ethernet/blob/master/src/main/scala/wrapper/EthGmiiConfigs.scala>`__.
* ``rivet.WithEthernetRGMII`` provides the Chisel-native RGMII MAC and is
  defined in `EthernetConfigMixins.scala <https://github.com/ucb-bar/ethernet/blob/master/src/main/scala/ethernet/EthernetConfigMixins.scala>`__.
* ``rivet.udp.WithEthernetRGMIIUdpOffload`` and
  ``rivet.udp.WithEthernetGMIIUdpOffload`` add UDP offload and are defined in
  `UdpConfigMixins.scala <https://github.com/ucb-bar/ethernet/blob/master/src/main/scala/udp/UdpConfigMixins.scala>`__.
* ``rivet.mdio.WithEthernetMDIO`` adds MDIO and is defined in
  `MdioPeriphery.scala <https://github.com/ucb-bar/ethernet/blob/master/src/main/scala/mdio/MdioPeriphery.scala>`__.

Simulation
----------

Chipyard provides ``EthernetRGMIILoopbackRocketConfig``,
``EthernetGMIILoopbackRocketConfig``, and
``EthernetXGMIILoopbackRocketConfig`` for the Verilog-backed MACs, and
``EthMac1GRgmiiLoopbackRocketConfig`` and
``EthMac1GGmiiLoopbackRocketConfig`` for the Chisel-native MACs. They are
defined in `EthernetConfigs.scala <https://github.com/ucb-bar/chipyard/blob/main/generators/chipyard/src/main/scala/config/EthernetConfigs.scala>`__.

The following runs the Verilog-backed RGMII loopback example with Verilator:

.. code-block:: shell

   cd <chipyard-root>
   source env.sh
   make -C software/ethernet ethernet_sim_loopback.riscv
   make -C sims/verilator \
       CONFIG=EthernetRGMIILoopbackRocketConfig \
       BINARY=$PWD/software/ethernet/ethernet_sim_loopback.riscv \
       LOADMEM=1 \
       run-binary-fast

FPGA
----

Nexys Video and Genesys 2 provide RGMII and MDIO connections to their
on-board RTL8211E PHYs. Ensure Vivado is available in ``PATH``, then source the
Chipyard environment and build the Verilog-backed Ethernet designs with:

.. code-block:: shell

   cd <chipyard-root>
   source env.sh
   cd fpga
   make SUB_PROJECT=nexysvideo CONFIG=RocketNexysVideoVerilogEthConfig bitstream
   make SUB_PROJECT=genesys2 CONFIG=RocketGenesys2VerilogEthConfig bitstream

Use ``RocketNexysVideoChiselEthConfig`` or
``RocketGenesys2ChiselEthConfig`` for the Chisel-native MAC. Ready-made UDP
offload configurations are currently provided only for Nexys Video.

Build the Ethernet firmware, PC utilities, and ``uart_tsi`` from the Chipyard
root:

.. code-block:: shell

   source env.sh
   make -C software/ethernet
   make -C software/ethernet/pc
   make -C generators/testchipip/uart_tsi

After programming the bitstream, run the raw-L2 firmware with:

.. code-block:: shell

   generators/testchipip/uart_tsi/uart_tsi \
       +tty=/dev/ttyUSBX \
       +selfcheck \
       software/ethernet/ethernet_fpga.riscv

See ``software/ethernet/README.md`` for host NIC setup, raw-L2 and UDP file
transfer, PHY loopback, and troubleshooting.
