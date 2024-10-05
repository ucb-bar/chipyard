// See LICENSE for license details.
package chipyard.fpga.zcu106.zcu106tsi

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

class WithZCU106TSITweaks extends Config(
  new WithZCU106TSIUARTTSI ++
  new WithZCU106TSIDDRTL ++
  new WithNoDesignKey ++
  //Still neede IO pass through?
  //new WithUARTIOPassthrough ++
  //new WithSPIIOPassthrough ++
  
  new chipyard.config.WithNoDebug ++ // no jtag
  new chipyard.config.WithNoUART ++ // use UART for the UART-TSI thing instad
  new chipyard.config.WithTLBackingMemory ++ // FPGA-shells converts the AXI to TL for us
  new freechips.rocketchip.subsystem.WithExtMemSize(BigInt(2000) << 20) ++ // 200gb on ARTY
  new freechips.rocketchip.subsystem.WithoutTLMonitors)

class RocketZCU106TSIConfig extends Config(
  new WithZCU106TSITweaks ++
  new chipyard.config.WithMemoryBusFrequency(50.0) ++
  new chipyard.config.WithPeripheryBusFrequency(50.0) ++  // Match the sbus and pbus frequency
  new chipyard.config.WithBroadcastManager ++ // no l2
  new chipyard.RocketConfig)

class BoomZCU106TSIConfig extends Config(
  new WithZCU106TSITweaks ++
  new chipyard.config.WithMemoryBusFrequency(50.0) ++
  new chipyard.config.WithPeripheryBusFrequency(50.0) ++  // Match the sbus and pbus frequency
  new chipyard.config.WithBroadcastManager ++ // no l2
  new chipyard.SmallBoomConfig)

class TestBoomZCU106TSIConfig extends Config(
  new WithZCU106TSITweaks ++
  new chipyard.config.WithMemoryBusFrequency(50.0) ++
  new chipyard.config.WithPeripheryBusFrequency(50.0) ++  // Match the sbus and pbus frequency
  new chipyard.config.WithBroadcastManager ++ // no l2
  new chipyard.SmallBoomConfig)

class UART230400RocketZCU106TSIConfig extends Config(
  new WithZCU106TSIUARTTSI(uartBaudRate = 230400) ++
  new RocketZCU106TSIConfig)

class UART460800RocketZCU106TSIConfig extends Config(
  new WithZCU106TSIUARTTSI(uartBaudRate = 460800) ++
  new RocketZCU106TSIConfig)

class UART921600RocketZCU106TSIConfig extends Config(
  new WithZCU106TSIUARTTSI(uartBaudRate = 921600) ++
  new RocketZCU106TSIConfig)


class NoCoresZCU106TSIConfig extends Config(
  new WithZCU106TSITweaks ++
  new chipyard.config.WithMemoryBusFrequency(50.0) ++
  new chipyard.config.WithPeripheryBusFrequency(50.0) ++  // Match the sbus and pbus frequency
  new chipyard.config.WithBroadcastManager ++ // no l2
  new chipyard.NoCoresConfig)
