package chipyard

import org.chipsalliance.cde.config.{Config}
import freechips.rocketchip.diplomacy.{AddressSet}
import freechips.rocketchip.subsystem.{SBUS}
import testchipip.soc.{OBUS}

// ------------------------------------------------
// Configs demonstrating chip-to-chip communication
// ------------------------------------------------

// Simple design which exposes a second serial-tl port that can connect to another instance of itself
class SymmetricChipletRocketConfig extends Config(
  new testchipip.soc.WithChipIdPin ++                               // Add pin to identify chips
  new chipyard.harness.WithSerialTLTiedOff(tieoffs=Some(Seq(1))) ++ // Tie-off the chip-to-chip link in single-chip sims
  new testchipip.serdes.WithSerialTL(Seq(
    testchipip.serdes.SerialTLParams(                               // 0th serial-tl is chip-to-bringup-fpga
      client = Some(testchipip.serdes.SerialTLClientParams()),      // bringup serial-tl acts only as a client
      phyParams = testchipip.serdes.ExternalSyncSerialParams()      // bringup serial-tl is sync'd to external clock
    ),
    testchipip.serdes.SerialTLParams(                               // 1st serial-tl is chip-to-chip
      client = Some(testchipip.serdes.SerialTLClientParams()),      // chip-to-chip serial-tl acts as a client
      manager = Some(testchipip.serdes.SerialTLManagerParams(       // chip-to-chip serial-tl managers other chip's memory
        memParams = Seq(testchipip.serdes.ManagerRAMParams(
          address = 0,
          size = 1L << 32,
        )),
        slaveWhere = OBUS
      )),
      phyParams = testchipip.serdes.SourceSyncSerialParams()        // chip-to-chip serial-tl is symmetric source-sync'd
    ))
  ) ++
  new testchipip.soc.WithOffchipBusClient(SBUS,                     // obus provides path to other chip's memory
    blockRange = Seq(AddressSet(0, (1L << 32) - 1)),                // The lower 4GB is mapped to this chip
    replicationBase = Some(1L << 32)                                // The upper 4GB goes off-chip
  ) ++
  new testchipip.soc.WithOffchipBus ++
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new chipyard.config.AbstractConfig)

// Simulates 2X of the SymmetricChipletRocketConfig in a multi-sim config
class MultiSimSymmetricChipletRocketConfig extends Config(
  new chipyard.harness.WithAbsoluteFreqHarnessClockInstantiator ++
  new chipyard.harness.WithMultiChipSerialTL(chip0=0, chip1=1, chip0portId=1, chip1portId=1) ++
  new chipyard.harness.WithMultiChip(0, new SymmetricChipletRocketConfig) ++
  new chipyard.harness.WithMultiChip(1, new SymmetricChipletRocketConfig)
)
