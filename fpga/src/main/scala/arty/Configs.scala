// See LICENSE for license details.
package chipyard.fpga.arty

import org.chipsalliance.cde.config._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.devices.debug._
import freechips.rocketchip.devices.tilelink._
import freechips.rocketchip.diplomacy.{DTSModel, DTSTimebase}
import freechips.rocketchip.system._
import freechips.rocketchip.tile._

import sifive.blocks.devices.uart._

import testchipip.{SerialTLKey}

import chipyard.{BuildSystem}

// DOC include start: AbstractArty and Rocket
class WithArtyTweaks extends Config(
  new WithArtyDebugResetHarnessBinder ++
  new WithArtyJTAGResetHarnessBinder ++
  new WithArtyJTAGHarnessBinder ++
  new WithArtyUARTHarnessBinder ++
  new WithDebugResetPassthrough ++

  new chipyard.harness.WithHarnessBinderClockFreqMHz(32) ++
  new chipyard.harness.WithAllClocksFromHarnessClockInstantiator ++
  new chipyard.config.WithDTSTimebase(32000) ++
  new chipyard.config.WithSystemBusFrequency(32) ++
  new chipyard.config.WithPeripheryBusFrequency(32) ++
  new testchipip.WithNoSerialTL
)

class TinyRocketArtyConfig extends Config(
  new WithArtyTweaks ++
  new freechips.rocketchip.subsystem.WithNBreakpoints(2) ++
  new chipyard.TinyRocketConfig
)
// DOC include end: AbstractArty and Rocket
