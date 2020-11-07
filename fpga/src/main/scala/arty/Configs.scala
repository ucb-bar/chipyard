// See LICENSE for license details.
package chipyard.fpga.arty

import freechips.rocketchip.config._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.devices.debug._
import freechips.rocketchip.devices.tilelink._
import freechips.rocketchip.diplomacy.{DTSModel, DTSTimebase}
import freechips.rocketchip.system._
import freechips.rocketchip.tile._

import sifive.blocks.devices.uart._

import testchipip.{SerialTLKey}

import chipyard.{BuildSystem}

class WithDefaultPeripherals extends Config((site, here, up) => {
  case PeripheryUARTKey => List(
    UARTParams(address = 0x10013000))
  case DTSTimebase => BigInt(32768)
  case JtagDTMKey => new JtagDTMConfig (
    idcodeVersion = 2,
    idcodePartNum = 0x000,
    idcodeManufId = 0x489,
    debugIdleCycles = 5)
  case SerialTLKey => None // remove serialized tl port
})
// DOC include start: AbstractArty and Rocket
class WithArtyTweaks extends Config(
  new WithArtyJTAGHarnessBinder ++
  new WithArtyUARTHarnessBinder ++
  new WithArtyResetHarnessBinder ++
  new WithResetPassthrough ++
  new WithDefaultPeripherals ++
  new freechips.rocketchip.subsystem.WithNBanks(0) ++ // remove L2$
  new freechips.rocketchip.subsystem.WithNoMemPort ++ // remove backing memory
  new freechips.rocketchip.subsystem.WithNBreakpoints(2) ++
  new freechips.rocketchip.subsystem.WithIncoherentBusTopology)

class TinyRocketArtyConfig extends Config(
  new WithArtyTweaks ++
  new chipyard.TinyRocketConfig)
// DOC include start: AbstractArty and Rocket
