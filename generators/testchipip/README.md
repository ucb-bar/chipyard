# testchipip

Useful IP components for chips.
BAR projects generally use these components with [chipyard](https://chipyard.readthedocs.io).

## ``testchipip.boot``
* ``BootAddrReg``: periphery device intended for use with testchipip/bootrom
* ``TileResetCtrl``: memory-mapped Tile-reset registers
* ``CustomBootPin``: I/O for setting non-default boot address to ``BootAddrReg``

## ``testchipip.clocking``
* ``ClockGates, ClockMuxes, ClockDividers``, etc.
* ``ResetSynchronizer``s

## ``testchipip.cosim``
* ``SpikeCosim``: Spike-based cosimulation model
* ``TraceIO``: Utilities for pulling instruction traces out of SoCs
* ``Dromajo``: DEPRECATED Dromajo cosimulation model

## ``testchipip.dram``
* ``SimDRAM``: DRAMSim-backed AXI-4 memory model
* ``SimTLMem``: Magic TileLink memory

## ``testchipip.iceblk``
* ``IceBlk``: Peripheral block device for FireSim

## ``testchipip.serdes``
* ``GenericSerdesser``: Generic Decoupled bidirectional Serdes generators
* ``TLSerdesser``: TileLink bi-directional serializer
* ``PeripheryTLSerial``: Attaches TLSerdesser to chip buses
* ``SerialWidthAdapter``: Converts between serial interfaces of different widths

## ``testchipip.soc``
* ``OffchipBus``: Custom bus for interfacing with off-chip memory
* ``Scratchpad``: TileLink SRAM-backed on-chip scratchpad memory
* ``SimDTM``: Simulation model for interacting with on-chip debug module
* ``TLNetwork``: DEPRECATED mechanism for creating a TileLink network-on-chip. Use Constellation NoC instead

## ``testchipip.spi``
* ``SimSPIFlashModel``: Simulation model for SPI flash memory

## ``testchipip.tsi``
* ``TSIToTileLink``: Converts a TSI master to a TileLink master
* ``SimTSI``: Simulation model for interacting with a TSI interface
* ``TSIHarness``: Utilities for attaching TSI-simulation devices to a TestHarness
* ``PeripheryUARTTSI``: Attaches a TSI-over-UART interface to a bringup FPGA or chip

## ``testchipip.uart``
* ``UARTToSerial``: Converts UART to a chip's serial interface
* ``SimUART``: Simulation model for a chip's UART

## ``bootrom/``
* Custom BootROM for SoCs with extra bringup features

## ``uart_tsi``
* Host utility tool based on FESVR for interfacing with FPGA prototypes or test-chips using the TSI protocol over a UART physical interface

## Usage
Testchipip can be used in your project in one of two ways:
1) As an sbt subproject that depends on rocket-chip, as in [chipyard](https://github.com/ucb-bar/chipyard/)
2) As a maven dependency (e.g. write

```
libraryDependencies += "edu.berkeley.cs" %% "testchipip" % "1.0-020719-SNAPSHOT"
```
in your build.sbt). Check [sonatype](https://oss.sonatype.org/content/repositories/snapshots/edu/berkeley/cs/testchipip_2.12/) to see the latest published version.

