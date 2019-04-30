package beagle

import chisel3._
import chisel3.util._

import freechips.rocketchip.subsystem._
import freechips.rocketchip.system._
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.tilelink.{BankBinder}
import freechips.rocketchip.devices.tilelink._
import freechips.rocketchip.util.DontTouch
import freechips.rocketchip.diplomacy.{NoCrossing, FlipRendering, SynchronousCrossing}

import sifive.blocks.devices.gpio._
import sifive.blocks.devices.spi._
import sifive.blocks.devices.uart._
import sifive.blocks.devices.i2c._
import sifive.blocks.devices.jtag._

class BeagleRocketTop(implicit p: Parameters) extends RocketSubsystem
  with HasPeripheryBootROM
  with HasPeripheryGPIO
  with HasPeripherySPI
  with HasPeripheryI2C
  with HasPeripheryUART
  with HasPeripheryBeagle {

  /** START: COPIED FROM ROCKET-CHIP */
  override lazy val module = new BeagleRocketTopModule(this)

  // The sbus masters the cbus; here we convert TL-UH -> TL-UL
  sbus.crossToBus(cbus, NoCrossing)

  // The cbus masters the pbus; which might be clocked slower
  cbus.crossToBus(pbus, SynchronousCrossing())

  // The fbus masters the sbus; both are TL-UH or TL-C
  FlipRendering { implicit p =>
    sbus.crossFromBus(fbus, SynchronousCrossing())
  }

  // The sbus masters the mbus; here we convert TL-C -> TL-UH
  private val BankedL2Params(nBanks, coherenceManager) = p(BankedL2Key)
  private val (in, out, halt) = coherenceManager(this)
  if (nBanks != 0) {
    sbus.coupleTo("coherence_manager") { in :*= _ }
    mbus.coupleFrom("coherence_manager") { _ :=* BankBinder(mbus.blockBytes * (nBanks-1)) :*= out }
  }
  /** END: COPIED FROM ROCKET-CHIP */
}

class BeagleRocketTopModule[+L <: BeagleRocketTop](l: L) extends RocketSubsystemModuleImp(l)
  with HasRTCModuleImp
  with HasPeripheryBootROMModuleImp
  with HasPeripheryGPIOModuleImp
  with HasPeripherySPIModuleImp
  with HasPeripheryI2CModuleImp
  with HasPeripheryUARTModuleImp
  with HasPeripheryBeagleModuleImp
  with HasTilesBundle
  with freechips.rocketchip.util.DontTouch {

  // backup clocks coming from offchip
  val alt_clks    = IO(Input(Vec(2, Clock())))
  val alt_clk_sel = IO(Input(UInt(1.W)))

  val clk_out       = IO(Output(Clock()))
  val lbwif_clk_out = IO(Output(Clock()))

  // pipe out the lbwif clock out
  lbwif_clk_out := lbwif_clk

  // get the actual clock from the multiple alternate clocks
  require(alt_clk_sel.getWidth >= log2Ceil(alt_clks.length), "[sys-top] must be able to select all input clocks")
  val clockMux = testchipip.ClockMutexMux(alt_clks)
  clockMux.io.sel := alt_clk_sel
  clockMux.io.resetAsync := rst_async
  clk_out := clockMux.io.clockOut
}
