package example

import aoplib.floorplan.{FloorplanAspect}
import chisel3.{Data, Vec}
import chisel3.Bundle
import chisel3.aop.Select
import firrtl.options.{RegisteredLibrary, ShellOption}
import firrtl.{AnnotationSeq}
import barstools.floorplan.chisel._
import barstools.floorplan.Rational

object ExampleFloorplans {

  def simple(th: TestHarness): AnnotationSeq = {
    val top = th.dut
    val tile = top.outer.tiles.head.asInstanceOf[freechips.rocketchip.tile.RocketTile]
    val tileMacro = Floorplan.createRect(tile.module)

    val cacheGrid = Floorplan.createGrid(tile.module, "myGrid", 3, 1, true)
    cacheGrid.set(0, 0, Floorplan.createRect(tile.dcache.module), Rational(2))
    cacheGrid.setModule(2, 0, tile.frontend.icache.module, Rational(2))

    Floorplan.commitAndGetAnnotations()
  }
}

case class RocketFloorplan() extends RegisteredLibrary {
  val name = "Rocket-Floorplan"
  val options = Seq(
    new ShellOption[String](
      longOption = "floorplan",
      toAnnotationSeq = {
        case "simple" => Seq(FloorplanAspect(ExampleFloorplans.simple))
      },
      helpText = "The Rocket example floorplan to use. Valid options are: 'simple'",
      helpValueName = Some("<name>")),
    new ShellOption[String](
      longOption = "floorplanFile",
      toAnnotationSeq = {
        case x: String => Seq(barstools.floorplan.firrtl.FloorplanIRFileAnnotation(x))
      },
      helpText = "The Rocket example floorplan filename.",
      helpValueName = Some("<file>"))
    )
}
