// See LICENSE for license details.
package chipyard.fpga.arty

import org.chipsalliance.cde.config._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.devices.debug._
import freechips.rocketchip.devices.tilelink._
import freechips.rocketchip.system._
import freechips.rocketchip.tile._

import sifive.blocks.devices.uart._

import testchipip.serdes.{SerialTLKey}

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
  new chipyard.config.WithUniformBusFrequencies(32) ++
  new testchipip.serdes.WithNoSerialTL ++
  new testchipip.soc.WithNoScratchpads
)

class TinyRocketArtyConfig extends Config(
  new WithArtyTweaks ++
  new freechips.rocketchip.rocket.WithNBreakpoints(2) ++
  new chipyard.TinyRocketConfig
)
// DOC include end: AbstractArty and Rocket
