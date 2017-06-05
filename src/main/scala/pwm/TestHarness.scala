package pwm

import config.Parameters
import diplomacy.LazyModule
import testchipip.GeneratorApp

class TestHarness(q: Parameters) extends example.TestHarness()(q) {
  override def buildTop(p: Parameters) =
    LazyModule(new ExampleTopWithPWM()(p))
}

object Generator extends GeneratorApp {
  generateFirrtl
}
