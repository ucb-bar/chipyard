// See LICENSE for license details
package chipyard.floorplan

import chipyard.TestHarness
import chipyard.{ChipTopLazyRawModuleImp, BuildSystem, DigitalTop}
import freechips.rocketchip.config.{Parameters}
import barstools.floorplan.chisel.{FloorplanAspect, Floorplan, FloorplanFunction}

object ChipTopFloorplans {

  def default: FloorplanFunction = {
    case top: ChipTopLazyRawModuleImp =>
      val context = Floorplan(top)
      val tiles = top.outer.lazySystem match {
        case t: DigitalTop => t.tiles.map(x => context.addHier(x.module))
        case _ => throw new Exception("Unsupported BuildSystem type")
      }
      val tileGrid = context.createElasticArray(tiles)
      val topGrid = context.createElasticArray(2)
      topGrid.placeElementAt(tileGrid, 1)
      topGrid.placeElementAt(context.createSpacer(Some("Dummy")), 0)
      context.elements
  }

}

case object ChipTopFloorplanAspect extends FloorplanAspect[chipyard.TestHarness](
  ChipTopFloorplans.default orElse
  RocketFloorplans.default
)
