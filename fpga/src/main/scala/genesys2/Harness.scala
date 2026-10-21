// See LICENSE for license details.
package chipyard.fpga.genesys2

import chipyard._
import chipyard.harness._
import chisel3._
import chisel3.util._
import freechips.rocketchip.diplomacy.IdRange
import freechips.rocketchip.prci._
import freechips.rocketchip.subsystem.SystemBusKey
import freechips.rocketchip.tilelink._
import org.chipsalliance.cde.config.Parameters
import org.chipsalliance.diplomacy._
import org.chipsalliance.diplomacy.bundlebridge.BundleBridgeSource
import org.chipsalliance.diplomacy.lazymodule.LazyModule
import sifive.blocks.devices.uart._
import sifive.fpgashells.clocks._
import sifive.fpgashells.shell._
import sifive.fpgashells.shell.xilinx._

class Genesys2Harness(override implicit val p: Parameters) extends Genesys2Shell {
  def dp = designParameters

  val clockOverlay = dp(ClockInputOverlayKey).map(_.place(ClockInputDesignInput())).head
  val harnessSysPLL = dp(PLLFactoryKey)
  val harnessSysPLLNode = harnessSysPLL()
  val dutFreqMHz = (dp(SystemBusKey).dtsFrequency.get / (1000 * 1000)).toInt
  val dutClock = ClockSinkNode(freqMHz = dutFreqMHz)
  println(s"Genesys2 FPGA Base Clock Freq: ${dutFreqMHz} MHz")
  val dutWrangler = LazyModule(new ResetWrangler())
  val dutGroup = ClockGroup()
  dutClock := dutWrangler.node := dutGroup := harnessSysPLLNode

  harnessSysPLLNode := clockOverlay.overlayOutput.node

  val io_uart_bb = BundleBridgeSource(() => new UARTPortIO(dp(PeripheryUARTKey).headOption.getOrElse(UARTParams(0))))
  val uartOverlay = dp(UARTOverlayKey).head.place(UARTDesignInput(io_uart_bb))

  // Optional DDR
  val ddrOverlay = if (p(Genesys2ShellDDR)) Some(dp(DDROverlayKey).head.place(DDRDesignInput(dp(ExtTLMem).get.master.base, dutWrangler.node, harnessSysPLLNode)).asInstanceOf[DDRGenesys2PlacedOverlay]) else None
  val ddrClient = if (p(Genesys2ShellDDR)) Some(TLClientNode(Seq(TLMasterPortParameters.v1(Seq(TLMasterParameters.v1(
    name = "chip_ddr",
    sourceId = IdRange(0, 1 << dp(ExtTLMem).get.master.idBits)
  )))))) else None
  val ddrBlockDuringReset = if (p(Genesys2ShellDDR)) Some(LazyModule(new TLBlockDuringReset(4))) else None
  if (p(Genesys2ShellDDR)) { ddrOverlay.get.overlayOutput.ddr := ddrBlockDuringReset.get.node := ddrClient.get }

  val ledOverlays = dp(LEDOverlayKey).map(_.place(LEDDesignInput()))
  val all_leds = ledOverlays.map(_.overlayOutput.led)
  val status_leds = all_leds.take(2)
  val other_leds = all_leds.drop(2)


  override lazy val module = new HarnessLikeImpl

  class HarnessLikeImpl extends Impl with HasHarnessInstantiators {
    all_leds.foreach(_ := DontCare)
    clockOverlay.overlayOutput.node.out.head._1.reset := ~resetPin

    val clk_200mhz = clockOverlay.overlayOutput.node.out.head._1.clock

    // Blink the status LEDs for sanity
    withClockAndReset(clk_200mhz, dutClock.in.head._1.reset) {
      val period = (BigInt(100) << 20) / status_leds.size
      val counter = RegInit(0.U(log2Ceil(period).W))
      val on = RegInit(0.U(log2Ceil(status_leds.size).W))
      status_leds.zipWithIndex.foreach { case (o,s) => o := on === s.U }
      counter := Mux(counter === (period-1).U, 0.U, counter + 1.U)
      when (counter === 0.U) {
        on := Mux(on === (status_leds.size-1).U, 0.U, on + 1.U)
      }
    }

    other_leds.head := resetPin

    harnessSysPLL.plls.foreach(_._1.getReset.get := pllReset)

    def referenceClockFreqMHz = dutFreqMHz
    def referenceClock = dutClock.in.head._1.clock
    def referenceReset = dutClock.in.head._1.reset
    def success = { require(requirement = false, "Unused"); false.B }

    if (p(Genesys2ShellDDR)) { 
      ddrOverlay.get.mig.module.clock := harnessBinderClock
      ddrOverlay.get.mig.module.reset := harnessBinderReset
      ddrBlockDuringReset.get.module.clock := harnessBinderClock
      ddrBlockDuringReset.get.module.reset := harnessBinderReset.asBool || !ddrOverlay.get.mig.module.io.port.init_calib_complete
    }

    instantiateChipTops()
  }
}
