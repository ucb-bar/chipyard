// See LICENSE for license details
package chipyard.floorplan

import freechips.rocketchip.tile.{RocketTileModuleImp}
import barstools.floorplan.chisel.{FloorplanAspect, Floorplan, FloorplanFunction}

object RocketFloorplans {

  def default: FloorplanFunction = {
    case tile: RocketTileModuleImp =>
      val context = Floorplan(tile)
      context.createDummy(Some("Dummy"))
      tile.outer.frontend.icache.module.data_arrays.map(x => context.addMem(x._1))
      context.elements
  }
}

case object RocketFloorplanAspect extends FloorplanAspect[chipyard.TestHarness](RocketFloorplans.default)
