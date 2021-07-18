// See LICENSE for license details
package chipyard.floorplan

import freechips.rocketchip.tile.{RocketTileModuleImp}
import barstools.floorplan.chisel.{FloorplanAspect, Floorplan, FloorplanFunction}

object RocketFloorplans {

  def default: FloorplanFunction = {
    case tile: RocketTileModuleImp =>
      val context = Floorplan(tile)
      context.createSpacer(Some("Spacer"))
      val memArray = context.createMemArray(Some("l1_icache_data"))
      tile.outer.frontend.icache.module.data_arrays.map(x => memArray.addMem(x._1))
      context.elements
  }
}

case object RocketFloorplanAspect extends FloorplanAspect[chipyard.TestHarness](RocketFloorplans.default)
