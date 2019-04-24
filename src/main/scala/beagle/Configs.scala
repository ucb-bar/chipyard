package beagle

import chisel3._

import freechips.rocketchip.config.{Field, Parameters, Config}
import freechips.rocketchip.subsystem.{RocketTilesKey, WithJtagDTM, WithRationalRocketTiles, WithNMemoryChannels, WithNBanks, WithNBigCores, SystemBusKey, MemoryBusKey, ControlBusKey}
import freechips.rocketchip.system.{BaseConfig}
import freechips.rocketchip.diplomacy.{LazyModule, ValName}

import sifive.blocks.devices.gpio._
import sifive.blocks.devices.spi._
import sifive.blocks.devices.uart._
import sifive.blocks.devices.i2c._
import sifive.blocks.devices.jtag._

import hwacha.{DefaultHwachaConfig}

case object NClusters extends Field[Int]
case object CacheBlockStriping extends Field[Int]

class WithBeagleUnClusterChanges extends Config((site, here, up) => {
  case SystemBusKey => up(SystemBusKey).copy(beatBytes = 16)
  case MemoryBusKey => up(MemoryBusKey).copy(beatBytes = 8)
  case ControlBusKey => {
    val cBus = up(ControlBusKey)
    cBus.copy(errorDevice = cBus.errorDevice.map(e => e.copy(maxTransfer=64)))
  }
  case BeagleSinkIds => 32
  case NClusters => 1
  case CacheBlockStriping => 1
  case BeaglePipelineResetDepth => 5
})

class WithBeagleSiFiveBlocks extends Config((site, here, up) => {
  case PeripheryGPIOKey => Seq(GPIOParams(address = 0x9000, width = 16))
  case PeripherySPIKey => Seq(SPIParams(rAddress = 0xa000))
  case PeripheryI2CKey => Seq(I2CParams(address = 0xb000))
  case PeripheryUARTKey => Seq(UARTParams(address = 0xc000))
  case PeripheryBeagleKey => BeagleParams(scrAddress = 0x110000)
})

class WithHierTiles extends Config((site, here, up) => {
  case RocketTilesKey => up(RocketTilesKey, site) map { r =>
    r.copy(boundaryBuffers = true) }
})

class BeagleRocketConfig extends Config(
  new example.WithBootROM ++
  new freechips.rocketchip.subsystem.WithoutTLMonitors ++
  new WithBeagleUnClusterChanges ++
  new WithBeagleSiFiveBlocks ++
  new WithJtagDTM ++
  new WithHierTiles ++
  new WithRationalRocketTiles ++
  new DefaultHwachaConfig ++
  new WithNMemoryChannels(2) ++
  new WithNBanks(2) ++
  new WithNBigCores(2) ++
  new BaseConfig)
