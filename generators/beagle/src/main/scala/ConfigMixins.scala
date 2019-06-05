package beagle

import chisel3._

import freechips.rocketchip.config.{Field, Parameters, Config}
import freechips.rocketchip.subsystem.{ExtMem, RocketTilesKey, BankedL2Key, WithJtagDTM, WithRationalRocketTiles, WithNMemoryChannels, WithNBanks, SystemBusKey, MemoryBusKey, ControlBusKey, CacheBlockBytes}
import freechips.rocketchip.diplomacy.{LazyModule, ValName, AddressSet}
import freechips.rocketchip.tile.{LazyRoCC, BuildRoCC, OpcodeSet, TileKey, RocketTileParams}
import freechips.rocketchip.rocket.{RocketCoreParams, BTBParams, DCacheParams, ICacheParams, MulDivParams}

import sifive.blocks.devices.gpio._
import sifive.blocks.devices.spi._
import sifive.blocks.devices.uart._
import sifive.blocks.devices.i2c._
import sifive.blocks.devices.jtag._

import hbwif.tilelink._
import hbwif._

import hwacha.{Hwacha}

import boom.system.{BoomTilesKey}

import systolic.{SystolicArray, SystolicArrayKey, SystolicArrayConfig, Dataflow}

import example.{MultiRoCCKey}

// --------------
// Special MIXINS
// --------------

class WithBeagleChanges extends Config((site, here, up) => {
  case SystemBusKey => up(SystemBusKey).copy(beatBytes = 16)
  case MemoryBusKey => up(MemoryBusKey).copy(beatBytes = 8)
  case ControlBusKey => {
    val cBus = up(ControlBusKey)
    cBus.copy(errorDevice = cBus.errorDevice.map(e => e.copy(maxTransfer=64)))
  }
  case BeaglePipelineResetDepth => 5
  case HbwifPipelineResetDepth => 5
  case CacheBlockStriping => 2
  case LbwifBitWidth => 4
  case PeripheryBeagleKey => BeagleParams(scrAddress = 0x110000)
})

class WithBeagleSimChanges extends Config((site, here, up) => {
  case LbwifBitWidth => 32
  case PeripheryBeagleKey => up(PeripheryBeagleKey).copy(
    uncoreClkDivInit = 1,
    bhClkDivInit = 1,
    rsClkDivInit = 1,
    lbwifClkDivInit = 1)
})

/**
 * Mixin for adding external I/O
 */
class WithBeagleSiFiveBlocks extends Config((site, here, up) => {
  case PeripheryGPIOKey => Seq(GPIOParams(address = 0x9000, width = 11))
  case PeripherySPIKey => Seq(SPIParams(rAddress = 0xa000))
  case PeripheryI2CKey => Seq(I2CParams(address = 0xb000))
  case PeripheryUARTKey => Seq(UARTParams(address = 0xc000))
})

class WithHierTiles extends Config((site, here, up) => {
  case RocketTilesKey => up(RocketTilesKey, site) map { r =>
    r.copy(boundaryBuffers = true) }
  case BoomTilesKey => up(BoomTilesKey, site) map { b =>
    b.copy(boundaryBuffers = true) }
})

/**
 * Mixin to change generic serdes parameters
 *
 * banks should be 1
 * lanes should be 2 or 4
 * clientPort = false
 * managerTL(UH\C) = false
 */
class WithBeagleSerdesChanges extends Config((site, here, up) => {
  case HbwifNumLanes => 2
  case HbwifTLKey => up(HbwifTLKey, site).copy(
    managerAddressSet = AddressSet.misaligned(site(ExtMem).get.master.base, site(ExtMem).get.master.size),
    numBanks = 2,
    numXact = 16,
    clientPort = false,
    managerTLUH = false,
    managerTLC = false)
})

class WithSystolicParams extends Config((site, here, up) => {
  case SystolicArrayKey =>
    SystolicArrayConfig(
      tileRows = 1,
      tileColumns = 1,
      meshRows = 16,
      meshColumns = 16,
      ld_str_queue_length = 10,
      ex_queue_length = 10,
      sp_banks = 4,
      sp_bank_entries = 256 * 1024 * 8 / (4 * 16 * 8), // has to be a multiply of meshRows*tileRows
      sp_width = 8 * 16, // has to be meshRows*tileRows*dataWidth // TODO should this be changeable?
      shifter_banks = 1, // TODO add separate parameters for left and up shifter banks
      depq_len = 256,
      dataflow = Dataflow.BOTH,
      acc_rows = 64 * 1024 * 8 / (16 * 32),
      mem_pipeline = 1
    )
})

// ---------------------
// Systolic Array Mixins
// ---------------------

/**
 * Mixin to add SystolicArrays to cores
 *
 * For ex:
 *   Core 0, 1, 2, 3 have been defined earlier
 *     with hardIds of 0, 1, 2, 3 respectively
 *   And you call WithMultiRoCCSystolicWithHarts(0,1)
 *   Then Core 0 and 1 will get a SystolicArray
 *
 * @param harts Seq of harts to specify which will get a SystolicArray
 */
class WithMultiRoCCSystolic(harts: Int*) extends Config((site, here, up) => {
  case MultiRoCCKey => {
    require(harts.max <= ((up(RocketTilesKey, site).length + up(BoomTilesKey, site).length) - 1))
    up(MultiRoCCKey, site) ++ harts.distinct.map{ i =>
      (i -> Seq((p: Parameters) => {
        implicit val q = p
        implicit val v = implicitly[ValName]
        LazyModule(new SystolicArray(SInt(16.W), SInt(16.W), SInt(32.W), OpcodeSet.custom3)).suggestName("systolic_array")
      }))
    }
  }
})

/**
 * Mixin to add a new Rocket core with hartId of X (last hartId in the system)
 */
class WithMiniRocketCore extends Config((site, here, up) => {
  case RocketTilesKey => up(RocketTilesKey, site) :+
    RocketTileParams(
      name = Some("rocket_tile"),
      core = RocketCoreParams(
        useVM = true,
        mulDiv = Some(MulDivParams(mulUnroll = 8))),
      btb = Some(BTBParams(nEntries = 14, nRAS = 2)),
      dcache = Some(DCacheParams(
        rowBits = site(SystemBusKey).beatBits,
        blockBytes = site(CacheBlockBytes),
        nSets = 64,
        nWays = 4)),
      icache = Some(ICacheParams(
        rowBits = site(SystemBusKey).beatBits,
        blockBytes = site(CacheBlockBytes),
        nSets = 64,
        nWays = 4)),
      hartId = up(BoomTilesKey, site).length + up(RocketTilesKey, site).length)
})
