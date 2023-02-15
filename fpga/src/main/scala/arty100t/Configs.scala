// See LICENSE for license details.
package chipyard.fpga.arty100t

import freechips.rocketchip.config._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.devices.debug._
import freechips.rocketchip.devices.tilelink._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.system._
import freechips.rocketchip.tile._

import sifive.blocks.devices.uart._
import sifive.fpgashells.shell.{DesignKey}

import testchipip.{SerialTLKey}

import chipyard.{BuildSystem}

// don't use FPGAShell's DesignKey
class WithNoDesignKey extends Config((site, here, up) => {
  case DesignKey => (p: Parameters) => new SimpleLazyModule()(p)
})

class WithArty100TTweaks extends Config(
  new WithArty100TUARTTSI ++
  new WithArty100TDDRTL ++
  new WithNoDesignKey ++
  new chipyard.config.WithNoDebug ++ // no jtag
  new chipyard.config.WithNoUART ++ // use UART for the UART-TSI thing instad
  new chipyard.config.WithTLBackingMemory ++
  new freechips.rocketchip.subsystem.WithExtMemSize(BigInt(256) << 20) ++ // 256mb on ARTY
  new freechips.rocketchip.subsystem.WithoutTLMonitors
)

class RocketArty100TConfig extends Config(
  new WithArty100TTweaks ++
  new chipyard.config.WithMemoryBusFrequency(10.0) ++
  new chipyard.config.WithPeripheryBusFrequency(10.0) ++  // Match the sbus and pbus frequency
  new chipyard.config.WithBroadcastManager ++ // no l2
  new chipyard.RocketConfig
)

class NoCoresArty100TConfig extends Config(
  new WithArty100TTweaks ++
  new chipyard.config.WithMemoryBusFrequency(10.0) ++
  new chipyard.config.WithPeripheryBusFrequency(10.0) ++  // Match the sbus and pbus frequency
  new chipyard.config.WithBroadcastManager ++ // no l2
  new chipyard.NoCoresConfig
)

class InitZeroNoCoresArty100TConfig extends Config(
  new WithArty100TTweaks ++
  new chipyard.example.WithInitZero(0x80000000L, 0x1000L) ++
  new chipyard.config.WithMemoryBusFrequency(10.0) ++
  new chipyard.config.WithPeripheryBusFrequency(10.0) ++  // Match the sbus and pbus frequency
  new chipyard.config.WithBroadcastManager ++ // no l2
  new chipyard.NoCoresConfig
)
