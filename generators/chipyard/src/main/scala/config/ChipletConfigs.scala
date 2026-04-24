package chipyard

import org.chipsalliance.cde.config.{Config}
import freechips.rocketchip.diplomacy.{AddressSet}
import freechips.rocketchip.subsystem.{SBUS}
import testchipip.soc.{OBUS, InwardAddressTranslatorParams, OutwardAddressTranslatorParams}

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
      phyParams = testchipip.serdes.DecoupledExternalSyncSerialPhyParams()   // bringup serial-tl is sync'd to external clock
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
      phyParams = testchipip.serdes.CreditedSourceSyncSerialPhyParams() // chip-to-chip serial-tl is symmetric source-sync'd
    ))
  ) ++
  new testchipip.soc.WithOffchipBusClient(SBUS,                     // obus provides path to other chip's memory
    blockRange = Seq(AddressSet(0, (1L << 32) - 1)),                // The lower 4GB is mapped to this chip
    replicationBase = Some(1L << 32)                                // The upper 4GB goes off-chip
  ) ++
  new testchipip.soc.WithOffchipBus ++
  new freechips.rocketchip.rocket.WithNHugeCores(1) ++
  new chipyard.config.AbstractConfig)

// Simulates 2X of the SymmetricChipletRocketConfig in a multi-sim config
class MultiSimSymmetricChipletRocketConfig extends Config(
  new chipyard.harness.WithAbsoluteFreqHarnessClockInstantiator ++
  new chipyard.harness.WithMultiChipSerialTL(chip0=0, chip1=1, chip0portId=1, chip1portId=1) ++
  new chipyard.harness.WithMultiChip(0, new SymmetricChipletRocketConfig) ++
  new chipyard.harness.WithMultiChip(1, new SymmetricChipletRocketConfig)
)

// Similar to the SymmetricChipletRocketConfig, but demonstrates a selectable c2c link
// with two variants of the SerialTL interface
class MultiLinkSymmetricChipletRocketConfig extends Config(
  new testchipip.soc.WithChipIdPin ++                               // Add pin to identify chips
  new chipyard.harness.WithSerialTLTiedOff(tieoffs=Some(Seq(1))) ++ // Tie-off the chip-to-chip link in single-chip sims
  new testchipip.serdes.WithSerialTL(Seq(
    testchipip.serdes.SerialTLParams(                               // 0th serial-tl is chip-to-bringup-fpga
      client = Some(testchipip.serdes.SerialTLClientParams()),      // bringup serial-tl acts only as a client
      phyParams = testchipip.serdes.DecoupledExternalSyncSerialPhyParams()   // bringup serial-tl is sync'd to external clock
    ),
    testchipip.serdes.SerialTLParams(                               // 1st serial-tl is narrow chip-to-chip
      client = Some(testchipip.serdes.SerialTLClientParams()),      // chip-to-chip serial-tl acts as a client
      manager = Some(testchipip.serdes.SerialTLManagerParams(       // chip-to-chip serial-tl managers other chip's memory
        memParams = Seq(testchipip.serdes.ManagerRAMParams(
          address = 0,
          size = 1L << 32,
        )),
        slaveWhere = OBUS
      )),
      phyParams = testchipip.serdes.CreditedSourceSyncSerialPhyParams(phitWidth=1) // narrow link
    ),
    testchipip.serdes.SerialTLParams(                               // 2nd serial-tl is wide chip-to-chip
      client = Some(testchipip.serdes.SerialTLClientParams()),      // chip-to-chip serial-tl acts as a client
      manager = Some(testchipip.serdes.SerialTLManagerParams(       // chip-to-chip serial-tl managers other chip's memory
        memParams = Seq(testchipip.serdes.ManagerRAMParams(
          address = 0,
          size = 1L << 32,
        )),
        slaveWhere = OBUS
      )),
      phyParams = testchipip.serdes.CreditedSourceSyncSerialPhyParams(phitWidth=16) // wide link
    ))
  ) ++
  new testchipip.soc.WithOffchipBusClient(SBUS,                     // obus provides path to other chip's memory
    blockRange = Seq(AddressSet(0, (1L << 32) - 1)),                // The lower 4GB is mapped to this chip
    replicationBase = Some(1L << 32)                                // The upper 4GB goes off-chip
  ) ++
  new testchipip.soc.WithOffchipBus ++
  new freechips.rocketchip.rocket.WithNBigCores(1) ++
  new chipyard.config.AbstractConfig)

// Simulates 2X of the SymmetricChipletRocketConfig in a multi-sim config
class MultiSimMultiLinkSymmetricChipletRocketConfig extends Config(
  new chipyard.harness.WithAbsoluteFreqHarnessClockInstantiator ++
  new chipyard.harness.WithMultiChipSerialTL(chip0=0, chip1=1, chip0portId=1, chip1portId=1) ++
  new chipyard.harness.WithMultiChipSerialTL(chip0=0, chip1=1, chip0portId=2, chip1portId=2) ++
  new chipyard.harness.WithMultiChip(0, new MultiLinkSymmetricChipletRocketConfig) ++
  new chipyard.harness.WithMultiChip(1, new MultiLinkSymmetricChipletRocketConfig)
)

// Core-only chiplet config, where the coherent memory is located on the LLC-chiplet
class RocketCoreChipletConfig extends Config(
  new testchipip.serdes.WithSerialTL(Seq(
    testchipip.serdes.SerialTLParams(
      client = Some(testchipip.serdes.SerialTLClientParams()),
      phyParams = testchipip.serdes.DecoupledExternalSyncSerialPhyParams()     // chip-to-chip serial-tl is symmetric source-sync'd
    ),
    testchipip.serdes.SerialTLParams(
      manager = Some(testchipip.serdes.SerialTLManagerParams(
        cohParams = Seq(testchipip.serdes.ManagerCOHParams(
          address = BigInt("80000000", 16),
          size    = BigInt("100000000", 16)
        )),
        slaveWhere = OBUS,
        isMemoryDevice = true
      )),
      phyParams = testchipip.serdes.CreditedSourceSyncSerialPhyParams()
    )
  )) ++
  new testchipip.soc.WithOffchipBusClient(SBUS) ++
  new testchipip.soc.WithOffchipBus ++
  new testchipip.soc.WithNoScratchpads ++
  new freechips.rocketchip.subsystem.WithIncoherentBusTopology ++
  new freechips.rocketchip.subsystem.WithNoMemPort ++
  new freechips.rocketchip.subsystem.WithNMemoryChannels(0) ++
  new freechips.rocketchip.rocket.WithNHugeCores(1) ++
  new chipyard.config.AbstractConfig)

// LLC-only chiplet
class LLCChipletConfig extends Config(
  new chipyard.harness.WithSerialTLTiedOff ++
  new testchipip.serdes.WithSerialTL(Seq(testchipip.serdes.SerialTLParams(                               // 1st serial-tl is chip-to-chip
    client = Some(testchipip.serdes.SerialTLClientParams(supportsProbe=true)),
    phyParams = testchipip.serdes.CreditedSourceSyncSerialPhyParams()  // chip-to-chip serial-tl is symmetric source-sync'd
  ))) ++
  new freechips.rocketchip.subsystem.WithExtMemSize((1 << 30) * 4L) ++
  new chipyard.NoCoresConfig
)

class MultiSimLLCChipletRocketConfig extends Config(
  new chipyard.harness.WithAbsoluteFreqHarnessClockInstantiator ++
  new chipyard.harness.WithMultiChipSerialTL(chip0=0, chip1=1, chip0portId=1, chip1portId=0) ++
  new chipyard.harness.WithMultiChip(0, new RocketCoreChipletConfig) ++
  new chipyard.harness.WithMultiChip(1, new LLCChipletConfig)
)

// --------------------------------------------
// ------------ IO Chiplet Example ------------
// --------------------------------------------

class ComputeChiplet1Config extends Config(
  new chipyard.harness.WithCTCLoopback ++
  new testchipip.soc.WithChipIdPinWidth(2) ++
  new testchipip.soc.WithChipIdPin ++
  new testchipip.ctc.WithCTC(Seq(new testchipip.ctc.CTCParams(
    translationParams = InwardAddressTranslatorParams(chipID=1, offset=0x100000000L), 
    offchip=Seq.tabulate(3)(i => AddressSet(0x100000000L << i, 0x100000000L - 1)), 
    phyParams = None))) ++ 
  new chipyard.RocketConfig
)

class ComputeChiplet2Config extends Config(
  new chipyard.harness.WithCTCLoopback ++
  new testchipip.soc.WithChipIdPinWidth(2) ++
  new testchipip.soc.WithChipIdPin ++
  new testchipip.ctc.WithCTC(Seq(new testchipip.ctc.CTCParams(
    translationParams = InwardAddressTranslatorParams(chipID=2, offset=0x100000000L), 
    offchip=Seq.tabulate(3)(i => AddressSet(0x100000000L << i, 0x100000000L - 1)), 
    phyParams = None))) ++ 
  new chipyard.RocketConfig
)

class IOChipletConfig extends Config(
  new chipyard.harness.WithCTCTiedOff ++
  new testchipip.soc.WithChipIdPinWidth(2) ++
  new testchipip.soc.WithChipIdPin ++
  new testchipip.ctc.WithCTC(Seq(
    new testchipip.ctc.CTCParams(
      translationParams = InwardAddressTranslatorParams(chipID=0, offset=0x100000000L), 
      offchip=Seq(AddressSet(0x200000000L, 0x100000000L - 1)), 
      phyParams = None), 
    new testchipip.ctc.CTCParams(
      translationParams = InwardAddressTranslatorParams(chipID=0, offset=0x100000000L), 
      offchip=Seq(AddressSet(0x400000000L, 0x100000000L - 1)), 
      phyParams = None)
    )) ++ 
  new chipyard.RocketConfig
)

class TripleChipletConfig extends Config(
  new chipyard.harness.WithAbsoluteFreqHarnessClockInstantiator ++
  new chipyard.harness.WithANDSuccessFn ++
  new chipyard.harness.WithMultiChipCTC(chip0=1, chip1=0, chip0portId=0, chip1portId=0) ++ // C1 to IO port 0
  new chipyard.harness.WithMultiChipCTC(chip0=2, chip1=0, chip0portId=0, chip1portId=1) ++ // C2 to IO port 1
  new chipyard.harness.WithMultiChip(0, new IOChipletConfig) ++
  new chipyard.harness.WithMultiChip(1, new ComputeChiplet1Config) ++
  new chipyard.harness.WithMultiChip(2, new ComputeChiplet2Config) 
)

