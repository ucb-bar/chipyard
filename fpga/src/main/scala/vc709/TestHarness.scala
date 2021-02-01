package chipyard.fpga.vc709

import chisel3._
import chisel3.experimental.{IO}

import freechips.rocketchip.diplomacy._
import freechips.rocketchip.config._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.tilelink._

import sifive.fpgashells.shell.xilinx._
import sifive.fpgashells.ip.xilinx._
import sifive.fpgashells.shell._
import sifive.fpgashells.clocks._

import sifive.blocks.devices.uart._
import sifive.blocks.devices.spi._
import sifive.blocks.devices.gpio._

import chipyard.{HasHarnessSignalReferences, HasTestHarnessFunctions, BuildTop, ChipTop, ExtTLMem, CanHaveMasterTLMemPort}
import chipyard.iobinders.{HasIOBinders}
import chipyard.harness.{ApplyHarnessBinders}

case object FPGAFrequencyKey extends Field[Double](100.0)

class VC709FPGATestHarness(override implicit val p: Parameters) extends VC709Shell {

  def dp = designParameters

// Now we removed jtag, cjtag, pcie-fmc, pcie-edge
  // val pmod_is_sdio  = p(VC709ShellPMOD) == "SDIO"
  // val jtag_location = Some(if (pmod_is_sdio) "FMC_J2" else "PMOD_J52")

  // Order matters; ddr depends on sys_clock
  // val jtag      = Overlay(JTAGDebugOverlayKey, new JTAGDebugVC709ShellPlacer(this, JTAGDebugShellInput(location = jtag_location)))
  // val cjtag     = Overlay(cJTAGDebugOverlayKey, new cJTAGDebugVC709ShellPlacer(this, cJTAGDebugShellInput()))
  // val jtagBScan = Overlay(JTAGDebugBScanOverlayKey, new JTAGDebugBScanVC709ShellPlacer(this, JTAGDebugBScanShellInput()))
  // val fmc       = Overlay(PCIeOverlayKey, new PCIeVC709FMCShellPlacer(this, PCIeShellInput()))
  // val edge      = Overlay(PCIeOverlayKey, new PCIeVC709EdgeShellPlacer(this, PCIeShellInput()))
  
  val uart      = Seq.tabulate(1)(i => Overlay(UARTOverlayKey, new UARTVC709ShellPlacer(this, UARTShellInput(index = 0))))
  // val jtag      = Overlay(JTAGDebugOverlayKey, new JTAGDebugVC709ShellPlacer(this, JTAGDebugShellInput()))
  val pcie      = Overlay(PCIeOverlayKey, new PCIeVC709ShellPlacer(this, PCIeShellInput()))
  val ddr1      = Overlay(DDROverlayKey, new DualDDR3VC709ShellPlacer(this, DDRShellInput()))

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
  val dutClock = ClockSinkNode(freqMHz = dp(FPGAFrequencyKey))
  val dutWrangler = LazyModule(new ResetWrangler)
  val dutGroup = ClockGroup()
  dutClock := dutWrangler.node := dutGroup := harnessSysPLL
// DOC include end: ClockOverlay

  /*** UART ***/

// DOC include start: UartOverlay
  // 1st UART goes to the VC709 dedicated UART

  val io_uart_bb = BundleBridgeSource(() => (new UARTPortIO(dp(PeripheryUARTKey).head)))
  dp(UARTOverlayKey).head.place(UARTDesignInput(io_uart_bb))
// DOC include end: UartOverlay

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

  // module implementation
  override lazy val module = new VC709FPGATestHarnessImp(this)
}

class VC709FPGATestHarnessImp(_outer: VC709FPGATestHarness) extends LazyRawModuleImp(_outer) with HasHarnessSignalReferences {

  val VC709Outer = _outer

  val reset = IO(Input(Bool()))
  _outer.xdc.addPackagePin(reset, "AV40")
  _outer.xdc.addIOStandard(reset, "LVCMOS18")

  val resetIBUF = Module(new IBUF)
  resetIBUF.io.I := reset

  val sysclk: Clock = _outer.sysClkNode.out.head._1.clock

  val powerOnReset: Bool = PowerOnResetFPGAOnly(sysclk)
  _outer.sdc.addAsyncPath(Seq(powerOnReset))

  val ereset: Bool = _outer.chiplink.get() match {
    case Some(x: ChipLinkVC709PlacedOverlay) => !x.ereset_n
    case _ => false.B
  }

  _outer.pllReset := (resetIBUF.io.O || powerOnReset || false.B)

  // reset setup
  val hReset = Wire(Reset())
  hReset := _outer.dutClock.in.head._1.reset

  val harnessClock = _outer.dutClock.in.head._1.clock
  val harnessReset = WireInit(hReset)
  val dutReset = hReset.asAsyncReset
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
