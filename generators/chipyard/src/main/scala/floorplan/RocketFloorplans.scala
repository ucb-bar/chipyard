// See LICENSE for license details
package chipyard.floorplan

import freechips.rocketchip.tile.{RocketTileModuleImp}
import barstools.floorplan.chisel.{FloorplanAspect, Floorplan}

case object RocketFloorplan extends FloorplanAspect {

  def floorplans = {
    case x: RocketTileModuleImp =>
      Seq(Floorplan.createRect(x))
  }
}
