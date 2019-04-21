package example

import chisel3._
import chisel3.experimental._
import firrtl.transforms.{BlackBoxResourceAnno, BlackBoxSourceHelper}
import freechips.rocketchip.diplomacy.LazyModule
import freechips.rocketchip.config.{Field, Parameters}
import freechips.rocketchip.util.GeneratorApp

object Generator extends GeneratorApp {
  val longName = names.topModuleProject + "." + names.topModuleClass + "." + names.configs
  generateFirrtl
  generateAnno
  generateTestSuiteMakefrags
  generateArtefacts
}
