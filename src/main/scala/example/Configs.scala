package example

import config.{Parameters, Config}
import testchipip.WithSerialAdapter

class DefaultExampleConfig extends Config(
  new WithSerialAdapter ++ new rocketchip.DefaultConfig)
