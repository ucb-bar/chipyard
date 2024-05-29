package chipyard.fpga.vcu118

import chisel3._
import chisel3.experimental.{IO}

import freechips.rocketchip.diplomacy.{LazyModule, LazyRawModuleImp, BundleBridgeSource}
import org.chipsalliance.cde.config.{Parameters}
import freechips.rocketchip.tilelink._
import freechips.rocketchip.diplomacy.{IdRange, TransferSizes}
import freechips.rocketchip.subsystem.{SystemBusKey}
import freechips.rocketchip.prci._
import sifive.fpgashells.shell.xilinx._
import sifive.fpgashells.ip.xilinx.{IBUF, PowerOnResetFPGAOnly}
import sifive.fpgashells.shell._
import sifive.fpgashells.clocks._

import sifive.blocks.devices.uart.{PeripheryUARTKey, UARTPortIO}
import sifive.blocks.devices.spi.{PeripherySPIKey, SPIPortIO}

import chipyard._
import chipyard.harness._

class VCU118FPGATestHarnessBareMetal(override implicit val p: Parameters) extends VCU118ShellBasicOverlays {
  
  def dp = designParameters

  val uart      = Overlay(UARTOverlayKey, new UARTVCU118ShellPlacer(this, UARTShellInput()))
  val sys_clock2 = Overlay(ClockInputOverlayKey, new SysClock2VCU118ShellPlacer(this, ClockInputShellInput()))

  // DOC include start: ClockOverlay
    // place all clocks in the shell
    require(dp(ClockInputOverlayKey).size >= 1)
    val sysClkNode = dp(ClockInputOverlayKey)(0).place(ClockInputDesignInput()).overlayOutput.node

    /*** Connect/Generate clocks ***/

    // connect to the PLL that will generate multiple clocks
    val harnessSysPLL = dp(PLLFactoryKey)()
    harnessSysPLL := sysClkNode

    // create and connect to the dutClock
    val dutFreqMHz = (dp(SystemBusKey).dtsFrequency.get / (1000 * 1000)).toInt
    val dutClock = ClockSinkNode(freqMHz = dutFreqMHz)
    println(s"VCU118 FPGA Base Clock Freq: ${dutFreqMHz} MHz")
    val dutWrangler = LazyModule(new ResetWrangler)
    val dutGroup = ClockGroup()
    dutClock := dutWrangler.node := dutGroup := harnessSysPLL
    /*** UART ***/

  // DOC include start: UartOverlay
    // 1st UART goes to the VCU118 dedicated UART

    val io_uart_bb = BundleBridgeSource(() => (new UARTPortIO(dp(PeripheryUARTKey).head)))
    dp(UARTOverlayKey).head.place(UARTDesignInput(io_uart_bb))
  // DOC include end: UartOverlay

  override lazy val module = new VCU118FPGATestHarnessImpBareMetal(this)
}

class VCU118FPGATestHarnessImpBareMetal(_outer: VCU118FPGATestHarnessBareMetal) extends LazyRawModuleImp(_outer) with HasHarnessInstantiators {
  override def provideImplicitClockToLazyChildren = true
  val vcu118Outer = _outer

  val reset = IO(Input(Bool())).suggestName("reset")
  _outer.xdc.addPackagePin(reset, "L19")
  _outer.xdc.addIOStandard(reset, "LVCMOS12")

  val resetIBUF = Module(new IBUF)
  resetIBUF.io.I := reset

  val sysclk: Clock = _outer.sysClkNode.out.head._1.clock

  val powerOnReset: Bool = PowerOnResetFPGAOnly(sysclk)
  _outer.sdc.addAsyncPath(Seq(powerOnReset))

  val ereset: Bool = _outer.chiplink.get() match {
    case Some(x: ChipLinkVCU118PlacedOverlay) => !x.ereset_n
    case _ => false.B
  }

  _outer.pllReset := (resetIBUF.io.O || powerOnReset || ereset)

  // reset setup
  val hReset = Wire(Reset())
  hReset := _outer.dutClock.in.head._1.reset

  def referenceClockFreqMHz = _outer.dutFreqMHz
  def referenceClock = _outer.dutClock.in.head._1.clock
  def referenceReset = hReset
  def success = { require(false, "Unused"); false.B }

  childClock := referenceClock
  childReset := referenceReset

  instantiateChipTops()
}
