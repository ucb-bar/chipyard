package chipyard

import chisel3._

import freechips.rocketchip.subsystem._
import freechips.rocketchip.system._
import freechips.rocketchip.trace._
import org.chipsalliance.cde.config.Parameters
import freechips.rocketchip.devices.tilelink._

// ------------------------------------
// BOOM and/or Rocket Top Level Systems
// ------------------------------------

// DOC include start: DigitalTop
class DigitalTop(implicit p: Parameters) extends ChipyardSystem
  with testchipip.tsi.CanHavePeripheryUARTTSI // Enables optional UART-based TSI transport
  with testchipip.boot.CanHavePeripheryCustomBootPin // Enables optional custom boot pin
  with testchipip.cosim.CanHaveTraceIO // Enables optionally adding trace IO
  with testchipip.soc.CanHaveSubsystemInjectors // Enables the subsystem injector API
  with testchipip.soc.CanHaveSwitchableOffchipBus // Enables optional off-chip-bus with interface-switch
  with testchipip.iceblk.CanHavePeripheryBlockDevice // Enables optionally adding the block device
  with testchipip.serdes.CanHavePeripheryTLSerial // Enables optionally adding the tl-serial interface
  with testchipip.serdes.old.CanHavePeripheryTLSerial // Enables optionally adding the DEPRECATED tl-serial interface
  with testchipip.soc.CanHavePeripheryChipIdPin // Enables optional pin to set chip id for multi-chip configs
  with sifive.blocks.devices.i2c.HasPeripheryI2C // Enables optionally adding the sifive I2C
  with sifive.blocks.devices.timer.HasPeripheryTimer // Enables optionally adding the timer device
  with sifive.blocks.devices.pwm.HasPeripheryPWM // Enables optionally adding the sifive PWM
  with sifive.blocks.devices.uart.HasPeripheryUART // Enables optionally adding the sifive UART
  with sifive.blocks.devices.gpio.HasPeripheryGPIO // Enables optionally adding the sifive GPIOs
  with sifive.blocks.devices.spi.HasPeripherySPIFlash // Enables optionally adding the sifive SPI flash controller
  with sifive.blocks.devices.spi.HasPeripherySPI // Enables optionally adding the sifive SPI port
  with icenet.CanHavePeripheryIceNIC // Enables optionally adding the IceNIC for FireSim
  with chipyard.example.CanHavePeripheryGCD // Enables optionally adding the GCD example widget
  with chipyard.clocking.HasChipyardPRCI // Use Chipyard reset/clock distribution
  with chipyard.clocking.CanHaveClockTap // Enables optionally adding a clock tap output port
  with constellation.soc.CanHaveGlobalNoC // Support instantiating a global NoC interconnect
  with rerocc.CanHaveReRoCCTiles // Support tiles that instantiate rerocc-attached accelerators
{
  override lazy val module = new DigitalTopModule(this)
}

class DigitalTopModule(l: DigitalTop) extends ChipyardSystemModule(l)
  with freechips.rocketchip.util.DontTouch
// DOC include end: DigitalTop
