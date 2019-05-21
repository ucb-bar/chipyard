package example

import chisel3._
import chisel3.internal.sourceinfo.{SourceInfo}

import freechips.rocketchip.config.{Field, Parameters}
import freechips.rocketchip.devices.tilelink._
import freechips.rocketchip.devices.debug.{HasPeripheryDebug, HasPeripheryDebugModuleImp}
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.diplomaticobjectmodel.model.{OMComponent, OMInterruptTarget}
import freechips.rocketchip.tile._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.interrupts._
import freechips.rocketchip.util._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.amba.axi4._

import boom.system.{BoomTilesKey}

/**
 * Example top with periphery devices and ports, and a BOOM subsystem
 */
class ExampleBoomAndRocketSystem(implicit p: Parameters) extends BoomAndRocketSubsystem
  with HasAsyncExtInterrupts
  with boom.system.CanHaveMisalignedMasterAXI4MemPort
  with CanHaveMasterAXI4MMIOPort
  with CanHaveSlaveAXI4Port
  with HasPeripheryBootROM
{
  override lazy val module = new ExampleBoomAndRocketSystemModule(this)

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

/**
 * Example top module with periphery devices and ports, and a BOOM subsystem
 */
class ExampleBoomAndRocketSystemModule[+L <: ExampleBoomAndRocketSystem](_outer: L) extends BoomAndRocketSubsystemModuleImp(_outer)
  with HasRTCModuleImp
  with HasExtInterruptsModuleImp
  with boom.system.CanHaveMisalignedMasterAXI4MemPortModuleImp
  with CanHaveMasterAXI4MMIOPortModuleImp
  with CanHaveSlaveAXI4PortModuleImp
  with HasPeripheryBootROMModuleImp
  with DontTouch
