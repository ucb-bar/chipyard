# Ethernet Software

This directory provides raw-L2 and UDP/IPv4 file transfer for Nexys Video and
Verilator, plus loopback diagnostics. Files are transferred as binary data and may be
up to 64 KiB.

The PC utilities require Linux with Bash, `ip`, `ethtool`, `arping`, `timeout`, and
`sudo`. NetworkManager is optional.

## Build

Run from the Chipyard root:

```bash
source env.sh
make -C software/ethernet
make -C software/ethernet/pc
```

Initialize this Chipyard checkout before building, and source its own `env.sh`
in each new shell before invoking Make or SBT. The firmware Makefile reuses
`tests/atexit-workaround.c` with `--wrap=atexit`, matching Chipyard's workaround
for the GCC 16 nano-Newlib exit path.

For FPGA builds, also make Vivado available in the build shell.

## Nexys Video raw-L2 file transfer

Program a timing-clean bitstream built with one of these configurations:

- `RocketNexysVideoVerilogEthConfig`
- `RocketNexysVideoChiselEthConfig`

### PC terminal

Select and configure the Ethernet interface connected to the board:

```bash
cd <chipyard-root>
ip -brief link
read -rp "Ethernet interface connected to Nexys Video: " ETH_IF
export ETH_IF
export TX_FILE="$PWD/software/ethernet/pc/tx_file.txt"
export RX_FILE="$PWD/software/ethernet/pc/rx_file.txt"

if command -v nmcli >/dev/null 2>&1; then
  sudo nmcli device set "$ETH_IF" managed no
fi
sudo ip link set dev "$ETH_IF" up
sudo ethtool -K "$ETH_IF" rx off tx off gro off gso off tso off
```

### UART terminal

Find the UART-TSI device and start the raw-L2 firmware:

```bash
cd <chipyard-root>
ls -l /dev/ttyUSB*
export UART_TSI=/dev/ttyUSB0  # Replace with the device found above.

generators/testchipip/uart_tsi/uart_tsi \
  +tty="$UART_TSI" \
  +selfcheck \
  software/ethernet/ethernet_fpga.riscv
```

Leave UART-TSI running. After it reports `LINK UP`, run in the PC terminal:

```bash
sudo software/ethernet/pc/ethernet_pc \
  "$ETH_IF" "$TX_FILE" "$RX_FILE"
cmp "$TX_FILE" "$RX_FILE"
```

A successful `cmp` produces no output.

### Cleanup

```bash
sudo ethtool -K "$ETH_IF" rx on tx on gro on gso on tso on
if command -v nmcli >/dev/null 2>&1; then
  sudo nmcli device set "$ETH_IF" managed yes
fi
```

## Nexys Video UDP file transfer

Program a timing-clean bitstream built with one of these configurations:

- `RocketNexysVideoVerilogUdpEthConfig`
- `RocketNexysVideoChiselUdpEthConfig`

### PC terminal

Select and configure the Ethernet interface connected to the board:

```bash
cd <chipyard-root>
ip -brief link
read -rp "Ethernet interface connected to Nexys Video: " ETH_IF
export ETH_IF
export TX_FILE="$PWD/software/ethernet/pc/tx_file.txt"
export RX_FILE="$PWD/software/ethernet/pc/rx_file.txt"

if command -v nmcli >/dev/null 2>&1; then
  sudo nmcli device set "$ETH_IF" managed no
fi
sudo ip link set dev "$ETH_IF" up
sudo ip address replace 192.168.1.100/24 dev "$ETH_IF"
sudo ethtool -K "$ETH_IF" tx off
ethtool "$ETH_IF" | grep -E 'Speed|Duplex|Link detected'
```

The link must report 1000 Mb/s, full duplex, and detected.

### UART terminal

Find the UART-TSI device and start the UDP firmware:

```bash
cd <chipyard-root>
ls -l /dev/ttyUSB*
export UART_TSI=/dev/ttyUSB0  # Replace with the device found above.

generators/testchipip/uart_tsi/uart_tsi \
  +tty="$UART_TSI" \
  +selfcheck \
  software/ethernet/ethernet_udp_fpga.riscv
```

Leave UART-TSI running. After it reports `LINK UP` and `waiting for START`, run in the
PC terminal:

```bash
sudo arping -c 3 \
  -I "$ETH_IF" \
  -S 192.168.1.100 \
  192.168.1.128

timeout 30s software/ethernet/pc/ethernet_udp_pc \
  192.168.1.128 "$TX_FILE" "$RX_FILE"
cmp "$TX_FILE" "$RX_FILE"
```

Expect ARP replies from `02:00:00:00:00:01`, `UDP FILE TRANSFER PASS`, and a
successful `cmp`. The FPGA endpoint is `192.168.1.128:1234`; the PC client uses UDP
port 5678.

### Cleanup

```bash
sudo ip address del 192.168.1.100/24 dev "$ETH_IF" 2>/dev/null || true
sudo ethtool -K "$ETH_IF" tx on
if command -v nmcli >/dev/null 2>&1; then
  sudo nmcli device set "$ETH_IF" managed yes
fi
```

## Nexys Video PHY loopback

Program either raw-L2 Nexys Video bitstream. Select the 100 Mb/s or 1 Gb/s firmware and
run it through UART-TSI:

```bash
cd <chipyard-root>
ls -l /dev/ttyUSB*
export UART_TSI=/dev/ttyUSB0  # Replace with the device found above.

export PHY_PROGRAM=software/ethernet/ethernet_phy_loopback.riscv
# For 1 Gb/s, use software/ethernet/ethernet_phy_loopback_1g.riscv instead.

generators/testchipip/uart_tsi/uart_tsi \
  +tty="$UART_TSI" \
  +selfcheck \
  "$PHY_PROGRAM"
```

Expect `LOOPBACK PASS`.

## Verilator direct loopback

Run from the Chipyard root with `env.sh` sourced.

| Interface | Verilog MAC | Chisel MAC |
|---|---|---|
| RGMII | `EthernetRGMIILoopbackRocketConfig` | `EthMac1GRgmiiLoopbackRocketConfig` |
| GMII | `EthernetGMIILoopbackRocketConfig` | `EthMac1GGmiiLoopbackRocketConfig` |
| XGMII | `EthernetXGMIILoopbackRocketConfig` | Not available |

```bash
source env.sh
export CONFIG=EthernetRGMIILoopbackRocketConfig

make -C sims/verilator \
  CONFIG="$CONFIG" \
  BINARY="$PWD/software/ethernet/ethernet_sim_loopback.riscv" \
  LOADMEM=1 \
  run-binary-fast
```

Expect `LOOPBACK PASS`.

## Verilator raw-L2 file transfer

| Interface | Verilog MAC | Chisel MAC |
|---|---|---|
| RGMII | `EthernetRGMIIFileTransferRocketConfig` | `EthMac1GRgmiiFileTransferRocketConfig` |
| GMII | `EthernetGMIIFileTransferRocketConfig` | `EthMac1GGmiiFileTransferRocketConfig` |
| XGMII | `EthernetXGMIIFileTransferRocketConfig` | Not available |

Run from the Chipyard root with `env.sh` sourced. The file paths passed to the simulator
must be absolute.

```bash
source env.sh
export CONFIG=EthernetRGMIIFileTransferRocketConfig
export FIRMWARE="$PWD/software/ethernet/ethernet_sim_file_transfer.riscv"
export TX_FILE="$PWD/software/ethernet/pc/tx_file.txt"
export RX_FILE="$PWD/software/ethernet/pc/rx_file.txt"

make -C sims/verilator \
  CONFIG="$CONFIG" \
  BINARY="$FIRMWARE" \
  LOADMEM=1 \
  EXTRA_SIM_FLAGS="+ethernet-tx-file=$TX_FILE +ethernet-rx-file=$RX_FILE" \
  run-binary-fast

cmp "$TX_FILE" "$RX_FILE"
```

Expect `[ethernet] FILE TRANSFER PASS` and a successful `cmp`.

## Verilator UDP file transfer

| Interface | Verilog MAC | Chisel MAC |
|---|---|---|
| RGMII | `EthernetRGMIIUdpFileTransferRocketConfig` | `EthMac1GRgmiiUdpFileTransferRocketConfig` |
| GMII | `EthernetGMIIUdpFileTransferRocketConfig` | `EthMac1GGmiiUdpFileTransferRocketConfig` |
| XGMII | `EthernetXGMIIUdpFileTransferRocketConfig` | Not available |

Run from the Chipyard root with `env.sh` sourced. The file paths passed to the simulator
must be absolute.

```bash
source env.sh
export CONFIG=EthernetRGMIIUdpFileTransferRocketConfig
export FIRMWARE="$PWD/software/ethernet/ethernet_udp_sim_file_transfer.riscv"
export TX_FILE="$PWD/software/ethernet/pc/tx_file.txt"
export RX_FILE="$PWD/software/ethernet/pc/rx_file.txt"

make -C sims/verilator \
  CONFIG="$CONFIG" \
  BINARY="$FIRMWARE" \
  LOADMEM=1 \
  EXTRA_SIM_FLAGS="+ethernet-tx-file=$TX_FILE +ethernet-rx-file=$RX_FILE" \
  run-binary-fast

cmp "$TX_FILE" "$RX_FILE"
```

Expect `[ethernet-udp] FILE TRANSFER PASS` and a successful `cmp`.

## FST waveform

This example traces the Chisel RGMII UDP configuration:

```bash
source env.sh
export CONFIG=EthMac1GRgmiiUdpFileTransferRocketConfig
export FIRMWARE="$PWD/software/ethernet/ethernet_udp_sim_file_transfer.riscv"
export TX_FILE="$PWD/software/ethernet/pc/tx_file.txt"
export RX_FILE="$PWD/software/ethernet/pc/rx_file.txt"

make -C sims/verilator \
  CONFIG="$CONFIG" \
  BINARY="$FIRMWARE" \
  LOADMEM=1 \
  USE_FST=1 \
  EXTRA_SIM_FLAGS="+ethernet-tx-file=$TX_FILE +ethernet-rx-file=$RX_FILE" \
  run-binary-debug
```
