//******************************************************************************
// Copyright (c) 2019 - 2019, The Regents of the University of California (Regents).
// All Rights Reserved. See LICENSE and LICENSE.SiFive for license details.
//------------------------------------------------------------------------------

package utilities

import chisel3._
import chisel3.internal.sourceinfo.{SourceInfo}

import freechips.rocketchip.config.{Field, Parameters}
import freechips.rocketchip.devices.tilelink._
import freechips.rocketchip.devices.debug.{HasPeripheryDebug, HasPeripheryDebugModuleImp}
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.diplomaticobjectmodel.model.{OMInterrupt}
import freechips.rocketchip.diplomaticobjectmodel.logicaltree.{RocketTileLogicalTreeNode, LogicalModuleTree}
import freechips.rocketchip.tile._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.interrupts._
import freechips.rocketchip.util._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.amba.axi4._

import boom.common.{BoomTile, BoomTilesKey, BoomCrossingKey}


trait HasBoomAndRocketTiles extends HasTiles
  with CanHavePeripheryPLIC
  with CanHavePeripheryCLINT
  with HasPeripheryDebug
{ this: BaseSubsystem =>

  val module: HasBoomAndRocketTilesModuleImp

  protected val rocketTileParams = p(RocketTilesKey)
  protected val boomTileParams = p(BoomTilesKey)
  // crossing can either be per tile or global (aka only 1 crossing specified)
  private val rocketCrossings = perTileOrGlobalSetting(p(RocketCrossingKey), rocketTileParams.size)
  private val boomCrossings = perTileOrGlobalSetting(p(BoomCrossingKey), boomTileParams.size)

  // Make a tile and wire its nodes into the system,
  // according to the specified type of clock crossing.
  // Note that we also inject new nodes into the tile itself,
  // also based on the crossing type.
  val rocketTiles = rocketTileParams.zip(rocketCrossings).map { case (tp, crossing) =>
    val rocket = LazyModule(new RocketTile(tp, crossing, PriorityMuxHartIdFromSeq(rocketTileParams), logicalTreeNode))

    connectMasterPortsToSBus(rocket, crossing)
    connectSlavePortsToCBus(rocket, crossing)

    def treeNode: RocketTileLogicalTreeNode = new RocketTileLogicalTreeNode(rocket.rocketLogicalTree.getOMInterruptTargets)
    LogicalModuleTree.add(logicalTreeNode, rocket.rocketLogicalTree)

    rocket
  }

  val boomTiles = boomTileParams.zip(boomCrossings).map { case (tp, crossing) =>
    val boom = LazyModule(new BoomTile(tp, crossing, PriorityMuxHartIdFromSeq(boomTileParams), logicalTreeNode))

    connectMasterPortsToSBus(boom, crossing)
    connectSlavePortsToCBus(boom, crossing)

    def treeNode: RocketTileLogicalTreeNode = new RocketTileLogicalTreeNode(boom.rocketLogicalTree.getOMInterruptTargets)
    LogicalModuleTree.add(logicalTreeNode, boom.rocketLogicalTree)

    boom
  }

  // combine tiles and connect interrupts based on the order of harts
  val boomAndRocketTiles = (rocketTiles ++ boomTiles).sortWith(_.tileParams.hartId < _.tileParams.hartId).map {
    tile => {
      connectInterrupts(tile, Some(debug), clintOpt, plicOpt)

      tile
    }
  }

  def coreMonitorBundles = (rocketTiles map { t => t.module.core.rocketImpl.coreMonitorBundle}).toList ++
                             (boomTiles map { t => t.module.core.coreMonitorBundle}).toList
}

trait HasBoomAndRocketTilesModuleImp extends HasTilesModuleImp
  with HasPeripheryDebugModuleImp
{
  val outer: HasBoomAndRocketTiles
}

class Subsystem(implicit p: Parameters) extends BaseSubsystem
  with HasBoomAndRocketTiles
{
  val tiles = boomAndRocketTiles
  override lazy val module = new SubsystemModuleImp(this)

  def getOMInterruptDevice(resourceBindingsMap: ResourceBindingsMap): Seq[OMInterrupt] = Nil
}

class SubsystemModuleImp[+L <: Subsystem](_outer: L) extends BaseSubsystemModuleImp(_outer)
  with HasResetVectorWire
  with HasBoomAndRocketTilesModuleImp
{
  tile_inputs.zip(outer.hartIdList).foreach { case(wire, i) =>
    wire.hartid := i.U
    wire.reset_vector := global_reset_vector
  }

  // create file with boom params
  ElaborationArtefacts.add("""core.config""", outer.tiles.map(x => x.module.toString).mkString("\n"))
}
