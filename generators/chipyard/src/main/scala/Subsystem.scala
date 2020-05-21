//******************************************************************************
// Copyright (c) 2019 - 2019, The Regents of the University of California (Regents).
// All Rights Reserved. See LICENSE and LICENSE.SiFive for license details.
//------------------------------------------------------------------------------

package chipyard

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

import boom.common.{BoomTile, BoomTilesKey, BoomCrossingKey, BoomTileParams}

import testchipip.{DromajoHelper}

trait HasChipyardTiles extends HasTiles
  with CanHavePeripheryPLIC
  with CanHavePeripheryCLINT
  with HasPeripheryDebug
{ this: BaseSubsystem =>

  val module: HasChipyardTilesModuleImp

  protected val rocketTileParams = p(RocketTilesKey)
  protected val boomTileParams = p(BoomTilesKey)
  protected val coreTileParams = CoreRegistrar.cores map (coreType => p(coreType.tilesKey))

  // crossing can either be per tile or global (aka only 1 crossing specified)
  private val rocketCrossings = perTileOrGlobalSetting(p(RocketCrossingKey), rocketTileParams.size)
  private val boomCrossings = perTileOrGlobalSetting(p(BoomCrossingKey), boomTileParams.size)
  private val coreCrossings = (CoreRegistrar.cores zip coreTileParams) map (_ match {
    case (coreType, tileParams) => perTileOrGlobalSetting(p(coreType.crossingKey), tileParams.size)
  })

  // TODO: XXX The "tiles" below scan for hartId but it is not in CoreParams. Should that be added in later 
  // revision, or I have to use reflection to get that parameter?
  val allTilesInfo = (rocketTileParams ++ boomTileParams ++ coreTileParams.flatten) zip (rocketCrossings ++ boomCrossings ++ coreCrossings.flatten)

  // Make a tile and wire its nodes into the system,
  // according to the specified type of clock crossing.
  // Note that we also inject new nodes into the tile itself,
  // also based on the crossing type.
  // This MUST be performed in order of hartid
  // There is something weird with registering tile-local interrupt controllers to the CLINT.
  // TODO: investigate why
  val tiles = allTilesInfo.sortWith(_._1.hartId < _._1.hartId).map {
    case (param, crossing) => {

      val tile = param match {
        case r: RocketTileParams => {
          LazyModule(new RocketTile(r, crossing, PriorityMuxHartIdFromSeq(rocketTileParams), logicalTreeNode))
        }
        case b: BoomTileParams => {
          LazyModule(new BoomTile(b, crossing, PriorityMuxHartIdFromSeq(boomTileParams), logicalTreeNode))
        }
        case _ => LazyModule(
          (CoreRegistrar.cores collect (core => core.instantiateTile(param, crossing, paramList, logicalTreeNode, p)).unlift()) (0)
        )
      }
      connectMasterPortsToSBus(tile, crossing)
      connectSlavePortsToCBus(tile, crossing)
      connectInterrupts(tile, debugOpt, clintOpt, plicOpt)

      tile
    }
  }


  def coreMonitorBundles = tiles.map {
    case r: RocketTile => r.module.core.rocketImpl.coreMonitorBundle
    case b: BoomTile => b.module.core.coreMonitorBundle
  }.toList
}

trait HasChipyardTilesModuleImp extends HasTilesModuleImp
  with HasPeripheryDebugModuleImp
{
  val outer: HasChipyardTiles
}

class Subsystem(implicit p: Parameters) extends BaseSubsystem
  with HasChipyardTiles
{
  override lazy val module = new SubsystemModuleImp(this)

  def getOMInterruptDevice(resourceBindingsMap: ResourceBindingsMap): Seq[OMInterrupt] = Nil
}

class SubsystemModuleImp[+L <: Subsystem](_outer: L) extends BaseSubsystemModuleImp(_outer)
  with HasResetVectorWire
  with HasChipyardTilesModuleImp
{
  tile_inputs.zip(outer.hartIdList).foreach { case(wire, i) =>
    wire.hartid := i.U
    wire.reset_vector := global_reset_vector
  }

  // create file with boom params
  ElaborationArtefacts.add("""core.config""", outer.tiles.map(x => x.module.toString).mkString("\n"))

  // Generate C header with relevant information for Dromajo
  // This is included in the `dromajo_params.h` header file
  DromajoHelper.addArtefacts
}
