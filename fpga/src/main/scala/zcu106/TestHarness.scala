package chipyard.fpga.zcu106

import chisel3._
import chisel3.util._
import chisel3.experimental.{IO}

import freechips.rocketchip.diplomacy.{LazyModule, LazyRawModuleImp, BundleBridgeSource}
import freechips.rocketchip.config.{Parameters}
import freechips.rocketchip.tilelink.{TLClientNode}

import sifive.fpgashells.shell.xilinx._
import sifive.fpgashells.ip.xilinx.{IBUF, PowerOnResetFPGAOnly}
import sifive.fpgashells.shell._
import sifive.fpgashells.clocks.{ClockGroup, ClockSinkNode, PLLFactoryKey, ResetWrangler}

import sifive.blocks.devices.uart.{PeripheryUARTKey, UARTPortIO}
import sifive.blocks.devices.spi.{PeripherySPIKey, SPIPortIO}

import chipyard._
import chipyard.iobinders.{HasIOBinders}
import chipyard.harness.{ApplyHarnessBinders}

class ZCU106FPGATestHarness(override implicit val p: Parameters) extends ZCU106ShellBasicOverlays {

  def dp = designParameters

  val pmod_is_sdio  = p(ZCU106ShellPMOD) == "SDIO"
  val jtag_location = Some(if (pmod_is_sdio) "FMC_J5" else "PMOD_J55")

  // // Order matters; ddr depends on sys_clock
  val uart      = Overlay(UARTOverlayKey, new UARTZCU106ShellPlacer(this, UARTShellInput()))
  val sdio      = if (pmod_is_sdio) Some(Overlay(SPIOverlayKey, new SDIOZCU106ShellPlacer(this, SPIShellInput()))) else None
  val jtag      = Overlay(JTAGDebugOverlayKey, new JTAGDebugZCU106ShellPlacer(this, JTAGDebugShellInput(location = jtag_location)))
  val cjtag     = Overlay(cJTAGDebugOverlayKey, new cJTAGDebugZCU106ShellPlacer(this, cJTAGDebugShellInput()))
  val jtagBScan = Overlay(JTAGDebugBScanOverlayKey, new JTAGDebugBScanZCU106ShellPlacer(this, JTAGDebugBScanShellInput()))
  val fmc       = Overlay(PCIeOverlayKey, new PCIeZCU106FMCShellPlacer(this, PCIeShellInput()))
  val edge      = Overlay(PCIeOverlayKey, new PCIeZCU106EdgeShellPlacer(this, PCIeShellInput()))
  val sys_clock2 = Overlay(ClockInputOverlayKey, new SysClock2ZCU106ShellPlacer(this, ClockInputShellInput()))
  val ddr2       = Overlay(DDROverlayKey, new DDR2ZCU106ShellPlacer(this, DDRShellInput()))

  val topDesign = LazyModule(p(BuildTop)(dp)).suggestName("chiptop")

 // DOC include start: ClockOverlay
  // place all clocks in the shell
  require(dp(ClockInputOverlayKey).size >= 1)
  val sysClkNode = dp(ClockInputOverlayKey)(0).place(ClockInputDesignInput()).overlayOutput.node

  /*** Connect/Generate clocks ***/

  // connect to the PLL that will generate multiple clocks
  val harnessSysPLL = dp(PLLFactoryKey)()
  harnessSysPLL := sysClkNode

  // create and connect to the dutClock
  println(s"ZCU106 FPGA Base Clock Freq: ${dp(DefaultClockFrequencyKey)} MHz")
  val dutClock = ClockSinkNode(freqMHz = dp(DefaultClockFrequencyKey))
  val dutWrangler = LazyModule(new ResetWrangler)
  val dutGroup = ClockGroup()
  dutClock := dutWrangler.node := dutGroup := harnessSysPLL
 // DOC include end: ClockOverlay

  /*** UART ***/

 // DOC include start: UartOverlay
  // 1st UART goes to the ZCU106 dedicated UART

  val io_uart_bb = BundleBridgeSource(() => (new UARTPortIO(dp(PeripheryUARTKey).head)))
  dp(UARTOverlayKey).head.place(UARTDesignInput(io_uart_bb))
 // DOC include end: UartOverlay

  /*** SPI ***/

  // 1st SPI goes to the ZCU106 SDIO port

  val io_spi_bb = BundleBridgeSource(() => (new SPIPortIO(dp(PeripherySPIKey).head)))
  dp(SPIOverlayKey).head.place(SPIDesignInput(dp(PeripherySPIKey).head, io_spi_bb))

  /*** DDR ***/

  val ddrNode = dp(DDROverlayKey).head.place(DDRDesignInput(dp(ExtTLMem).get.master.base, dutWrangler.node, harnessSysPLL)).overlayOutput.ddr

  // connect 1 mem. channel to the FPGA DDR
  val inParams = topDesign match { case td: ChipTop =>
    td.lazySystem match { case lsys: CanHaveMasterTLMemPort =>
      lsys.memTLNode.edges.in(0)
    }
  }
  val ddrClient = TLClientNode(Seq(inParams.master))
  ddrNode := ddrClient

  val ledOverlays = dp(LEDOverlayKey).map(_.place(LEDDesignInput()))
  val all_leds = ledOverlays.map(_.overlayOutput.led)
  val status_leds = all_leds.take(3)
  val reset_led  = all_leds(4)
  val other_leds = all_leds.drop(4)

  // module implementation
  override lazy val module = new ZCU106FPGATestHarnessImp(this)
}

class ZCU106FPGATestHarnessImp(_outer: ZCU106FPGATestHarness) extends LazyRawModuleImp(_outer) with HasHarnessSignalReferences {

  val zcu106Outer = _outer

  val reset = IO(Input(Bool()))
  _outer.xdc.addPackagePin(reset, "G13")
  _outer.xdc.addIOStandard(reset, "LVCMOS18")

  val resetIBUF = Module(new IBUF)
  resetIBUF.io.I := reset

  val sysclk: Clock = _outer.sysClkNode.out.head._1.clock

  val powerOnReset: Bool = PowerOnResetFPGAOnly(sysclk)
  _outer.sdc.addAsyncPath(Seq(powerOnReset))

  val ereset: Bool = _outer.chiplink.get() match {
    case Some(x: ChipLinkZCU106PlacedOverlay) => !x.ereset_n
    case _ => false.B
  }

  _outer.pllReset := (resetIBUF.io.O || powerOnReset || ereset)

  // reset setup
  val hReset = Wire(Reset())
  hReset := _outer.dutClock.in.head._1.reset


  val buildtopClock = _outer.dutClock.in.head._1.clock
  val buildtopReset = WireInit(hReset)
  val dutReset = hReset.asAsyncReset
  val success = false.B

  val sys_clk_mhz = _outer.sysClkNode.out.head._1.clock
  val clk_50mhz = _outer.dutClock.in.head._1.clock
  val clk_300mhz = _outer.sysClkNode.out.head._2.clock //What is this?

  // Blink the status LEDs for sanity
  withClockAndReset(sys_clk_mhz, _outer.pllReset) {
    val period = (BigInt(100) << 20) / _outer.status_leds.size
    val counter = RegInit(0.U(log2Ceil(period).W))
    val on = RegInit(0.U(log2Ceil(_outer.status_leds.size).W))
    _outer.status_leds.zipWithIndex.map { case (o,s) => o := on === s.U }
    counter := Mux(counter === (period-1).U, 0.U, counter + 1.U)
    when (counter === 0.U) {
      on := Mux(on === (_outer.status_leds.size-1).U, 0.U, on + 1.U)
    }
  }

  withClockAndReset(clk_50mhz, _outer.pllReset) {
    val period = (BigInt(100) << 20) / (_outer.other_leds.size - 1)
    val counter = RegInit(0.U(log2Ceil(period).W))
    val on = RegInit(0.U(log2Ceil(_outer.other_leds.size).W))
    _outer.other_leds.zipWithIndex.map { case (o,s) => o := on === s.U }
    counter := Mux(counter === (period-1).U, 0.U, counter + 1.U)
    when (counter === 0.U) {
      on := Mux(on === (_outer.other_leds.size-1).U, 0.U, on + 1.U)
    }
  }

  _outer.reset_led := _outer.pllReset

  childClock := buildtopClock
  childReset := buildtopReset

  // harness binders are non-lazy
  _outer.topDesign match { case d: HasIOBinders =>
    ApplyHarnessBinders(this, d.lazySystem, d.portMap)
  }

  // check the top-level reference clock is equal to the default
  // non-exhaustive since you need all ChipTop clocks to equal the default
  require(getRefClockFreq == p(DefaultClockFrequencyKey))
}
