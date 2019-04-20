package beagle

import chisel3._
import freechips.rocketchip.config.{Parameters, Config}
import freechips.rocketchip.subsystem.{WithRoccExample, WithNMemoryChannels}
import freechips.rocketchip.diplomacy.{LazyModule, ValName}
import freechips.rocketchip.devices.tilelink.BootROMParams
import freechips.rocketchip.tile.XLen
import testchipip._

class WithBootROM extends Config((site, here, up) => {
  case BootROMParams => BootROMParams(
    contentFileName = s"./bootrom/bootrom.rv${site(XLen)}.img")
})

object ConfigValName {
  implicit val valName = ValName("TestHarness")
}
import ConfigValName._

class WithBeagleTop extends Config((site, here, up) => {
  case BuildTop => (clock: Clock, reset: Bool, p: Parameters) => {
    Module(LazyModule(new BeagleTop()(p)).module)
  }
  case BoomBuildTop => None
})

class WithBeagleBoomTop extends Config((site, here, up) => {
  case BoomBuildTop => (clock: Clock, reset: Bool, p: Parameters) => {
    Module(LazyModule(new BeagleBoomTop()(p)).module)
  }
  case BuildTop => None
})

class BeagleConfig extends Config(
  new WithBeagleTop ++
  new freechips.rocketchip.subsystem.WithoutTLMonitors ++
  new WithBootROM ++
  new freechips.rocketchip.subsystem.WithRationalRocketTiles ++
  new freechips.rocketchip.subsystem.WithNMemoryChannels(2) ++
  new freechips.rocketchip.subsystem.WithNBanks(2) ++
  new freechips.rocketchip.subsystem.WithNBigCores(2) ++
  new freechips.rocketchip.system.BaseConfig)

class BeagleBoomConfig extends Config(
  new WithBeagleBoomTop ++
  new freechips.rocketchip.subsystem.WithoutTLMonitors ++
  new WithBootROM ++
  new freechips.rocketchip.subsystem.WithRationalRocketTiles ++
  new freechips.rocketchip.subsystem.WithNMemoryChannels(2) ++
  new freechips.rocketchip.subsystem.WithNBanks(2) ++
  new boom.system.WithNBoomCores(2) ++
  new boom.system.BoomConfig)
