package chipyard.fpga.zcu104

import chipyard._
import chipyard.harness._
import chisel3._
import freechips.rocketchip.diplomacy.IdRange
import freechips.rocketchip.prci._
import freechips.rocketchip.subsystem.SystemBusKey
import freechips.rocketchip.tilelink._
import org.chipsalliance.cde.config.Parameters
import org.chipsalliance.diplomacy.bundlebridge.BundleBridgeSource
import org.chipsalliance.diplomacy.lazymodule._
import sifive.blocks.devices.spi.{PeripherySPIKey, SPIPortIO}
import sifive.blocks.devices.uart.{PeripheryUARTKey, UARTParams, UARTPortIO}
import sifive.fpgashells.clocks._
import sifive.fpgashells.ip.xilinx.{IBUF, PowerOnResetFPGAOnly}
import sifive.fpgashells.shell._
import sifive.fpgashells.shell.xilinx._

class ZCU104FPGATestHarness(override implicit val p: Parameters) extends ZCU104ShellBasicOverlays {

  def dp = designParameters

  val pmod_is_sdio  = p(ZCU104ShellPMOD) == "SDIO"
  val jtag_location = Some(if (pmod_is_sdio) "PMOD_J87" else "PMOD_J55")

  // Order matters; ddr depends on sys_clock
  val uart         = Overlay(UARTOverlayKey, new UARTZCU104ShellPlacer(this, UARTShellInput()))
  val sdio         = if (pmod_is_sdio) Some(Overlay(SPIOverlayKey, new SDIOZCU104ShellPlacer(this, SPIShellInput()))) else None
  val jtag         = Overlay(JTAGDebugOverlayKey, new JTAGDebugZCU104ShellPlacer(this, JTAGDebugShellInput(location = jtag_location)))
  val jtagBScan    = Overlay(JTAGDebugBScanOverlayKey, new JTAGDebugBScanZCU104ShellPlacer(this, JTAGDebugBScanShellInput()))
  val sys_clock2   = Overlay(ClockInputOverlayKey, new SysClockZCU104ShellPlacer(this, ClockInputShellInput()))
  override val ddr = if (p(ZCU104ShellDDR)) Some(Overlay(DDROverlayKey, new DDRZCU104ShellPlacer(this, DDRShellInput()))) else None

// DOC include start: ClockOverlay
  // place all clocks in the shell
  require(dp(ClockInputOverlayKey).nonEmpty)
  val sysClkNode = dp(ClockInputOverlayKey).head.place(ClockInputDesignInput()).overlayOutput.node

  /*** Connect/Generate clocks ***/

  // connect to the PLL that will generate multiple clocks
  val harnessSysPLL = dp(PLLFactoryKey)()
  harnessSysPLL := sysClkNode

  // create and connect to the dutClock
  val dutFreqMHz = (dp(SystemBusKey).dtsFrequency.get / (1000 * 1000)).toInt
  val dutClock = ClockSinkNode(freqMHz = dutFreqMHz)
  println(s"ZCU104 FPGA Base Clock Freq: ${dutFreqMHz} MHz")
  val dutWrangler = LazyModule(new ResetWrangler)
  val dutGroup = ClockGroup()
  dutClock := dutWrangler.node := dutGroup := harnessSysPLL
// DOC include end: ClockOverlay

  /*** UART ***/
  // DOC include start: UartOverlay
  // 1st UART goes to the ZCU104 dedicated UART
  // UART-TSI use this bundle bridge to reach the physical PL UART2 overlay.
  val io_uart_bb = BundleBridgeSource(() => new UARTPortIO(dp(PeripheryUARTKey).headOption.getOrElse(UARTParams(0))))
  dp(UARTOverlayKey).head.place(UARTDesignInput(io_uart_bb))
  // DOC include end: UartOverlay

  /*** SPI ***/
  // 1st SPI goes to the ZCU104 SDIO port
  val io_spi_bb = dp(PeripherySPIKey).headOption.map { spiParams =>
    val bridge = BundleBridgeSource(() => new SPIPortIO(spiParams))
    dp(SPIOverlayKey).head.place(SPIDesignInput(spiParams, bridge))
    bridge
  }

  /*** Optional DDR ***/
  val ddrNode = if (p(ZCU104ShellDDR)) Some(dp(DDROverlayKey).head
    .place(DDRDesignInput(dp(ExtTLMem).get.master.base, dutWrangler.node, harnessSysPLL)).overlayOutput.ddr) else None
  val ddrClient = if (p(ZCU104ShellDDR)) Some(TLClientNode(Seq(TLMasterPortParameters.v1(Seq(TLMasterParameters.v1(
    name = "chip_ddr",
    sourceId = IdRange(0, 1 << dp(ExtTLMem).get.master.idBits)
  )))))) else None
  if (p(ZCU104ShellDDR)) {
    ddrNode.get := TLWidthWidget(dp(ExtTLMem).get.master.beatBytes) := ddrClient.get
  }

  /*** JTAG ***/
  val jtagPlacedOverlay = dp(JTAGDebugOverlayKey).head.place(JTAGDebugDesignInput())
  // module implementation
  override lazy val module = new ZCU104FPGATestHarnessImp(this)
}

class ZCU104FPGATestHarnessImp(_outer: ZCU104FPGATestHarness) extends LazyRawModuleImp(_outer) with HasHarnessInstantiators {
  override def provideImplicitClockToLazyChildren = true
  val zcu104Outer = _outer

  val reset = IO(Input(Bool())).suggestName("reset")
  _outer.xdc.addPackagePin(reset, "M11")
  _outer.xdc.addIOStandard(reset, "LVCMOS33")

  val resetIBUF = Module(new IBUF)
  resetIBUF.io.I := reset

  val sysclk: Clock = _outer.sysClkNode.out.head._1.clock

  val powerOnReset: Bool = PowerOnResetFPGAOnly(sysclk)
  _outer.sdc.addAsyncPath(Seq(powerOnReset))


  _outer.pllReset := (resetIBUF.io.O || powerOnReset)

  // reset setup
  val hReset = Wire(Reset())
  hReset := _outer.dutClock.in.head._1.reset

  def referenceClockFreqMHz = _outer.dutFreqMHz
  def referenceClock = _outer.dutClock.in.head._1.clock
  def referenceReset = hReset
  def success = { require(requirement = false, "Unused"); false.B }

  childClock := referenceClock
  childReset := referenceReset

  instantiateChipTops()
}
