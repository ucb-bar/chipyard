package example

import aoplib.floorplan.FloorplanAspect
import chisel3.{Data, Vec}
import chisel3.Bundle
import floorplan._
import FloatingPointDimensionImplicits._
import chisel3.aop.Select
import firrtl.options.{RegisteredLibrary, ShellOption}

object Floorplans {

  def layoutTop(th: TestHarness): LayoutBase = {
    val top = th.dut
    val topLayout = VBox("top", Seq(
      HardMacro(top.outer.tiles.head.module, "tile", 500, 200),
      VerticalExpander(),
      HardMacro(top.outer.sbus.module, "uncore", 500, 300)
    ))
    println(s"TOPLAYOUT:\n${topLayout}")
    topLayout.replaceWidthAndHeight(500,500)
  }


}

case class RocketFloorplan() extends RegisteredLibrary {
  val name = "Rocket-Floorplan"
  val options = Seq(new ShellOption[String](
    longOption = "floorplan",
    toAnnotationSeq = {
      case "simple" => Seq(FloorplanAspect("Simple_Rocket","test_run_dir/html/myfloorplan",{ t: TestHarness => Floorplans.layoutTop(t) }))
    },
    helpText = "The name of a mini floorplan must be <dci|icd> indicating the relative positions of the icache, core, and dcache.",
    helpValueName = Some("<dci|icd>")))
}
