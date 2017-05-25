package pwm

import config.Parameters
import diplomacy.LazyModule

class TestHarness(q: Parameters) extends example.TestHarness()(q) {
  override def buildTop(p: Parameters) =
    LazyModule(new ExampleTopWithPWM()(p))
}

object Generator extends example.ExampleGeneratorApp {
  generateFirrtl
}
