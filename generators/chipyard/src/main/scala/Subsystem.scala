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


  // Relying on [[TLBusWrapperConnection]].driveClockFromMaster for
  // bus-couplings that are not asynchronous strips the bus name from the sink
  // ClockGroup. This makes it impossible to determine which clocks are driven
  // by which bus based on the member names, which is problematic when there is
  // a rational crossing between two buses. Instead, provide all bus clocks
  // directly from the asyncClockGroupsNode in the subsystem to ensure bus
  // names are always preserved in the top-level clock names.
  //
  // For example, using a RationalCrossing between the Sbus and Cbus, and
  // driveClockFromMaster = Some(true) results in all cbus-attached device and
  // bus clocks to be given names of the form "subsystem_sbus_[0-9]*".
  // Conversly, if an async crossing is used, they instead receive names of the
  // form "subsystem_cbus_[0-9]*". The assignment below provides the latter names in all cases.
  Seq(PBUS, FBUS, MBUS, CBUS).foreach { loc =>
    tlBusWrapperLocationMap.lift(loc).foreach { _.clockGroupNode := asyncClockGroupsNode }
  }
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

