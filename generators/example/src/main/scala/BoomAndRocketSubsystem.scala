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

trait HasBoomAndRocketTiles extends HasTiles
  with CanHavePeripheryPLIC
  with CanHavePeripheryCLINT
  with HasPeripheryDebug
{ this: BaseSubsystem =>

  val module: HasBoomAndRocketTilesModuleImp

  protected val rocketTileParams = p(RocketTilesKey)
  protected val boomTileParams = p(BoomTilesKey)
  private val rocketCrossings = perTileOrGlobalSetting(p(RocketCrossingKey), rocketTileParams.size)
  private val boomCrossings = perTileOrGlobalSetting(p(RocketCrossingKey), boomTileParams.size)

  // Make a tile and wire its nodes into the system,
  // according to the specified type of clock crossing.
  // Note that we also inject new nodes into the tile itself,
  // also based on the crossing type.
  val rocketTiles = rocketTileParams.zip(rocketCrossings).map { case (tp, crossing) =>
    val rocket = LazyModule(new RocketTile(tp, crossing.crossingType)(augmentedTileParameters(tp))).suggestName(tp.name)

    connectMasterPortsToSBus(rocket, crossing)
    connectSlavePortsToCBus(rocket, crossing)
    connectInterrupts(rocket, Some(debug), clintOpt, plicOpt)

    rocket
  }

  println(s"DEBUG: Amount of rocket tiles: ${rocketTiles.length}")

  val boomTiles = boomTileParams.zip(boomCrossings).map { case (tp, crossing) =>
    val boomCore = LazyModule(
      new boom.common.BoomTile(tp, crossing.crossingType)(augmentedTileParameters(tp))).suggestName(tp.name)

    connectMasterPortsToSBus(boomCore, crossing)
    connectSlavePortsToCBus(boomCore, crossing)
    connectInterrupts(boomCore, Some(debug), clintOpt, plicOpt)

    boomCore
  }

  println(s"DEBUG: Amount of boom tiles: ${boomTiles.length}")

  val boomAndRocketTiles = rocketTiles ++ boomTiles
  println(s"DEBUG: Amount of both tiles: ${boomAndRocketTiles.length}")

  def coreMonitorBundles = (rocketTiles map { t => t.module.core.rocketImpl.coreMonitorBundle}).toList ++
                             (boomTiles map { t => t.module.core.coreMonitorBundle}).toList

  def getOMRocketInterruptTargets(): Seq[OMInterruptTarget] =
    boomAndRocketTiles.flatMap(c => c.cpuDevice.getInterruptTargets())

  def getOMRocketCores(resourceBindingsMap: ResourceBindingsMap): Seq[OMComponent] =
    boomAndRocketTiles.flatMap(c => c.cpuDevice.getOMComponents(resourceBindingsMap))
}

trait HasBoomAndRocketTilesModuleImp extends HasTilesModuleImp
    with HasPeripheryDebugModuleImp {
  val outer: HasBoomAndRocketTiles
}

class BoomAndRocketSubsystem(implicit p: Parameters) extends BaseSubsystem
    with HasBoomAndRocketTiles {
  val tiles = boomAndRocketTiles
  override lazy val module = new BoomAndRocketSubsystemModuleImp(this)
}

class BoomAndRocketSubsystemModuleImp[+L <: BoomAndRocketSubsystem](_outer: L) extends BaseSubsystemModuleImp(_outer)
    with HasBoomAndRocketTilesModuleImp {
  tile_inputs.zip(outer.hartIdList).foreach { case(wire, i) =>
    wire.clock := clock
    wire.reset := reset
    wire.hartid := i.U
    wire.reset_vector := global_reset_vector
  }
}
