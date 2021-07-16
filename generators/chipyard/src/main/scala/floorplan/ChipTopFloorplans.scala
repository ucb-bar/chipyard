// See LICENSE for license details
package chipyard.floorplan

import chipyard.TestHarness
import chipyard.{ChipTopLazyRawModuleImp, BuildSystem, DigitalTop}
import freechips.rocketchip.config.{Parameters}
import barstools.floorplan.chisel.{FloorplanAspect, Floorplan, FloorplanFunction}

object ChipTopFloorplans {

  def default: FloorplanFunction = {
    case top: TestHarness =>
      val context = Floorplan(top)
      context.createDummy(Some("foo"))
      context.elements
    case top: ChipTopLazyRawModuleImp =>
      val context = Floorplan(top)
      top.outer.lazySystem match {
        case t: DigitalTop =>
          val tiles = t.tiles.map(x => context.addHier(x.module))
      }
      context.elements
  }

}

case object ChipTopFloorplanAspect extends FloorplanAspect[chipyard.TestHarness](
  ChipTopFloorplans.default orElse
  RocketFloorplans.default
)
