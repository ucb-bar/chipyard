package chipyard.fpga.vcu118.bringup

import chisel3._
import chisel3.experimental.{attach}

import freechips.rocketchip.diplomacy._
import chipsalliance.rocketchip.config.{Parameters, Field}

import sifive.fpgashells.shell._
import sifive.fpgashells.ip.xilinx._
import sifive.fpgashells.shell.xilinx._

import sifive.blocks.devices.gpio._


import chipyard.fpga.vcu118.{FMCPMap}

/* Connect the I2C to certain FMC pins */
class BringupI2CVCU118PlacedOverlay(val shell: VCU118ShellBasicOverlays, name: String, val designInput: I2CDesignInput, val shellInput: I2CShellInput)
  extends I2CXilinxPlacedOverlay(name, designInput, shellInput)
{
  shell { InModuleBody {
    require(shellInput.index == 0) // only support 1 I2C <-> FMC connection
    val i2cLocations = List(List(FMCPMap("K11"), FMCPMap("E2")))
    val packagePinsWithPackageIOs = Seq((i2cLocations(shellInput.index)(0), IOPin(io.scl)),
                                        (i2cLocations(shellInput.index)(1), IOPin(io.sda)))

    packagePinsWithPackageIOs foreach { case (pin, io) => {
      shell.xdc.addPackagePin(io, pin)
      shell.xdc.addIOStandard(io, "LVCMOS18")
      shell.xdc.addIOB(io)
    } }
  } }
}

class BringupI2CVCU118ShellPlacer(val shell: VCU118ShellBasicOverlays, val shellInput: I2CShellInput)(implicit val valName: ValName)
  extends I2CShellPlacer[VCU118ShellBasicOverlays]
{
  def place(designInput: I2CDesignInput) = new BringupI2CVCU118PlacedOverlay(shell, valName.name, designInput, shellInput)
}

/* Connect the UART to certain FMC pins */
class BringupUARTVCU118PlacedOverlay(val shell: VCU118ShellBasicOverlays, name: String, val designInput: UARTDesignInput, val shellInput: UARTShellInput)
  extends UARTXilinxPlacedOverlay(name, designInput, shellInput, true)
{
  shell { InModuleBody {
    val packagePinsWithPackageIOs = Seq((FMCPMap("E9"), IOPin(io.ctsn.get)), // unused
                                        (FMCPMap("E10"), IOPin(io.rtsn.get)), // unused
                                        (FMCPMap("C15"), IOPin(io.rxd)),
                                        (FMCPMap("C14"), IOPin(io.txd)))

    packagePinsWithPackageIOs foreach { case (pin, io) => {
      shell.xdc.addPackagePin(io, pin)
      shell.xdc.addIOStandard(io, "LVCMOS18")
      shell.xdc.addIOB(io)
    } }

    // add pullup on ctsn (ctsn is an input that is not used or driven)
    packagePinsWithPackageIOs take 1 foreach { case (pin, io) => {
      shell.xdc.addPullup(io)
    } }
  } }
}

class BringupUARTVCU118ShellPlacer(shell: VCU118ShellBasicOverlays, val shellInput: UARTShellInput)(implicit val valName: ValName)
  extends UARTShellPlacer[VCU118ShellBasicOverlays] {
  def place(designInput: UARTDesignInput) = new BringupUARTVCU118PlacedOverlay(shell, valName.name, designInput, shellInput)
}

/* Connect SPI to ADI device */
class BringupSPIVCU118PlacedOverlay(val shell: VCU118ShellBasicOverlays, name: String, val designInput: SPIDesignInput, val shellInput: SPIShellInput)
   extends SDIOXilinxPlacedOverlay(name, designInput, shellInput)
{
  shell { InModuleBody {
    val packagePinsWithPackageIOs = Seq((FMCPMap("H37"), IOPin(io.spi_clk)),
                                        (FMCPMap("H19"), IOPin(io.spi_cs)),
                                        (FMCPMap("H17"), IOPin(io.spi_dat(0))),
                                        (FMCPMap("H28"), IOPin(io.spi_dat(1))),
                                        (FMCPMap("H29"), IOPin(io.spi_dat(2))),
                                        (FMCPMap("H16"), IOPin(io.spi_dat(3))))

    packagePinsWithPackageIOs foreach { case (pin, io) => {
      shell.xdc.addPackagePin(io, pin)
      shell.xdc.addIOStandard(io, "LVCMOS18")
    } }
    packagePinsWithPackageIOs drop 1 foreach { case (pin, io) => {
      shell.xdc.addPullup(io)
      shell.xdc.addIOB(io)
    } }
  } }
}

class BringupSPIVCU118ShellPlacer(shell: VCU118ShellBasicOverlays, val shellInput: SPIShellInput)(implicit val valName: ValName)
  extends SPIShellPlacer[VCU118ShellBasicOverlays] {
  def place(designInput: SPIDesignInput) = new BringupSPIVCU118PlacedOverlay(shell, valName.name, designInput, shellInput)
}

// TODO: Move this to a different location
// SPI device description for ADI part
class ADISPIDevice(spi: Device, maxMHz: Double = 1) extends SimpleDevice("clkgen", Seq("analog,adi9516-4")) {
  override def parent = Some(spi)
  override def describe(resources: ResourceBindings): Description = {
    val Description(name, mapping) = super.describe(resources)
    val extra = Map("spi-max-frequency" -> Seq(ResourceInt(maxMHz * 1000000)))
    Description(name, mapping ++ extra)
  }
}

/* Connect GPIOs to FMC */
abstract class GPIOXilinxPlacedOverlay(name: String, di: GPIODesignInput, si: GPIOShellInput)
  extends GPIOPlacedOverlay(name, di, si)
{
  def shell: XilinxShell

  shell { InModuleBody {
    (io.gpio zip tlgpioSink.bundle.pins).map { case (ioPin, sinkPin) =>
      val iobuf = Module(new IOBUF)
      iobuf.suggestName(s"gpio_iobuf")
      attach(ioPin, iobuf.io.IO)
      sinkPin.i.ival := iobuf.io.O
      iobuf.io.T := !sinkPin.o.oe
      iobuf.io.I := sinkPin.o.oval
    }
  } }
}

class BringupGPIOVCU118PlacedOverlay(val shell: VCU118ShellBasicOverlays, name: String, val designInput: GPIODesignInput, val shellInput: GPIOShellInput, gpioNames: Seq[String])
   extends GPIOXilinxPlacedOverlay(name, designInput, shellInput)
{
  shell { InModuleBody {
    require(gpioNames.length == io.gpio.length)

    val packagePinsWithIOStdWithPackageIOs = (gpioNames zip io.gpio).map { case (name, io) =>
      val (pin, iostd) = BringupGPIOs.pinMapping(name)
      (pin, iostd, IOPin(io))
    }

    packagePinsWithIOStdWithPackageIOs foreach { case (pin, iostd, io) => {
      shell.xdc.addPackagePin(io, pin)
      shell.xdc.addIOStandard(io, iostd)
      if (iostd == "LVCMOS12") { shell.xdc.addDriveStrength(io, "8") }
    } }
  } }
}

class BringupGPIOVCU118ShellPlacer(shell: VCU118ShellBasicOverlays, val shellInput: GPIOShellInput, gpioNames: Seq[String])(implicit val valName: ValName)
  extends GPIOShellPlacer[VCU118ShellBasicOverlays] {
  def place(designInput: GPIODesignInput) = new BringupGPIOVCU118PlacedOverlay(shell, valName.name, designInput, shellInput, gpioNames)
}


