// See LICENSE for license details.
package chipyard.fpga.nexysvideo

import chipyard._
import chipyard.harness._
import chisel3._
import chisel3.util._
import freechips.rocketchip.diplomacy.IdRange
import freechips.rocketchip.prci._
import freechips.rocketchip.subsystem.SystemBusKey
import freechips.rocketchip.tilelink._
import org.chipsalliance.cde.config.Parameters
import org.chipsalliance.diplomacy.bundlebridge.BundleBridgeSource
import org.chipsalliance.diplomacy.lazymodule.LazyModule
import rivet.common.EthernetRGMIIKey
import sifive.blocks.devices.uart._
import sifive.fpgashells.clocks._
import sifive.fpgashells.ip.xilinx.Series7MMCM
import sifive.fpgashells.shell._
import sifive.fpgashells.shell.xilinx._

class NexysVideoHarness(override implicit val p: Parameters) extends NexysVideoShell {
  def dp = designParameters

  val clockOverlay = dp(ClockInputOverlayKey).map(_.place(ClockInputDesignInput())).head
  val harnessSysPLL = dp(PLLFactoryKey)
  val harnessSysPLLNode = harnessSysPLL()
  val dutFreqMHz = (dp(SystemBusKey).dtsFrequency.get / (1000 * 1000)).toInt
  val dutClock = ClockSinkNode(freqMHz = dutFreqMHz)
  println(s"NexysVideo FPGA Base Clock Freq: ${dutFreqMHz} MHz")
  val dutWrangler = LazyModule(new ResetWrangler())
  val dutGroup = ClockGroup()
  dutClock := dutWrangler.node := dutGroup := harnessSysPLLNode

  harnessSysPLLNode := clockOverlay.overlayOutput.node

  val io_uart_bb = BundleBridgeSource(() => new UARTPortIO(dp(PeripheryUARTKey).headOption.getOrElse(UARTParams(0))))
  val uartOverlay = dp(UARTOverlayKey).head.place(UARTDesignInput(io_uart_bb))

  // Optional DDR
  val ddrOverlay = if (p(NexysVideoShellDDR)) Some(dp(DDROverlayKey).head.place(DDRDesignInput(dp(ExtTLMem).get.master.base, dutWrangler.node, harnessSysPLLNode)).asInstanceOf[DDRNexysVideoPlacedOverlay]) else None
  val ddrClient = if (p(NexysVideoShellDDR)) Some(TLClientNode(Seq(TLMasterPortParameters.v1(Seq(TLMasterParameters.v1(
    name = "chip_ddr",
    sourceId = IdRange(0, 1 << dp(ExtTLMem).get.master.idBits)
  )))))) else None
  val ddrBlockDuringReset = if (p(NexysVideoShellDDR)) Some(LazyModule(new TLBlockDuringReset(4))) else None
  if (p(NexysVideoShellDDR)) { ddrOverlay.get.overlayOutput.ddr := ddrBlockDuringReset.get.node := ddrClient.get }

  // Ethernet
  val harnessETHPLL = if (dp(EthernetRGMIIKey).isDefined) Some(new PLLFactory(this, 7, p => Module(new Series7MMCM(PLLParameters(
    name = "eth_pll",
    input = PLLInClockParameters(freqMHz = 100.0),
    req = Seq(
      PLLOutClockParameters(freqMHz = 125.0),
      PLLOutClockParameters(freqMHz = 125.0, phaseDeg = 90)
    )
  ))))) else None
  val harnessETHPLLNode = if (dp(EthernetRGMIIKey).isDefined) Some(harnessETHPLL.get()) else None
  val ethPLLClock = if (dp(EthernetRGMIIKey).isDefined) Some(ClockSourceNode(freqMHz = 100)) else None
  val ethClock_125 = if (dp(EthernetRGMIIKey).isDefined) Some(ClockSinkNode(freqMHz = 125)) else None
  val ethClock_125_90 = if (dp(EthernetRGMIIKey).isDefined) Some(ClockSinkNode(freqMHz = 125, phaseDeg = 90)) else None
  val ethWrangler = if (dp(EthernetRGMIIKey).isDefined) Some(LazyModule(new ResetWrangler())) else None
  val ethGroup = if (dp(EthernetRGMIIKey).isDefined) Some(ClockGroup()) else None
  if (dp(EthernetRGMIIKey).isDefined) {
    ethClock_125.get := ethWrangler.get.node := ethGroup.get := harnessETHPLLNode.get
    ethClock_125_90.get := ethWrangler.get.node := ethGroup.get
    harnessETHPLLNode.get := ethPLLClock.get
  }

  val ledOverlays = dp(LEDOverlayKey).map(_.place(LEDDesignInput()))
  val all_leds = ledOverlays.map(_.overlayOutput.led)
  val status_leds = all_leds.take(2)
  val other_leds = all_leds.drop(2)


  override lazy val module = new HarnessLikeImpl

  class HarnessLikeImpl extends Impl with HasHarnessInstantiators {
    all_leds.foreach(_ := DontCare)
    clockOverlay.overlayOutput.node.out.head._1.reset := ~resetPin

    val clk_100mhz = clockOverlay.overlayOutput.node.out.head._1.clock

    // Blink the status LEDs for sanity
    withClockAndReset(clk_100mhz, dutClock.in.head._1.reset) {
      val period = (BigInt(100) << 20) / status_leds.size
      val counter = RegInit(0.U(log2Ceil(period).W))
      val on = RegInit(0.U(log2Ceil(status_leds.size).W))
      status_leds.zipWithIndex.foreach { case (o,s) => o := on === s.U }
      counter := Mux(counter === (period-1).U, 0.U, counter + 1.U)
      when (counter === 0.U) {
        on := Mux(on === (status_leds.size-1).U, 0.U, on + 1.U)
      }
    }

    other_leds(0) := resetPin

    harnessSysPLL.plls.foreach(_._1.getReset.get := pllReset)

    def referenceClockFreqMHz = dutFreqMHz
    def referenceClock = dutClock.in.head._1.clock
    def referenceReset = dutClock.in.head._1.reset
    def success = { require(requirement = false, "Unused"); false.B }

    if (p(NexysVideoShellDDR)) { 
      ddrOverlay.get.mig.module.clock := harnessBinderClock
      ddrOverlay.get.mig.module.reset := harnessBinderReset
      ddrBlockDuringReset.get.module.clock := harnessBinderClock
      ddrBlockDuringReset.get.module.reset := harnessBinderReset.asBool || !ddrOverlay.get.mig.module.io.port.init_calib_complete
    }

    if (dp(EthernetRGMIIKey).isDefined) {
      ethPLLClock.get.out.head._1.clock := clk_100mhz
      harnessETHPLL.get.plls.foreach(_._1.getReset.get := pllReset)
    }

    instantiateChipTops()
  }
}
