// See LICENSE for license details
package chipyard.floorplan

import freechips.rocketchip.tile.{RocketTileModuleImp}
import freechips.rocketchip.rocket.{HasHellaCache, DCache}
import hwacha.{Hwacha, HwachaImp}
import barstools.floorplan.chisel.{FloorplanAspect, Floorplan, FloorplanFunction, Direction}
import barstools.floorplan.{GreaterThanOrEqualTo}

object RocketFloorplans {

  def default: FloorplanFunction = {
    case tile: RocketTileModuleImp =>
      val context = Floorplan(tile)
      val topGroup = context.setTopGroup(context.createElasticArray(3))
      val cacheDataArray = topGroup.placeAt(0, context.createElasticArray(3, Direction.Horizontal))

      val icacheData = cacheDataArray.placeAt(0, context.createMemArray(Some("l1_icache_data"), Some(0.9)))
      tile.outer.frontend.icache.module.data_arrays.map(x => icacheData.addMem(x._1))

      cacheDataArray.placeAt(1, context.createSpacer(
        name = Some("cache_spacer"),
        width = GreaterThanOrEqualTo(750)))

      tile.outer match {
        case x: HasHellaCache =>
          val dcacheData = cacheDataArray.placeAt(2, context.createMemArray(Some("l1_dcache_data"), Some(0.9)))
          x.dcache match {
            case cache: DCache =>
              cache.module.dcacheImpl.data.data_arrays.map(x => dcacheData.addMem(x._1))
            case _ =>
              ???
          }
        case _ =>
          // Do nothing
      }

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
  def vertical: FloorplanFunction = {
    case tile: RocketTileModuleImp =>
      val context = Floorplan(tile)
      val topGroup = context.setTopGroup(context.createElasticArray(3))
      val cacheDataArray = topGroup.placeAt(0, context.createElasticArray(3, Direction.Vertical))

      val icacheData = cacheDataArray.placeAt(0, context.createMemArray(Some("l1_icache_data"), Some(1)))
      tile.outer.frontend.icache.module.data_arrays.map(x => icacheData.addMem(x._1))

      cacheDataArray.placeAt(1, context.createSpacer(
        name = Some("cache_spacer"),
        height = GreaterThanOrEqualTo(760)))

      tile.outer match {
        case x: HasHellaCache =>
          val dcacheData = cacheDataArray.placeAt(2, context.createMemArray(Some("l1_dcache_data"), Some(1)))
          x.dcache match {
            case cache: DCache =>
              cache.module.dcacheImpl.data.data_arrays.map(x => dcacheData.addMem(x._1))
            case _ =>
              ???
          }
        case _ =>
          // Do nothing
      }

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

case object RocketFloorplanAspect extends FloorplanAspect[chipyard.TestHarness](RocketFloorplans.vertical)
