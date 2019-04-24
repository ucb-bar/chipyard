package beagle

import chisel3._

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

  // TODO: How do deal with the I/O out of the Rocket System?
  val cclk = IO(Input(Vec(3, Clock())))
  val clk_sel = IO(Input(UInt(2.W)))
  val unclusterClockOut = IO(Output(Clock()))
  val lbwifClockOut = IO(Output(Clock()))
}
