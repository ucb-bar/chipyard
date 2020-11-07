package chipyard.fpga.vcu118.bringup

import chisel3._
import chisel3.experimental.{attach}

import freechips.rocketchip.diplomacy._
import freechips.rocketchip.config.{Parameters, Field}
import freechips.rocketchip.tilelink.{TLInwardNode, TLAsyncCrossingSink}

import sifive.fpgashells.shell._
import sifive.fpgashells.ip.xilinx._
import sifive.fpgashells.shell.xilinx._
import sifive.fpgashells.clocks._
import sifive.fpgashells.devices.xilinx.xilinxvcu118mig.{XilinxVCU118MIGPads, XilinxVCU118MIGParams, XilinxVCU118MIG}

import testchipip.{TSIHostParams, TSIHostWidgetIO}

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

case class TSIHostShellInput()
case class TSIHostDesignInput(
  wrangler: ClockAdapterNode,
  corePLL: PLLNode,
  tsiHostParams: TSIHostParams,
  node: BundleBridgeSource[TSIHostWidgetIO],
  vc7074gbdimm: Boolean = false
  )(
  implicit val p: Parameters)
case class TSIHostOverlayOutput(ddr: TLInwardNode)
trait TSIHostShellPlacer[Shell] extends ShellPlacer[TSIHostDesignInput, TSIHostShellInput, TSIHostOverlayOutput]

class TSIHostWithDDRIO(val w: Int, val size: BigInt) extends Bundle {
  val tsi = new TSIHostWidgetIO(w)
  val ddr = new XilinxVCU118MIGPads(size)
}

case object TSIHostOverlayKey extends Field[Seq[DesignPlacer[TSIHostDesignInput, TSIHostShellInput, TSIHostOverlayOutput]]](Nil)

abstract class TSIHostPlacedOverlay[IO <: Data](val name: String, val di: TSIHostDesignInput, val si: TSIHostShellInput)
  extends IOPlacedOverlay[IO, TSIHostDesignInput, TSIHostShellInput, TSIHostOverlayOutput]
{
  implicit val p = di.p
}

case object TSIHostVCU118DDRSize extends Field[BigInt](0x40000000L * 2) // 2GB
class TSIHostVCU118PlacedOverlay(val shell: BringupVCU118FPGATestHarness, name: String, val designInput: TSIHostDesignInput, val shellInput: TSIHostShellInput)
  extends TSIHostPlacedOverlay[TSIHostWithDDRIO](name, designInput, shellInput)
{
  val size = p(TSIHostVCU118DDRSize)

  // connect the DDR
  val migParams = XilinxVCU118MIGParams(address = AddressSet.misaligned(di.tsiHostParams.targetBaseAddress, size))
  val mig = LazyModule(new XilinxVCU118MIG(migParams))
  val ioNode = BundleBridgeSource(() => mig.module.io.cloneType)
  val topIONode = shell { ioNode.makeSink() }
  val ddrUI     = shell { ClockSourceNode(freqMHz = 200) }
  val areset    = shell { ClockSinkNode(Seq(ClockSinkParameters())) }
  areset := designInput.wrangler := ddrUI

  // since this uses a separate clk/rst need to put an async crossing
  val asyncSink = LazyModule(new TLAsyncCrossingSink())
  val migClkRstNode = BundleBridgeSource(() => new Bundle {
    val clock = Output(Clock())
    val reset = Output(Bool())
  })
  val topMigClkRstIONode = shell { migClkRstNode.makeSink() }

  // connect the TSI serial
  val tlTsiSerialSink = di.node.makeSink()
  val tsiIoNode = BundleBridgeSource(() => new TSIHostWidgetIO(di.tsiHostParams.serialIfWidth))
  val topTSIIONode = shell { tsiIoNode.makeSink() }

  def overlayOutput = TSIHostOverlayOutput(ddr = mig.node)
  def ioFactory = new TSIHostWithDDRIO(di.tsiHostParams.serialIfWidth, size)

  InModuleBody {
    // connect MIG
    ioNode.bundle <> mig.module.io

    // setup async crossing
    asyncSink.module.clock := migClkRstNode.bundle.clock
    asyncSink.module.reset := migClkRstNode.bundle.reset

    // connect TSI serial
    val tsiSourcePort = tsiIoNode.bundle
    val tsiSinkPort = tlTsiSerialSink.bundle
    tsiSinkPort.serial_clock := tsiSourcePort.serial_clock
    tsiSourcePort.serial.out.bits := tsiSinkPort.serial.out.bits
    tsiSourcePort.serial.out.valid := tsiSinkPort.serial.out.valid
    tsiSinkPort.serial.out.ready := tsiSourcePort.serial.out.ready
    tsiSinkPort.serial.in.bits  := tsiSourcePort.serial.in.bits
    tsiSinkPort.serial.in.valid := tsiSourcePort.serial.in.valid
    tsiSourcePort.serial.in.ready := tsiSinkPort.serial.in.ready
  }

  // connect the DDR port
  shell { InModuleBody {
    require (shell.sys_clock2.get.isDefined, "Use of TSIHostVCU118Overlay depends on SysClock2VCU118Overlay")
    val (sys, _) = shell.sys_clock2.get.get.overlayOutput.node.out(0)
    val (ui, _) = ddrUI.out(0)
    val (ar, _) = areset.in(0)

    // connect the async fifo sink to sys_clock2
    topMigClkRstIONode.bundle.clock := sys.clock
    topMigClkRstIONode.bundle.reset := sys.reset

    val ddrPort = topIONode.bundle.port
    io.ddr <> ddrPort
    ui.clock := ddrPort.c0_ddr4_ui_clk
    ui.reset := /*!ddrPort.mmcm_locked ||*/ ddrPort.c0_ddr4_ui_clk_sync_rst
    ddrPort.c0_sys_clk_i := sys.clock.asUInt
    ddrPort.sys_rst := sys.reset // pllReset
    ddrPort.c0_ddr4_aresetn := !ar.reset

    // This was just copied from the SiFive example, but it's hard to follow.
    // The pins are emitted in the following order:
    // adr[0->13], we_n, cas_n, ras_n, bg, ba[0->1], reset_n, act_n, ck_c, ck_t, cke, cs_n, odt, dq[0->63], dqs_c[0->7], dqs_t[0->7], dm_dbi_n[0->7]
    val allddrpins = Seq(
      "AM27", "AL27", "AP26", "AP25", "AN28", "AM28", "AP28", "AP27", "AN26", "AM26", "AR28", "AR27", "AV25", "AT25", // adr[0->13]
      "AV28", "AU26", "AV26", "AU27", // we_n, cas_n, ras_n, bg
      "AR25", "AU28", // ba[0->1]
      "BD35", "AN25", "AT27", "AT26", "AW28", "AY29", "BB29", // reset_n, act_n, ck_c, ck_t, cke, cs_n, odt
      "BD30", "BE30", "BD32", "BE33", "BC33", "BD33", "BC31", "BD31", "BA32", "BB33", "BA30", "BA31", "AW31", "AW32", "AY32", "AY33", // dq[0->15]
      "AV30", "AW30", "AU33", "AU34", "AT31", "AU32", "AU31", "AV31", "AR33", "AT34", "AT29", "AT30", "AP30", "AR30", "AN30", "AN31", // dq[16->31]
      "BE34", "BF34", "BC35", "BC36", "BD36", "BE37", "BF36", "BF37", "BD37", "BE38", "BC39", "BD40", "BB38", "BB39", "BC38", "BD38", // dq[32->47]
      "BB36", "BB37", "BA39", "BA40", "AW40", "AY40", "AY38", "AY39", "AW35", "AW36", "AU40", "AV40", "AU38", "AU39", "AV38", "AV39", // dq[48->63]
      "BF31", "BA34", "AV29", "AP32", "BF35", "BF39", "BA36", "AW38", // dqs_c[0->7]
      "BF30", "AY34", "AU29", "AP31", "BE35", "BE39", "BA35", "AW37", // dqs_t[0->7]
      "BE32", "BB31", "AV33", "AR32", "BC34", "BE40", "AY37", "AV35") // dm_dbi_n[0->7]

    (IOPin.of(io) zip allddrpins) foreach { case (io, pin) => shell.xdc.addPackagePin(io, pin) }
  } }

  shell.sdc.addGroup(pins = Seq(mig.island.module.blackbox.io.c0_ddr4_ui_clk))
}

class BringupTSIHostVCU118PlacedOverlay(override val shell: BringupVCU118FPGATestHarness, override val name: String, override val designInput: TSIHostDesignInput, override val shellInput: TSIHostShellInput)
  extends TSIHostVCU118PlacedOverlay(shell, name, designInput, shellInput)
{
  // connect the TSI port
  shell { InModuleBody {
    // connect TSI signals
    val tsiPort = topTSIIONode.bundle
    io.tsi <> tsiPort

    require(di.tsiHostParams.serialIfWidth == 4)

    val clkIo = IOPin(io.tsi.serial_clock)
    val packagePinsWithPackageIOs = Seq(
      (FMCPMap("D8"), clkIo),
      (FMCPMap("D17"), IOPin(io.tsi.serial.out.ready)),
      (FMCPMap("D18"), IOPin(io.tsi.serial.out.valid)),
      (FMCPMap("D11"), IOPin(io.tsi.serial.out.bits, 0)),
      (FMCPMap("D12"), IOPin(io.tsi.serial.out.bits, 1)),
      (FMCPMap("D14"), IOPin(io.tsi.serial.out.bits, 2)),
      (FMCPMap("D15"), IOPin(io.tsi.serial.out.bits, 3)),
      (FMCPMap("D26"), IOPin(io.tsi.serial.in.ready)),
      (FMCPMap("D27"), IOPin(io.tsi.serial.in.valid)),
      (FMCPMap("D20"), IOPin(io.tsi.serial.in.bits, 0)),
      (FMCPMap("D21"), IOPin(io.tsi.serial.in.bits, 1)),
      (FMCPMap("D23"), IOPin(io.tsi.serial.in.bits, 2)),
      (FMCPMap("D24"), IOPin(io.tsi.serial.in.bits, 3)))

    packagePinsWithPackageIOs foreach { case (pin, io) => {
      shell.xdc.addPackagePin(io, pin)
      shell.xdc.addIOStandard(io, "LVCMOS18")
    } }

    // Don't add an IOB to the clock
    (packagePinsWithPackageIOs take 1) foreach { case (pin, io) => {
      shell.xdc.addIOB(io)
    } }

    shell.sdc.addClock("TSI_CLK", clkIo, 50)
    shell.sdc.addGroup(pins = Seq(clkIo))
    shell.xdc.clockDedicatedRouteFalse(clkIo)
  } }
}

class BringupTSIHostVCU118ShellPlacer(shell: BringupVCU118FPGATestHarness, val shellInput: TSIHostShellInput)(implicit val valName: ValName)
  extends TSIHostShellPlacer[BringupVCU118FPGATestHarness] {
  def place(designInput: TSIHostDesignInput) = new BringupTSIHostVCU118PlacedOverlay(shell, valName.name, designInput, shellInput)
}

class SysClock2VCU118PlacedOverlay(val shell: VCU118ShellBasicOverlays, name: String, val designInput: ClockInputDesignInput, val shellInput: ClockInputShellInput)
  extends LVDSClockInputXilinxPlacedOverlay(name, designInput, shellInput)
{
  val node = shell { ClockSourceNode(freqMHz = 250, jitterPS = 50)(ValName(name)) }

  shell { InModuleBody {
    shell.xdc.addPackagePin(io.p, "AW26")
    shell.xdc.addPackagePin(io.n, "AW27")
    shell.xdc.addIOStandard(io.p, "DIFF_SSTL12")
    shell.xdc.addIOStandard(io.n, "DIFF_SSTL12")
  } }
}
class SysClock2VCU118ShellPlacer(shell: VCU118ShellBasicOverlays, val shellInput: ClockInputShellInput)(implicit val valName: ValName)
  extends ClockInputShellPlacer[VCU118ShellBasicOverlays]
{
    def place(designInput: ClockInputDesignInput) = new SysClock2VCU118PlacedOverlay(shell, valName.name, designInput, shellInput)
}
