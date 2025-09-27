package chipyard

import chisel3._

import freechips.rocketchip.subsystem._
import freechips.rocketchip.system._
import org.chipsalliance.cde.config.Parameters
import freechips.rocketchip.devices.tilelink._

import freechips.rocketchip.diplomacy.{AsynchronousCrossing}
import cordic.WithCORDIC
// ------------------------------------
// BOOM and/or Rocket Top Level Systems
// ------------------------------------

// DOC include start: DigitalTop
class DigitalTop(implicit p: Parameters) extends ChipyardSystem
  with testchipip.tsi.CanHavePeripheryUARTTSI // Enables optional UART-based TSI transport
  with testchipip.boot.CanHavePeripheryCustomBootPin // Enables optional custom boot pin
  with testchipip.boot.CanHavePeripheryBootAddrReg // Use programmable boot address register
  with testchipip.cosim.CanHaveTraceIO // Enables optionally adding trace IO
  with testchipip.soc.CanHaveBankedScratchpad // Enables optionally adding a banked scratchpad
  with testchipip.iceblk.CanHavePeripheryBlockDevice // Enables optionally adding the block device
  with testchipip.serdes.CanHavePeripheryTLSerial // Enables optionally adding the backing memory and serial adapter
  with testchipip.soc.CanHavePeripheryChipIdPin // Enables optional pin to set chip id for multi-chip configs
  with sifive.blocks.devices.i2c.HasPeripheryI2C // Enables optionally adding the sifive I2C
  with sifive.blocks.devices.pwm.HasPeripheryPWM // Enables optionally adding the sifive PWM
  with sifive.blocks.devices.uart.HasPeripheryUART // Enables optionally adding the sifive UART
  with sifive.blocks.devices.gpio.HasPeripheryGPIO // Enables optionally adding the sifive GPIOs
  with sifive.blocks.devices.spi.HasPeripherySPIFlash // Enables optionally adding the sifive SPI flash controller
  with sifive.blocks.devices.spi.HasPeripherySPI // Enables optionally adding the sifive SPI port
  with icenet.CanHavePeripheryIceNIC // Enables optionally adding the IceNIC for FireSim
  with chipyard.example.CanHavePeripheryInitZero // Enables optionally adding the initzero example widget
  with chipyard.example.CanHavePeripheryGCD // Enables optionally adding the GCD example widget
  with chipyard.example.CanHavePeripheryStreamingFIR // Enables optionally adding the DSPTools FIR example widget
  with chipyard.example.CanHavePeripheryStreamingPassthrough // Enables optionally adding the DSPTools streaming-passthrough example widget
  with nvidia.blocks.dla.CanHavePeripheryNVDLA // Enables optionally having an NVDLA
  with chipyard.clocking.HasChipyardPRCI // Use Chipyard reset/clock distribution
  with chipyard.clocking.CanHaveClockTap // Enables optionally adding a clock tap output port
  with fftgenerator.CanHavePeripheryFFT // Enables optionally having an MMIO-based FFT block
  with constellation.soc.CanHaveGlobalNoC // Support instantiating a global NoC interconnect
  with cordic.CanHavePeripheryCORDIC // Enables optionally having an MMIO-based CORDIC block
{
  override lazy val module = new DigitalTopModule(this)
}

class DigitalTopModule[+L <: DigitalTop](l: L) extends ChipyardSystemModule(l)
  with sifive.blocks.devices.i2c.HasPeripheryI2CModuleImp
  with sifive.blocks.devices.pwm.HasPeripheryPWMModuleImp
  with sifive.blocks.devices.uart.HasPeripheryUARTModuleImp
  with sifive.blocks.devices.gpio.HasPeripheryGPIOModuleImp
  with sifive.blocks.devices.spi.HasPeripherySPIFlashModuleImp
  with sifive.blocks.devices.spi.HasPeripherySPIModuleImp
  with freechips.rocketchip.util.DontTouch
// DOC include end: DigitalTop
