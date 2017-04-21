package pwm

import util.GeneratorApp
import config.Parameters
import diplomacy.LazyModule

class TestHarness(q: Parameters) extends example.TestHarness()(q) {
  override def buildTop(p: Parameters) =
    LazyModule(new ExampleTopWithPWM()(p))
}

object Generator extends GeneratorApp {
  val longName = names.topModuleProject + "." +
                 names.topModuleClass + "." +
                 names.configs
  generateFirrtl
}
