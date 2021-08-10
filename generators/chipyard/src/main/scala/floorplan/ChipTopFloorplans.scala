// See LICENSE for license details
package chipyard.floorplan

import chipyard.TestHarness
import chipyard.{ChipTopLazyRawModuleImp, BuildSystem, DigitalTop}
import freechips.rocketchip.config.{Parameters}
import barstools.floorplan.chisel.{FloorplanAspect, Floorplan, FloorplanFunction}

object ChipTopFloorplans {

  def default: FloorplanFunction = {
    case top: ChipTopLazyRawModuleImp =>
      val context = Floorplan(top, 500.0, 500.0)
      val topGrid = context.setTopGroup(context.createElasticArray(2))
      val tiles = top.outer.lazySystem match {
        case t: DigitalTop => t.tiles.map(x => context.addHier(x.module))
        case _ => throw new Exception("Unsupported BuildSystem type")
      }
      topGrid.placeAt(1, context.createElasticArray(tiles))
      topGrid.placeAt(0, context.createSpacer(Some("spacer")))
      context.commit()
  }

}

case object ChipTopFloorplanAspect extends FloorplanAspect[chipyard.TestHarness](
  ChipTopFloorplans.default orElse
  RocketFloorplans.default
)
