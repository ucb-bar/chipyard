package beagle

import chisel3._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.system._
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.devices.tilelink._
import freechips.rocketchip.util.DontTouch
import testchipip._
import example.{HasPeripheryPWMTL, HasPeripheryPWMAXI4, HasPeripheryPWMTLModuleImp, HasPeripheryPWMAXI4ModuleImp}

//---------------------------------------------------------------------------------------------------------

class BeagleRocketTop(implicit p: Parameters) extends RocketSystem
  with HasPeripheryBootROM
  with HasPeripheryGPIO
  with HasPeripherySPI
  with HasPeripheryI2C
  with HasPeripheryUART
  with HasPeripheryEagle {

  require(clusterPLLParams.size == p(NClusters))

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
}

class BeagleRocketTopModule[+L <: BeagleRocketTop](l: L) extends RocketSubsystemModuleImp(l)
  with HasRTCModuleImp
  with HasPeripheryBootROMModuleImp
  with HasPeripheryGPIOModuleImp
  with HasPeripherySPIModuleImp
  with HasPeripheryI2CModuleImp
  with HasPeripheryUARTModuleImp
  with HasPeripheryEagleModuleImp
  with HasTilesBundle
  with freechips.rocketchip.util.DontTouch {

  val cclk = IO(Input(Vec(3, Clock())))
  val clk_sel = IO(Input(UInt(2.W)))
  val unclusterClockOut = IO(Output(Clock()))
  val lbwifClockOut = IO(Output(Clock()))

  val clusterClocks = (clusterClockSels zip outer.clusterPLLs).zipWithIndex.map { case ((sel, pll), i) =>
    val mux = testchipip.ClockMutexMux(Seq(pll.module.io.clockOut,
      pll.module.io.clockOutDiv, pllRefClock) ++ cclk).suggestName(s"cluster_clock_mux$i")
    require(mux.n == p(PeripheryEagleKey).numClusterClocks)
    mux.io.sel := sel
    mux.io.resetAsync := resetAsync
    mux.io.clockOut
  }

  //We put the refClock at zero and three so that there are no invalid configs of unclusterClockSel although this isn't necessary
  val uncluster_clock_mux = testchipip.ClockMutexMux(Seq(pllRefClock,
    outer.unclusterPLL.module.io.clockOut, outer.unclusterPLL.module.io.clockOutDiv, pllRefClock))
  uncluster_clock_mux.io.sel := unclusterClockSel
  uncluster_clock_mux.io.resetAsync := resetAsync
  unclusterClockOut := uncluster_clock_mux.io.clockOut
  lbwifClockOut := lbwifClock
}
