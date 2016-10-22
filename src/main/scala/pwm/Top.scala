package pwm

import chisel3._
import example._
import cde.Parameters

class ExampleTopWithPWM(q: Parameters) extends ExampleTop(q)
    with PeripheryPWM {
  override lazy val module = Module(
    new ExampleTopWithPWMModule(p, this, new ExampleTopWithPWMBundle(p)))
}

class ExampleTopWithPWMBundle(p: Parameters) extends ExampleTopBundle(p)
  with PeripheryPWMBundle

class ExampleTopWithPWMModule(p: Parameters, l: ExampleTopWithPWM, b: => ExampleTopWithPWMBundle)
  extends ExampleTopModule(p, l, b) with PeripheryPWMModule
