// See LICENSE for license details
package chipyard.floorplan

import freechips.rocketchip.tile.{RocketTileModuleImp}
import hwacha.{Hwacha, HwachaImp}
import barstools.floorplan.chisel.{FloorplanAspect, Floorplan, FloorplanFunction}

object RocketFloorplans {

  def default: FloorplanFunction = {
    case tile: RocketTileModuleImp =>
      val context = Floorplan(tile)
      val topGroup = context.setTopGroup(context.createElasticArray(3))
      val memArray = topGroup.placeAt(0, context.createMemArray(Some("l1_icache_data")))
      topGroup.placeAt(1, context.createSpacer(Some("spacer")))
      tile.outer.frontend.icache.module.data_arrays.map(x => memArray.addMem(x._1))
      // Add more SRAM arrays here

      // Add optional accelerator placements
      val hwacha = tile.outer.roccs.collectFirst { case h: Hwacha =>
        topGroup.placeAt(2, context.addHier(h.module))
      }

      context.commit()
    case hwacha: HwachaImp =>
      val context = Floorplan(hwacha)
      val topGroup = context.setTopGroup(context.createElasticArray(2))
      // Hwacha floorplan goes here
      context.commit()
  }
}

case object RocketFloorplanAspect extends FloorplanAspect[chipyard.TestHarness](RocketFloorplans.default)
