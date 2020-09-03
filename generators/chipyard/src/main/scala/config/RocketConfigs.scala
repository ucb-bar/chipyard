package chipyard

import freechips.rocketchip.config.{Config}

// --------------
// Rocket Configs
// --------------

class RocketConfig extends Config(
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++         // single rocket-core
  new chipyard.config.AbstractConfig)

class HwachaRocketConfig extends Config(
  new chipyard.config.WithHwachaTest ++
  new hwacha.DefaultHwachaConfig ++                        // use Hwacha vector accelerator
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new chipyard.config.AbstractConfig)

// DOC include start: GemminiRocketConfig
class GemminiRocketConfig extends Config(
  new gemmini.DefaultGemminiConfig ++                        // use Gemmini systolic array GEMM accelerator
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new chipyard.config.AbstractConfig)
// DOC include end: GemminiRocketConfig

// DOC include start: JtagRocket
class jtagRocketConfig extends Config(
  new chipyard.iobinders.WithSimDebug ++                   // add SimDebug, in addition to default SimSerial
  new freechips.rocketchip.subsystem.WithJtagDTM ++        // sets DTM communication interface to JTAG
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new chipyard.config.AbstractConfig)
// DOC include end: JtagRocket

// DOC include start: DmiRocket
class dmiRocketConfig extends Config(
  new chipyard.iobinders.WithTiedOffSerial ++          // tie-off serial, override default add SimSerial
  new chipyard.iobinders.WithSimDebug ++               // add SimDebug, override default tie-off debug
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new chipyard.config.AbstractConfig)
// DOC include end: DmiRocket

// DOC include start: GCDTLRocketConfig
class GCDTLRocketConfig extends Config(
  new chipyard.example.WithGCD(useAXI4=false, useBlackBox=false) ++          // Use GCD Chisel, connect Tilelink
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new chipyard.config.AbstractConfig)
// DOC include end: GCDTLRocketConfig

// DOC include start: GCDAXI4BlackBoxRocketConfig
class GCDAXI4BlackBoxRocketConfig extends Config(
  new chipyard.example.WithGCD(useAXI4=true, useBlackBox=true) ++          // Use GCD blackboxed verilog, connect by AXI4->Tilelink
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new chipyard.config.AbstractConfig)
// DOC include end: GCDAXI4BlackBoxRocketConfig

class LargeSPIFlashROMRocketConfig extends Config(
  new chipyard.iobinders.WithSimSPIFlashModel(true) ++      // add the SPI flash model in the harness (read-only)
  new chipyard.config.WithSPIFlash ++                       // add the SPI flash controller
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new chipyard.config.AbstractConfig)

class SmallSPIFlashRocketConfig extends Config(
  new chipyard.iobinders.WithSimSPIFlashModel(false) ++     // add the SPI flash model in the harness (writeable)
  new chipyard.config.WithSPIFlash(0x100000) ++             // add the SPI flash controller (1 MiB)
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new chipyard.config.AbstractConfig)

class SimAXIRocketConfig extends Config(
  new chipyard.iobinders.WithSimAXIMem ++                   // drive the master AXI4 memory with a SimAXIMem, a 1-cycle magic memory, instead of default SimDRAM
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new chipyard.config.AbstractConfig)

class SimBlockDeviceRocketConfig extends Config(
  new chipyard.iobinders.WithSimBlockDevice ++             // drive block-device IOs with SimBlockDevice
  new testchipip.WithBlockDevice ++                        // add block-device module to peripherybus
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new chipyard.config.AbstractConfig)

class BlockDeviceModelRocketConfig extends Config(
  new chipyard.iobinders.WithBlockDeviceModel ++           // drive block-device IOs with a BlockDeviceModel
  new testchipip.WithBlockDevice ++                        // add block-device module to periphery bus
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new chipyard.config.AbstractConfig)

// DOC include start: GPIORocketConfig
class GPIORocketConfig extends Config(
  new chipyard.iobinders.WithGPIOTiedOff ++                // tie off GPIO inputs into the top
  new chipyard.config.WithGPIO ++                          // add GPIOs to the peripherybus
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new chipyard.config.AbstractConfig)
// DOC include end: GPIORocketConfig

class QuadRocketConfig extends Config(
  new freechips.rocketchip.subsystem.WithNBigCores(4) ++   // quad-core (4 RocketTiles)
  new chipyard.config.AbstractConfig)

class RV32RocketConfig extends Config(
  new freechips.rocketchip.subsystem.WithRV32 ++           // set RocketTiles to be 32-bit
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new chipyard.config.AbstractConfig)

class GB1MemoryRocketConfig extends Config(
  new freechips.rocketchip.subsystem.WithExtMemSize((1<<30) * 1L) ++ // use 1GB simulated external memory
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new chipyard.config.AbstractConfig)

// DOC include start: Sha3Rocket
class Sha3RocketConfig extends Config(
  new sha3.WithSha3Accel ++                                // add SHA3 rocc accelerator
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new chipyard.config.AbstractConfig)
// DOC include end: Sha3Rocket

// DOC include start: InitZeroRocketConfig
class InitZeroRocketConfig extends Config(
  new chipyard.example.WithInitZero(0x88000000L, 0x1000L) ++                // add InitZero
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new chipyard.config.AbstractConfig)
// DOC include end: InitZeroRocketConfig

class LoopbackNICRocketConfig extends Config(
  new chipyard.iobinders.WithLoopbackNIC ++                        // drive NIC IOs with loopback
  new icenet.WithIceNIC ++                                         // add an IceNIC
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new chipyard.config.AbstractConfig)

// DOC include start: l1scratchpadrocket
class ScratchpadOnlyRocketConfig extends Config(
  new freechips.rocketchip.subsystem.WithNMemoryChannels(0) ++ // remove offchip mem port
  new freechips.rocketchip.subsystem.WithNBanks(0) ++
  new freechips.rocketchip.subsystem.WithNoMemPort ++
  new freechips.rocketchip.subsystem.WithScratchpadsOnly ++    // use rocket l1 DCache scratchpad as base phys mem
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new chipyard.config.AbstractConfig)
// DOC include end: l1scratchpadrocket

class L1ScratchpadRocketConfig extends Config(
  new chipyard.config.WithRocketICacheScratchpad ++   // use rocket ICache scratchpad
  new chipyard.config.WithRocketDCacheScratchpad ++   // use rocket DCache scratchpad
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new chipyard.config.AbstractConfig)

// DOC include start: mbusscratchpadrocket
class MbusScratchpadRocketConfig extends Config(
  new testchipip.WithBackingScratchpad ++                   // add mbus backing scratchpad
  new freechips.rocketchip.subsystem.WithNoMemPort ++       // remove offchip mem port
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new chipyard.config.AbstractConfig)
// DOC include end: mbusscratchpadrocket

class MemBladeConfig extends Config(
  new chipyard.config.WithMemBladeKey ++
  new chipyard.config.WithMemBladeSystem ++
  new freechips.rocketchip.subsystem.WithNTrackersPerBank(4) ++
  new freechips.rocketchip.subsystem.WithNBanks(4) ++
  new freechips.rocketchip.subsystem.WithNMemoryChannels(2) ++
  new chipyard.iobinders.WithUARTAdapter ++
  new chipyard.iobinders.WithTieOffInterrupts ++
  new chipyard.iobinders.WithBlackBoxSimMem ++
  new chipyard.iobinders.WithTiedOffDebug ++
  new chipyard.iobinders.WithSimSerial ++
  new testchipip.WithTSI ++
  new chipyard.config.WithBootROM ++
  new chipyard.config.WithUART ++
  new freechips.rocketchip.subsystem.WithNoMMIOPort ++
  new freechips.rocketchip.subsystem.WithNoSlavePort ++
  new freechips.rocketchip.subsystem.WithNExtTopInterrupts(0) ++
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new freechips.rocketchip.subsystem.WithCoherentBusTopology ++
  new freechips.rocketchip.system.BaseConfig)

class MemBlade1024Config extends Config(
  new chipyard.config.WithMemBladeKey(Some(1024)) ++
  new MemBladeConfig)

class RemoteMemClientConfig extends Config(
  new chipyard.config.WithMemBenchKey ++
  new chipyard.config.WithRemoteMemClientKey ++
  new chipyard.config.WithRemoteMemClientSystem ++
  new chipyard.config.WithStandardL2 ++
  new RocketConfig)

class HwachaRemoteMemClientConfig extends Config(
  new hwacha.WithNLanes(2) ++
  new chipyard.config.WithHwachaNVMTEntries(64) ++
  new chipyard.config.WithHwachaConfPrec ++
  new chipyard.config.WithMemBenchKey ++
  new chipyard.config.WithRemoteMemClientKey ++
  new chipyard.config.WithRemoteMemClientSystem ++
  new chipyard.config.WithStandardL2(4) ++
  new freechips.rocketchip.subsystem.WithNMemoryChannels(2) ++
  new HwachaRocketConfig)

class DRAMCacheConfig extends Config(
  new chipyard.config.WithMemBenchKey ++
  new chipyard.config.WithDRAMCacheKey(4, 8) ++
  new chipyard.config.WithDRAMCacheSystem ++
  new chipyard.config.WithStandardL2 ++
  new memblade.prefetcher.WithPrefetchMiddleManTopology ++
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new chipyard.config.AbstractConfig)

class HwachaDRAMCacheConfig extends Config(
  new hwacha.WithNLanes(2) ++
  new chipyard.config.WithHwachaNVMTEntries(64) ++
  new chipyard.config.WithHwachaConfPrec ++
  new chipyard.config.WithMemBenchKey ++
  new chipyard.config.WithDRAMCacheKey(7, 16, 2) ++
  new chipyard.config.WithDRAMCacheSystem ++
  new chipyard.config.WithStandardL2(4, 6) ++
  new memblade.prefetcher.WithPrefetchMiddleManTopology ++
  new chipyard.config.WithHwachaTest ++
  new hwacha.DefaultHwachaConfig ++
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new chipyard.config.AbstractConfig)

class HwachaDRAMCache1024Config extends Config(
  new chipyard.config.WithDRAMCacheKey(
    7, 1, 2, Some(1024), memblade.cache.BuildBankFunction.trackerFile) ++
  new HwachaDRAMCacheConfig)

// DOC include start: RingSystemBusRocket
class RingSystemBusRocketConfig extends Config(
  new testchipip.WithRingSystemBus ++ // Ring-topology system bus
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new chipyard.config.AbstractConfig)
// DOC include end: RingSystemBusRocket

class StreamingPassthroughRocketConfig extends Config(
  new chipyard.example.WithStreamingPassthrough ++ // use top with tilelink-controlled streaming passthrough
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new chipyard.config.AbstractConfig)

// DOC include start: StreamingFIRRocketConfig
class StreamingFIRRocketConfig extends Config (
  new chipyard.example.WithStreamingFIR ++ // use top with tilelink-controlled streaming FIR
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new chipyard.config.AbstractConfig)
// DOC include end: StreamingFIRRocketConfig

class SmallNVDLARocketConfig extends Config(
  new nvidia.blocks.dla.WithNVDLA("small") ++ // add a small NVDLA
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new chipyard.config.AbstractConfig)

class LargeNVDLARocketConfig extends Config(
  new nvidia.blocks.dla.WithNVDLA("large", true) ++ // add a large NVDLA with synth. rams
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new chipyard.config.AbstractConfig)

class MMIORocketConfig extends Config(
  new chipyard.iobinders.WithTieOffL2FBusAXI ++ // Tie-off the incoming MMIO port
  new chipyard.iobinders.WithSimAXIMMIO ++ // Attach a simulated memory to the outwards MMIO port
  new freechips.rocketchip.subsystem.WithDefaultMMIOPort ++ // add default external master port
  new freechips.rocketchip.subsystem.WithDefaultSlavePort ++ // add default external slave port
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new chipyard.config.AbstractConfig)

// NOTE: This config doesn't work yet because SimWidgets in the TestHarness
// always get the TestHarness clock. The Tiles and Uncore receive the correct clocks
class DividedClockRocketConfig extends Config(
  new chipyard.config.WithTileDividedClock ++                       // Put the Tile on its own clock domain
  new freechips.rocketchip.subsystem.WithRationalRocketTiles ++   // Add rational crossings between RocketTile and uncore
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new chipyard.config.AbstractConfig)

class WithDRAMCacheBinderSet extends Config(
  new chipyard.config.WithMemBladeKey ++
  new icenet.WithIceNIC(inBufFlits = 8192, usePauser = true, ctrlQueueDepth = 64) ++
  new chipyard.iobinders.WithDRAMCacheBinder)

class FullDRAMCacheConfig extends Config(
  new WithDRAMCacheBinderSet ++
  new DRAMCacheConfig)
