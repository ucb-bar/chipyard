package example

import cde.{Parameters, Config, CDEMatchError}
import testchipip.WithSerialAdapter

class DefaultExampleConfig extends Config(
  new WithSerialAdapter ++ new rocketchip.BaseConfig)
