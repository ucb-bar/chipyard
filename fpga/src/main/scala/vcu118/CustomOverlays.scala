package chipyard.fpga.vcu118.bringup

import chisel3._

import freechips.rocketchip.diplomacy._

import sifive.fpgashells.shell._
import sifive.fpgashells.ip.xilinx._
import sifive.fpgashells.shell.xilinx._

import chipyard.fpga.vcu118.{FMCPMap}

/* Connect the I2C to certain FMC pins */
class BringupI2CVCU118PlacedOverlay(val shell: VCU118Shell, name: String, val designInput: I2CDesignInput, val shellInput: I2CShellInput)
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

class BringupI2CVCU118ShellPlacer(val shell: VCU118Shell, val shellInput: I2CShellInput)(implicit val valName: ValName)
  extends I2CShellPlacer[VCU118Shell]
{
  def place(designInput: I2CDesignInput) = new BringupI2CVCU118PlacedOverlay(shell, valName.name, designInput, shellInput)
}

/* Connect the UART to certain FMC pins */
class BringupUARTVCU118PlacedOverlay(val shell: VCU118Shell, name: String, val designInput: UARTDesignInput, val shellInput: UARTShellInput)
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

class BringupUARTVCU118ShellPlacer(shell: VCU118Shell, val shellInput: UARTShellInput)(implicit val valName: ValName)
  extends UARTShellPlacer[VCU118Shell] {
  def place(designInput: UARTDesignInput) = new BringupUARTVCU118PlacedOverlay(shell, valName.name, designInput, shellInput)
}

/* Connect SPI to ADI device */
