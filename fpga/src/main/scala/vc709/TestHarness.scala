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

class VC709FPGATestHarness(override implicit val p: Parameters) extends VC709ShellBasicOverlays {

  def dp = designParameters

  // Order matters; ddr depends on sys_clock
  // val cjtag     = Overlay(cJTAGDebugOverlayKey, new cJTAGDebugVC709ShellPlacer(this, cJTAGDebugShellInput()))
  // val jtagBScan = Overlay(JTAGDebugBScanOverlayKey, new JTAGDebugBScanVC709ShellPlacer(this, JTAGDebugBScanShellInput()))
  // val fmc       = Overlay(PCIeOverlayKey, new PCIeVC709FMCShellPlacer(this, PCIeShellInput()))
  // val edge      = Overlay(PCIeOverlayKey, new PCIeVC709EdgeShellPlacer(this, PCIeShellInput()))
  // val mem_clock = Overlay(ClockInputOverlayKey, new MemClockVC709ShellPlacer(this, ClockInputShellInput()))
  // val ddr1      = Overlay(DDROverlayKey, new DualDDR3VC709ShellPlacer(this, DDRShellInput()))

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
  // All UART goes to the VC709 dedicated UART
  val io_uart_bb_s = (dp(UARTOverlayKey) zip dp(PeripheryUARTKey)).map {
    case (uartOverlayKey, peripheryUARTKey) => 
      val io_uart_bb = BundleBridgeSource(() => (new UARTPortIO(peripheryUARTKey)))
      uartOverlayKey.place(UARTDesignInput(io_uart_bb))
      io_uart_bb
  }
// DOC include end: UartOverlay

  /*** DDR ***/
// DOC include start: DDR3Overlay
  val ddrDesignInput = DDRDesignInput(dp(ExtTLMem).get.master.base, dutWrangler.node, harnessSysPLL)
  // connect all mem. channels to the FPGA DDR
  val (ddrNodes, ddrClients) = topDesign match {
    case td: ChipTop => td.lazySystem match {
      case lsys: CanHaveMasterTLMemPort => {
        (dp(DDROverlayKey) zip lsys.memTLNode.edges.in).map { case (ddrOverlayKey, inParams) => 
          val ddrNode = ddrOverlayKey.place(ddrDesignInput).overlayOutput.ddr
          val ddrClient = TLClientNode(Seq(inParams.master))
          ddrNode := ddrClient
          (ddrNode, ddrClient)
        }.unzip
      }
    }
  }
  println("ddrNodes: " + ddrNodes.toString())
  println("ddrClients: " + ddrClients.toString())
// DOC include end: DDR3Overlay

  /*** PCIe ***/
  // hook the first PCIe the board has
  // dp(PCIeOverlayKey) foreach { case key => {
      // val pcies = key.place(PCIeDesignInput(wrangler=dutWrangler.node, corePLL=harnessSysPLL)).overlayOutput
      // pcies match { case (pcieNode, pcieInt) => {
        // val pciename = Some(s"pcie_$i")
        // sbus.fromMaster(pciename) { pcieNode }
        // sbus.toFixedWidthSlave(pciename) { pcieNode }
        // ibus.fromSync := pcieInt
        // println(pciename)
      // } }
    // }
  // }

  // module implementation
  override lazy val module = new VC709FPGATestHarnessImp(this)
}

class VC709FPGATestHarnessImp(_outer: VC709FPGATestHarness) extends LazyRawModuleImp(_outer) with HasHarnessSignalReferences {

  val vc709Outer = _outer

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
