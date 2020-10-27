package chipyard.fpga.vcu118.bringup

import chisel3._
import chisel3.experimental.{Analog, IO}

import freechips.rocketchip.diplomacy._
import freechips.rocketchip.config.{Parameters, Field}
import freechips.rocketchip.subsystem.{ExtMem, BaseSubsystem}
import freechips.rocketchip.tilelink._

import sifive.fpgashells.shell.xilinx._
import sifive.fpgashells.ip.xilinx._
import sifive.fpgashells.shell._
import sifive.fpgashells.clocks._

import sifive.blocks.devices.uart._
import sifive.blocks.devices.spi._
import sifive.blocks.devices.i2c._
import sifive.blocks.devices.gpio._

import chipyard.harness._
import chipyard.{HasHarnessSignalReferences, HasTestHarnessFunctions, BuildTop, CanHaveMasterTLMemPort, ChipTop}
import chipyard.iobinders.{HasIOBinders}

case object DUTFrequencyKey extends Field[Double](100.0)

class BringupVCU118FPGATestHarness(override implicit val p: Parameters) extends VCU118ShellBasicOverlays {

  def dp = designParameters

  val pmod_is_sdio  = p(VCU118ShellPMOD) == "SDIO"
  val jtag_location = Some(if (pmod_is_sdio) "FMC_J2" else "PMOD_J52")

  // Order matters; ddr depends on sys_clock
  val uart      = Overlay(UARTOverlayKey, new UARTVCU118ShellPlacer(this, UARTShellInput()))
  val sdio      = if (pmod_is_sdio) Some(Overlay(SPIOverlayKey, new SDIOVCU118ShellPlacer(this, SPIShellInput()))) else None
  val jtag      = Overlay(JTAGDebugOverlayKey, new JTAGDebugVCU118ShellPlacer(this, JTAGDebugShellInput(location = jtag_location)))
  val cjtag     = Overlay(cJTAGDebugOverlayKey, new cJTAGDebugVCU118ShellPlacer(this, cJTAGDebugShellInput()))
  val jtagBScan = Overlay(JTAGDebugBScanOverlayKey, new JTAGDebugBScanVCU118ShellPlacer(this, JTAGDebugBScanShellInput()))
  val fmc       = Overlay(PCIeOverlayKey, new PCIeVCU118FMCShellPlacer(this, PCIeShellInput()))
  val edge      = Overlay(PCIeOverlayKey, new PCIeVCU118EdgeShellPlacer(this, PCIeShellInput()))

  val topDesign = LazyModule(p(BuildTop)(dp))

  // place all clocks in the shell
  dp(ClockInputOverlayKey).foreach { _.place(ClockInputDesignInput()) }

  /*** Connect/Generate clocks ***/

  // connect to the PLL that will generate multiple clocks
  val harnessSysPLL = dp(PLLFactoryKey)()
  sys_clock.get() match {
    case Some(x : SysClockVCU118PlacedOverlay) => {
      harnessSysPLL := x.node
    }
  }

  // create and connect to the dutClock
  val dutClock = ClockSinkNode(freqMHz = dp(DUTFrequencyKey))
  val dutWrangler = LazyModule(new ResetWrangler)
  val dutGroup = ClockGroup()
  dutClock := dutWrangler.node := dutGroup := harnessSysPLL

  //InModuleBody {
  //  topDesign.module match { case td: LazyModuleImp => {
  //      td.clock := dutClock.in.head._1.clock
  //      td.reset := dutClock.in.head._1.reset
  //    }
  //  }
  //}

  // connect ref clock to dummy sink node
  ref_clock.get() match {
    case Some(x : RefClockVCU118PlacedOverlay) => {
      val sink = ClockSinkNode(Seq(ClockSinkParameters()))
      sink := x.node
    }
  }

    // extra overlays

  /*** UART ***/

  require(dp(PeripheryUARTKey).size == 2)

  // 1st UART goes to the VCU118 dedicated UART

  // BundleBridgeSource is a was for Diplomacy to connect something from very deep in the design
  // to somewhere much, much higher. For ex. tunneling trace from the tile to the very top level.
  val io_uart_bb = BundleBridgeSource(() => (new UARTPortIO(dp(PeripheryUARTKey).head)))
  dp(UARTOverlayKey).head.place(UARTDesignInput(io_uart_bb))

  // 2nd UART goes to the FMC UART

  val uart_fmc = Overlay(UARTOverlayKey, new BringupUARTVCU118ShellPlacer(this, UARTShellInput()))

  val io_uart_bb_2 = BundleBridgeSource(() => (new UARTPortIO(dp(PeripheryUARTKey).last)))
  dp(UARTOverlayKey).last.place(UARTDesignInput(io_uart_bb_2))

  /*** SPI ***/

  require(dp(PeripherySPIKey).size == 2)

  // 1st SPI goes to the VCU118 SDIO port

  val io_spi_bb = BundleBridgeSource(() => (new SPIPortIO(dp(PeripherySPIKey).head)))
  val sdio_placed = dp(SPIOverlayKey).head.place(SPIDesignInput(dp(PeripherySPIKey).head, io_spi_bb))

  // 2nd SPI goes to the ADI port

  val adi = Overlay(SPIOverlayKey, new BringupSPIVCU118ShellPlacer(this, SPIShellInput()))

  val io_spi_bb_2 = BundleBridgeSource(() => (new SPIPortIO(dp(PeripherySPIKey).last)))
  val adi_placed = dp(SPIOverlayKey).last.place(SPIDesignInput(dp(PeripherySPIKey).last, io_spi_bb_2))

  /*** I2C ***/

  val i2c = Overlay(I2COverlayKey, new BringupI2CVCU118ShellPlacer(this, I2CShellInput()))

  val io_i2c_bb = BundleBridgeSource(() => (new I2CPort))
  dp(I2COverlayKey).head.place(I2CDesignInput(io_i2c_bb))

  /*** GPIO ***/

  val gpio = Seq.tabulate(dp(PeripheryGPIOKey).size)(i => {
    val maxGPIOSupport = 32
    val names = BringupGPIOs.names.slice(maxGPIOSupport*i, maxGPIOSupport*(i+1))
    Overlay(GPIOOverlayKey, new BringupGPIOVCU118ShellPlacer(this, GPIOShellInput(), names))
  })

  val io_gpio_bb = dp(PeripheryGPIOKey).map { p => BundleBridgeSource(() => (new GPIOPortIO(p))) }
  (dp(GPIOOverlayKey) zip dp(PeripheryGPIOKey)).zipWithIndex.map { case ((placer, params), i) =>
    placer.place(GPIODesignInput(params, io_gpio_bb(i)))
  }

  /*** DDR ***/

  val ddrPlaced = dp(DDROverlayKey).head.place(DDRDesignInput(dp(ExtMem).get.master.base, dutWrangler.node, harnessSysPLL))

  // connect 1 mem. channel to the FPGA DDR
  val inParams = topDesign match { case td: ChipTop =>
    td.lazySystem match { case lsys: CanHaveMasterTLMemPort =>
      lsys.memTLNode.edges.in(0)
    }
  }
  val ddrClient = TLClientNode(Seq(inParams.master))
  ddrPlaced.overlayOutput.ddr := ddrClient

  // module implementation
  override lazy val module = new BringupVCU118FPGATestHarnessImp(this)
}

class BringupVCU118FPGATestHarnessImp(_outer: BringupVCU118FPGATestHarness) extends LazyRawModuleImp(_outer) with HasHarnessSignalReferences {

  val outer = _outer

  val reset = IO(Input(Bool()))
  _outer.xdc.addPackagePin(reset, "L19")
  _outer.xdc.addIOStandard(reset, "LVCMOS12")

  val reset_ibuf = Module(new IBUF)
  reset_ibuf.io.I := reset

  val sysclk: Clock = _outer.sys_clock.get() match {
    case Some(x: SysClockVCU118PlacedOverlay) => x.clock
  }

  val powerOnReset: Bool = PowerOnResetFPGAOnly(sysclk)
  _outer.sdc.addAsyncPath(Seq(powerOnReset))

  val ereset: Bool = _outer.chiplink.get() match {
    case Some(x: ChipLinkVCU118PlacedOverlay) => !x.ereset_n
    case _ => false.B
  }

  _outer.pllReset := (reset_ibuf.io.O || powerOnReset || ereset)

  // cy stuff
  val harnessClock = _outer.dutClock.in.head._1.clock
  val harnessReset = WireInit(_outer.dutClock.in.head._1.reset)
  val dutReset = harnessReset
  val success = false.B

  childClock := harnessClock
  childReset := harnessReset

  // harness binders are non-lazy
  _outer.topDesign match { case d: HasTestHarnessFunctions =>
    d.harnessFunctions.foreach(_(this))
  }
  _outer.topDesign match { case d: HasIOBinders =>
    ApplyHarnessBinders(this, d.lazySystem, d.portMap)
  }
}
