package example

import cde.{Parameters, Config, CDEMatchError}
import testchipip.WithSerialAdapter
import chisel3._
import diplomacy.LazyModule

class WithExampleTop extends Config(
  (pname, site, here) => pname match {
    case BuildExampleTop => (p: Parameters) => LazyModule(new ExampleTop(p))
    case _ => throw new CDEMatchError
  })

class WithPWM extends Config(
  (pname, site, here) => pname match {
    case BuildExampleTop => (p: Parameters) => LazyModule(new ExampleTopWithPWM(p))
    case _ => throw new CDEMatchError
  })

class SerialAdapterConfig extends Config(
  new WithSerialAdapter ++ new rocketchip.BaseConfig)

class DefaultExampleConfig extends Config(
  new WithExampleTop ++ new SerialAdapterConfig)

class PWMExampleConfig extends Config(
  new WithPWM ++ new SerialAdapterConfig)
