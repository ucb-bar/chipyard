package chipyard.fpga.zcu104

import freechips.rocketchip.devices.tilelink.BootROMLocated
import freechips.rocketchip.resources.DTSTimebase
import freechips.rocketchip.subsystem.{ExtMem, SystemBusKey}
import freechips.rocketchip.util.SystemFileName
import org.chipsalliance.cde.config.Config
import sifive.blocks.devices.spi.{PeripherySPIKey, SPIParams}
import sifive.blocks.devices.uart.{PeripheryUARTKey, UARTParams}
import sifive.fpgashells.shell.xilinx.{ZCU104DDRSize, ZCU104ShellPMOD}
import testchipip.serdes.SerialTLKey

import scala.sys.process._

class WithDefaultPeripherals extends Config((site, here, up) => {
  case PeripheryUARTKey => List(UARTParams(address = BigInt(0x64000000L)))
  case PeripherySPIKey => List(SPIParams(rAddress = BigInt(0x64001000L)))
  case ZCU104ShellPMOD => "SDIO"
})

class WithSystemModifications extends Config((site, here, up) => {
  case DTSTimebase => BigInt((1e6).toLong)
  case BootROMLocated(x) => up(BootROMLocated(x), site).map { p =>
    // invoke makefile for sdboot
    val freqMHz = (site(SystemBusKey).dtsFrequency.get / (1000 * 1000)).toLong
    val make = s"make -C fpga/src/main/resources/zcu104/sdboot PBUS_CLK=$freqMHz bin"
    require (make.! == 0, "Failed to build bootrom")
    p.copy(hang = 0x10000, contentFileName = SystemFileName(s"./fpga/src/main/resources/zcu104/sdboot/build/sdboot.bin"))
  }
  case ExtMem => up(ExtMem, site).map(x => x.copy(master = x.master.copy(size = site(ZCU104DDRSize)))) // set extmem to DDR size
  case SerialTLKey => Nil // remove serialized tl port
})

// DOC include start: AbstractZCU104 and Rocket
class WithZCU104Tweaks(freqMHz: Double = 100) extends Config(
  // clocking
  new chipyard.harness.WithAllClocksFromHarnessClockInstantiator ++
  new chipyard.clocking.WithPassthroughClockGenerator ++
  new chipyard.harness.WithHarnessBinderClockFreqMHz(freqMHz) ++
  new chipyard.config.WithUniformBusFrequencies(freqMHz) ++
  // harness binders
  new WithUART ++
  new WithSPISDCard ++
  new WithDDRMem ++
  new WithJTAG ++
  // other configuration
  new WithDefaultPeripherals ++
  new chipyard.config.WithTLBackingMemory ++ // use TL backing memory
  new WithSystemModifications ++ // setup busses, use sdboot bootrom, setup ext. mem. size
  new freechips.rocketchip.subsystem.WithoutTLMonitors ++
  new freechips.rocketchip.subsystem.WithNMemoryChannels(1)
)

class RocketZCU104Config extends Config(
  new WithZCU104Tweaks ++
  new chipyard.RocketConfig
)

/** UART-TSI ZCU104 */
class WithZCU104NoDDRTweaks extends Config(
  new WithZCU104UARTTSI ++
  new sifive.fpgashells.shell.xilinx.WithNoZCU104ShellDDR ++
  new testchipip.tsi.WithUARTTSIClient ++
  new chipyard.harness.WithSerialTLTiedOff ++
  new chipyard.config.WithNoUART ++
  new chipyard.config.WithNoDebug ++
  new chipyard.harness.WithAllClocksFromHarnessClockInstantiator ++
  new chipyard.clocking.WithPassthroughClockGenerator ++
  new chipyard.harness.WithHarnessBinderClockFreqMHz(50) ++
  new chipyard.config.WithUniformBusFrequencies(50) ++
  new freechips.rocketchip.subsystem.WithoutTLMonitors
)

/** RV64 with a 256 KiB DTIM at 0x8000_0000 */
class RocketZCU104NoDDRConfig extends Config(
  new WithZCU104NoDDRTweaks ++
  new freechips.rocketchip.rocket.WithL1DCacheSets(4096) ++
  new chipyard.ScratchpadOnlyRocketConfig
)
// DOC include end: AbstractZCU104 and Rocket

class BoomZCU104Config extends Config(
  new WithZCU104Tweaks(freqMHz = 50) ++
  new chipyard.MegaBoomV3Config
)
