package chipyard.fpga.vc707

import chisel3._
import chisel3.experimental.{IO}

import freechips.rocketchip.diplomacy.{LazyModule, LazyRawModuleImp, BundleBridgeSource}
import freechips.rocketchip.config.{Parameters}
import freechips.rocketchip.tilelink.{TLClientNode}

import sifive.fpgashells.shell.xilinx.{VC707Shell, UARTVC707ShellPlacer, PCIeVC707ShellPlacer, ChipLinkVC707PlacedOverlay}
import sifive.fpgashells.ip.xilinx.{IBUF, PowerOnResetFPGAOnly}
import sifive.fpgashells.shell.{ClockInputOverlayKey, ClockInputDesignInput, UARTOverlayKey, UARTDesignInput, UARTShellInput, SPIOverlayKey, SPIDesignInput, PCIeOverlayKey, PCIeDesignInput, PCIeShellInput, DDROverlayKey, DDRDesignInput}
import sifive.fpgashells.clocks.{ClockGroup, ClockSinkNode, PLLFactoryKey, ResetWrangler}
import sifive.fpgashells.devices.xilinx.xilinxvc707pciex1.{XilinxVC707PCIeX1IO}

import sifive.blocks.devices.uart.{PeripheryUARTKey, UARTPortIO}
import sifive.blocks.devices.spi.{PeripherySPIKey, SPIPortIO}

import chipyard.{HasHarnessSignalReferences, BuildTop, ChipTop, ExtTLMem, CanHaveMasterTLMemPort, DefaultClockFrequencyKey}
import chipyard.iobinders.{HasIOBinders}
import chipyard.harness.{ApplyHarnessBinders}

class VC707FPGATestHarness(override implicit val p: Parameters) extends VC707Shell { outer =>

  def dp = designParameters


  // Order matters; ddr depends on sys_clock
  val uart      = Overlay(UARTOverlayKey, new UARTVC707ShellPlacer(this, UARTShellInput()))
  val pcie       = Overlay(PCIeOverlayKey, new PCIeVC707ShellPlacer(this, PCIeShellInput()))

  val topDesign = LazyModule(p(BuildTop)(dp)).suggestName("chiptop")

  // place all clocks in the shell
  require(dp(ClockInputOverlayKey).size >= 1)
  val sysClkNode = dp(ClockInputOverlayKey)(0).place(ClockInputDesignInput()).overlayOutput.node

  /*** Connect/Generate clocks ***/

  // connect to the PLL that will generate multiple clocks
  val harnessSysPLL = dp(PLLFactoryKey)()
  harnessSysPLL := sysClkNode

  // create and connect to the dutClock
  println(s"VC707 FPGA Base Clock Freq: ${dp(DefaultClockFrequencyKey)} MHz")
  val dutClock = ClockSinkNode(freqMHz = dp(DefaultClockFrequencyKey))
  val dutWrangler = LazyModule(new ResetWrangler)
  val dutGroup = ClockGroup()
  dutClock := dutWrangler.node := dutGroup := harnessSysPLL

  /*** UART ***/

  // 1st UART goes to the VC707 dedicated UART

  val io_uart_bb = BundleBridgeSource(() => (new UARTPortIO(dp(PeripheryUARTKey).head)))
  dp(UARTOverlayKey).head.place(UARTDesignInput(io_uart_bb))

  /*** SPI ***/

  // 1st SPI goes to the VC707 SDIO port

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

  // module implementation
  override lazy val module = new LazyRawModuleImp(this) with HasHarnessSignalReferences {
    val reset = IO(Input(Bool()))
    xdc.addBoardPin(reset, "reset")

    val resetIBUF = Module(new IBUF)
    resetIBUF.io.I := reset

    val sysclk: Clock = sysClkNode.out.head._1.clock
    // val sysclk: Clock = sys_clock.get() match {
    //   case Some(x: SysClockVC707PlacedOverlay) => x.clock
    // }

    val powerOnReset: Bool = PowerOnResetFPGAOnly(sysclk)
    sdc.addAsyncPath(Seq(powerOnReset))

    val ereset: Bool = chiplink.get() match {
      case Some(x: ChipLinkVC707PlacedOverlay) => !x.ereset_n
      case _ => false.B
    }

    pllReset := (resetIBUF.io.O || powerOnReset || ereset)

    // reset setup
    val hReset = Wire(Reset())
    hReset := dutClock.in.head._1.reset

    val buildtopClock = dutClock.in.head._1.clock
    val buildtopReset = WireInit(hReset)
    val dutReset = hReset.asAsyncReset
    val success = false.B

    childClock := buildtopClock
    childReset := buildtopReset

    // harness binders are non-lazy
    topDesign match { case d: HasIOBinders =>
      ApplyHarnessBinders(this, d.lazySystem, d.portMap)
    }

    // check the top-level reference clock is equal to the default
    // non-exhaustive since you need all ChipTop clocks to equal the default
    require(getRefClockFreq == p(DefaultClockFrequencyKey))
  }
}