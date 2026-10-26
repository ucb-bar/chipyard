package chipyard

import org.chipsalliance.cde.config.Config

/** Verilog RGMII Ethernet loopback with MAC TX connected to MAC RX. */
// Runs the Ethernet simulation loopback bare-metal program through the MMIO TX/RX queues.
// Run from chipyard root:
//   make -C sims/verilator CONFIG=EthernetRGMIILoopbackRocketConfig run-binary-fast \
//     BINARY=$PWD/software/ethernet/ethernet_sim_loopback.riscv
class EthernetRGMIILoopbackRocketConfig extends Config(
  new chipyard.harness.WithEthernetRGMIILoopback ++
  new rivet.wrapper.WithEthernetRGMIISim ++
  new freechips.rocketchip.rocket.WithCFlushEnabled ++
  new freechips.rocketchip.rocket.WithNHugeCores(1) ++
  new chipyard.config.AbstractConfig)

/** Verilog GMII Ethernet loopback with MAC TX connected to MAC RX. */
// Run from chipyard root:
//   make -C sims/verilator CONFIG=EthernetGMIILoopbackRocketConfig run-binary-fast \
//     BINARY=$PWD/software/ethernet/ethernet_sim_loopback.riscv
class EthernetGMIILoopbackRocketConfig extends Config(
  new chipyard.harness.WithEthernetGMIILoopback ++
  new rivet.wrapper.WithEthernetGMIISim ++
  new freechips.rocketchip.rocket.WithCFlushEnabled ++
  new freechips.rocketchip.rocket.WithNHugeCores(1) ++
  new chipyard.config.AbstractConfig)

/** Verilog XGMII Ethernet loopback with MAC TX connected to MAC RX. */
// Run from chipyard root:
//   make -C sims/verilator CONFIG=EthernetXGMIILoopbackRocketConfig run-binary-fast \
//     BINARY=$PWD/software/ethernet/ethernet_sim_loopback.riscv
class EthernetXGMIILoopbackRocketConfig extends Config(
  new chipyard.harness.WithEthernetXGMIILoopback ++
  new rivet.wrapper.WithEthernetXGMIISim ++
  new freechips.rocketchip.rocket.WithCFlushEnabled ++
  new freechips.rocketchip.rocket.WithNHugeCores(1) ++
  new chipyard.config.AbstractConfig)

/** Chisel-native RGMII Ethernet loopback using the same testbench loopback. */
// Run from chipyard root:
//   make -C sims/verilator CONFIG=EthMac1GRgmiiLoopbackRocketConfig run-binary-fast \
//     BINARY=$PWD/software/ethernet/ethernet_sim_loopback.riscv
class EthMac1GRgmiiLoopbackRocketConfig extends Config(
  new chipyard.harness.WithEthernetRGMIILoopback ++
  new rivet.WithEthernetRGMIISim ++
  new freechips.rocketchip.rocket.WithCFlushEnabled ++
  new freechips.rocketchip.rocket.WithNHugeCores(1) ++
  new chipyard.config.AbstractConfig)

/** Chisel-native GMII Ethernet loopback using the same testbench loopback. */
// Run from chipyard root:
//   make -C sims/verilator CONFIG=EthMac1GGmiiLoopbackRocketConfig run-binary-fast \
//     BINARY=$PWD/software/ethernet/ethernet_sim_loopback.riscv
class EthMac1GGmiiLoopbackRocketConfig extends Config(
  new chipyard.harness.WithEthernetGMIILoopback ++
  new rivet.WithEthernetGMIISim ++
  new freechips.rocketchip.rocket.WithCFlushEnabled ++
  new freechips.rocketchip.rocket.WithNHugeCores(1) ++
  new chipyard.config.AbstractConfig)

/** Verilog RGMII Ethernet connected to a file-backed peer in the testbench. */
class EthernetRGMIIFileTransferRocketConfig extends Config(
  new chipyard.harness.WithEthernetRGMIIFileModel ++
  new rivet.wrapper.WithEthernetRGMIISim ++
  new freechips.rocketchip.rocket.WithCFlushEnabled ++
  new freechips.rocketchip.rocket.WithNHugeCores(1) ++
  new chipyard.config.AbstractConfig)

/** Verilog GMII Ethernet connected to a file-backed peer in the testbench. */
class EthernetGMIIFileTransferRocketConfig extends Config(
  new chipyard.harness.WithEthernetGMIIFileModel ++
  new rivet.wrapper.WithEthernetGMIISim ++
  new freechips.rocketchip.rocket.WithCFlushEnabled ++
  new freechips.rocketchip.rocket.WithNHugeCores(1) ++
  new chipyard.config.AbstractConfig)

/** Verilog XGMII Ethernet connected to a file-backed peer in the testbench. */
class EthernetXGMIIFileTransferRocketConfig extends Config(
  new chipyard.harness.WithEthernetXGMIIFileModel ++
  new rivet.wrapper.WithEthernetXGMIISim ++
  new freechips.rocketchip.rocket.WithCFlushEnabled ++
  new freechips.rocketchip.rocket.WithNHugeCores(1) ++
  new chipyard.config.AbstractConfig)

/** Chisel-native RGMII Ethernet connected to a file-backed peer in the testbench. */
class EthMac1GRgmiiFileTransferRocketConfig extends Config(
  new chipyard.harness.WithEthernetRGMIIFileModel ++
  new rivet.WithEthernetRGMIISim ++
  new freechips.rocketchip.rocket.WithCFlushEnabled ++
  new freechips.rocketchip.rocket.WithNHugeCores(1) ++
  new chipyard.config.AbstractConfig)

/** Chisel-native GMII Ethernet connected to a file-backed peer in the testbench. */
class EthMac1GGmiiFileTransferRocketConfig extends Config(
  new chipyard.harness.WithEthernetGMIIFileModel ++
  new rivet.WithEthernetGMIISim ++
  new freechips.rocketchip.rocket.WithCFlushEnabled ++
  new freechips.rocketchip.rocket.WithNHugeCores(1) ++
  new chipyard.config.AbstractConfig)

/** Verilog RGMII MAC and Verilog UDP stack connected to the UDP file peer. */
class EthernetRGMIIUdpFileTransferRocketConfig extends Config(
  new chipyard.harness.WithEthernetRGMIIUdpFileModel ++
  new rivet.udp.WithEthernetRGMIIUdpOffload(rivet.udp.VerilogUdpStack) ++
  new rivet.wrapper.WithEthernetRGMIISim ++
  new freechips.rocketchip.rocket.WithCFlushEnabled ++
  new freechips.rocketchip.rocket.WithNHugeCores(1) ++
  new chipyard.config.AbstractConfig)

/** Verilog GMII MAC and Verilog UDP stack connected to the UDP file peer. */
class EthernetGMIIUdpFileTransferRocketConfig extends Config(
  new chipyard.harness.WithEthernetGMIIUdpFileModel ++
  new rivet.udp.WithEthernetGMIIUdpOffload(rivet.udp.VerilogUdpStack) ++
  new rivet.wrapper.WithEthernetGMIISim ++
  new freechips.rocketchip.rocket.WithCFlushEnabled ++
  new freechips.rocketchip.rocket.WithNHugeCores(1) ++
  new chipyard.config.AbstractConfig)

/** Verilog XGMII MAC and byte-wide Verilog UDP stack connected to the UDP file peer. */
class EthernetXGMIIUdpFileTransferRocketConfig extends Config(
  new chipyard.harness.WithEthernetXGMIIUdpFileModel ++
  new rivet.udp.WithEthernetXGMIIUdpOffload(rivet.udp.VerilogUdpStack) ++
  new rivet.wrapper.WithEthernetXGMIISim ++
  new freechips.rocketchip.rocket.WithCFlushEnabled ++
  new freechips.rocketchip.rocket.WithNHugeCores(1) ++
  new chipyard.config.AbstractConfig)

/** Chisel RGMII MAC and native UDP stack connected to the UDP file peer. */
class EthMac1GRgmiiUdpFileTransferRocketConfig extends Config(
  new chipyard.harness.WithEthernetRGMIIUdpFileModel ++
  new rivet.udp.WithEthernetRGMIIUdpOffload(rivet.udp.ChiselUdpStack) ++
  new rivet.WithEthernetRGMIISim ++
  new freechips.rocketchip.rocket.WithCFlushEnabled ++
  new freechips.rocketchip.rocket.WithNHugeCores(1) ++
  new chipyard.config.AbstractConfig)

/** Chisel GMII MAC and native UDP stack connected to the UDP file peer. */
class EthMac1GGmiiUdpFileTransferRocketConfig extends Config(
  new chipyard.harness.WithEthernetGMIIUdpFileModel ++
  new rivet.udp.WithEthernetGMIIUdpOffload(rivet.udp.ChiselUdpStack) ++
  new rivet.WithEthernetGMIISim ++
  new freechips.rocketchip.rocket.WithCFlushEnabled ++
  new freechips.rocketchip.rocket.WithNHugeCores(1) ++
  new chipyard.config.AbstractConfig)
