package chipyard.fpga.vc709

import chisel3._
import chisel3.experimental.{IO}

import freechips.rocketchip.diplomacy._
import freechips.rocketchip.config._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.interrupts._

import sifive.fpgashells.shell.xilinx._
import sifive.fpgashells.ip.xilinx._
import sifive.fpgashells.shell._
import sifive.fpgashells.clocks._

import sifive.blocks.devices.i2c._
import sifive.blocks.devices.uart._
import sifive.blocks.devices.gpio._

import chipyard.{HasHarnessSignalReferences, HasTestHarnessFunctions, BuildTop, ChipTop, ExtTLMem, CanHaveMasterTLMemPort}
import chipyard.iobinders.{HasIOBinders}
import chipyard.harness.{ApplyHarnessBinders}

case object FPGAFrequencyKey extends Field[Double](50.0)

class VC709FPGATestHarness(override implicit val p: Parameters) extends VC709ShellBasicOverlays {

  def dp = designParameters

  // Order matters; ddr depends on sys_clock
  val mem_clock = Overlay(ClockInputOverlayKey, new MemClockVC709ShellPlacer(this, ClockInputShellInput()))
  val gpio      = Overlay(GPIOOverlayKey, new GPIOVC709ShellPlacer(this, GPIOShellInput(), GPIOs.names))
  // val ddr1      = Overlay(DDROverlayKey, new DDR3VC709ShellPlacer(this, DDRShellInput()))

  val topDesign = LazyModule(p(BuildTop)(dp)).suggestName("chiptop")

// DOC include start: ClockOverlay

  require(dp(ClockInputOverlayKey).size > 0, "There must be at least one sysclk.")
  /*** Connect/Generate clocks ***/
  // place all clocks in the shell, and connect to the PLL that will generate
  //  multiple clocks, finally create and connect to the clockSinkNode

  /*** The first clock goes to the system and the first DDR ***/
  val sysClkNode = dp(ClockInputOverlayKey).head.place(ClockInputDesignInput()).overlayOutput.node
  val harnessSysPLL = dp(PLLFactoryKey)()
  val dutGroup = ClockGroup()
  val dutWrangler = LazyModule(new ResetWrangler)
  val dutClock = ClockSinkNode(freqMHz = dp(FPGAFrequencyKey))

  dutClock := dutWrangler.node := dutGroup := harnessSysPLL := sysClkNode

// DOC include end: ClockOverlay

  /*** I2C ***/

  // 1st I2C goes to the VC709 dedicated I2C
  val io_i2c_bb = BundleBridgeSource(() => (new I2CPort))
  dp(I2COverlayKey).head.place(I2CDesignInput(io_i2c_bb))
  
  /*** UART ***/

// DOC include start: UartOverlay
  // 1st UART goes to the VC709 dedicated UART

  val io_uart_bb = BundleBridgeSource(() => (new UARTPortIO(dp(PeripheryUARTKey).head)))
  dp(UARTOverlayKey).head.place(UARTDesignInput(io_uart_bb))
// DOC include end: UartOverlay

  /*** LEDs / GPIO ***/
// DOC include start: GPIOOverlay
  // 1st GPIO goes to the VC709 dedicated GPIO

  val gpioParams = dp(PeripheryGPIOKey).head
  val io_gpio_bb = BundleBridgeSource(() => (new GPIOPortIO(gpioParams)))
  dp(GPIOOverlayKey).head.place(GPIODesignInput(gpioParams, io_gpio_bb))
// DOC include end: GPIOOverlay

  /*** DDR ***/

// DOC include start: DDR3Overlay
  // All DDR3s use the same clock

  var ddrDesignInput = DDRDesignInput(dp(ExtTLMem).get.master.base, dutWrangler.node, harnessSysPLL)
  val ddrClients = topDesign match { case td: ChipTop =>
    td.lazySystem match { case lsys: CanHaveMasterTLMemPort => 
      (dp(DDROverlayKey) zip lsys.memTLNode.edges.in).map { case (ddrOverlay, edge) =>
        val ddtNode = ddrOverlay.place(ddrDesignInput).overlayOutput.ddr
        val ddrClient = TLClientNode(Seq(edge.master))
        ddtNode := ddrClient
        ddrClient
      }
    }
  }
// DOC include end: DDR3Overlay

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
