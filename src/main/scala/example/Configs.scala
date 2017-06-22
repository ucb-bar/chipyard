package example

import config.{Parameters, Config}
import testchipip.WithSerialAdapter
import coreplex.WithRoccExample

class DefaultExampleConfig extends Config(
  new WithSerialAdapter ++ new rocketchip.DefaultConfig)

class RoccExampleConfig extends Config(
  new WithRoccExample ++ new DefaultExampleConfig)
