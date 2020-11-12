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
import freechips.rocketchip.devices.debug.{HasPeripheryDebug, HasPeripheryDebugModuleImp, ExportDebug, DebugModuleKey}
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


import testchipip.{DromajoHelper, CanHavePeripheryTLSerial, SerialTLKey}

trait CanHaveHTIF { this: BaseSubsystem =>
  // Advertise HTIF if system can communicate with fesvr
  if (this match {
    case _: CanHavePeripheryTLSerial if p(SerialTLKey).nonEmpty => true
    case _: HasPeripheryDebug if (!p(DebugModuleKey).isEmpty && p(ExportDebug).dmi) => true
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

class ChipyardSubsystem(implicit p: Parameters) extends BaseSubsystem
  with HasTiles
  with CanHaveHTIF
{
  def coreMonitorBundles = tiles.map {
    case r: RocketTile => r.module.core.rocketImpl.coreMonitorBundle
    case b: BoomTile => b.module.core.coreMonitorBundle
  }.toList

  override lazy val module = new ChipyardSubsystemModuleImp(this)
}

class ChipyardSubsystemModuleImp[+L <: ChipyardSubsystem](_outer: L) extends BaseSubsystemModuleImp(_outer)
  with HasTilesModuleImp
{
  // create file with core params
  ElaborationArtefacts.add("""core.config""", outer.tiles.map(x => x.module.toString).mkString("\n"))
  // Generate C header with relevant information for Dromajo
  // This is included in the `dromajo_params.h` header file
  DromajoHelper.addArtefacts(InSubsystem)
}

