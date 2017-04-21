package pwm

import chisel3._
import example._
import config.Parameters

class ExampleTopWithPWM(implicit p: Parameters) extends ExampleTop()(p)
    with PeripheryPWM {
  override lazy val module = new ExampleTopWithPWMModule(this, () => new ExampleTopWithPWMBundle(this))
}

class ExampleTopWithPWMBundle[+L <: ExampleTopWithPWM](l: L)
  extends ExampleTopBundle(l)
  with PeripheryPWMBundle

class ExampleTopWithPWMModule[+L <: ExampleTopWithPWM, +B <: ExampleTopWithPWMBundle[L]](l: L, b: () => B)
    extends ExampleTopModule(l, b) with PeripheryPWMModule
