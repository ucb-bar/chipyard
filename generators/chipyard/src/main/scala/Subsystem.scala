//******************************************************************************
// Copyright (c) 2019 - 2019, The Regents of the University of California (Regents).
// All Rights Reserved. See LICENSE and LICENSE.SiFive for license details.
//------------------------------------------------------------------------------

package chipyard

import chisel3._
import chisel3.internal.sourceinfo.{SourceInfo}

import freechips.rocketchip.prci._
import freechips.rocketchip.config.{Field, Parameters}
import freechips.rocketchip.devices.tilelink._
import freechips.rocketchip.devices.debug.{HasPeripheryDebug, HasPeripheryDebugModuleImp, ExportDebug}
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.diplomaticobjectmodel.model.{OMInterrupt}
import freechips.rocketchip.diplomaticobjectmodel.logicaltree.{RocketTileLogicalTreeNode, LogicalModuleTree}
import freechips.rocketchip.tile._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.interrupts._
import freechips.rocketchip.util._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.amba.axi4._

import boom.common.{BoomTile}


import testchipip.{DromajoHelper, CanHavePeripherySerial, SerialKey}


trait CanHaveHTIF { this: BaseSubsystem =>
  // Advertise HTIF if system can communicate with fesvr
  if (this match {
    case _: CanHavePeripherySerial if p(SerialKey) => true
    case _: HasPeripheryDebug if p(ExportDebug).protocols.nonEmpty => true
    case _ => false
  }) {
    ResourceBinding {
      val htif = new Device {
        def describe(resources: ResourceBindings): Description = {
          val compat = resources("compat").map(_.value)
          Description("htif", Map(
            "compatible" -> compat))
        }
      }
      Resource(htif, "compat").bind(ResourceString("ucb,htif0"))
    }
  }
}


// Controls whether tiles are driven by implicit subsystem clock, or by
// diplomatic clock graph
case object UseDiplomaticTileClocks extends Field[Boolean](false)

class ChipyardSubsystem(implicit p: Parameters) extends BaseSubsystem
  with HasTiles
  with CanHaveHTIF
{
  def coreMonitorBundles = tiles.map {
    case r: RocketTile => r.module.core.rocketImpl.coreMonitorBundle
    case b: BoomTile => b.module.core.coreMonitorBundle
  }.toList

  // TODO: In the future, RC tiles may extend ClockDomain. When that happens,
  // we won't need to manually create this clock node and connect it to the
  // tiles' implicit clocks

  val tilesClockSinkNode = if (p(UseDiplomaticTileClocks)) {
    val node = ClockSinkNode(List(ClockSinkParameters()))
    node := ClockGroup()(p, ValName("chipyard_tiles")) := asyncClockGroupsNode
    Some(node)
  } else {
    None
  }

  override lazy val module = new ChipyardSubsystemModuleImp(this)
}


class ChipyardSubsystemModuleImp[+L <: ChipyardSubsystem](_outer: L) extends BaseSubsystemModuleImp(_outer)
  with HasResetVectorWire
  with HasTilesModuleImp
{
  for (i <- 0 until outer.tiles.size) {
    val wire = tile_inputs(i)
    wire.hartid := outer.hartIdList(i).U
    wire.reset_vector := global_reset_vector

    outer.tilesClockSinkNode.map( n => {
      outer.tiles(i).module.clock := n.in.head._1.clock
      outer.tiles(i).module.reset := n.in.head._1.reset
    })
  }

  // create file with core params
  ElaborationArtefacts.add("""core.config""", outer.tiles.map(x => x.module.toString).mkString("\n"))
  // Generate C header with relevant information for Dromajo
  // This is included in the `dromajo_params.h` header file
  DromajoHelper.addArtefacts()
}

