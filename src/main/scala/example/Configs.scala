package example

import config.{Parameters, Config}
import testchipip.WithSerialAdapter
import coreplex.WithRoccExample
import rocketchip.WithoutTLMonitors

class DefaultExampleConfig extends Config(
  new WithoutTLMonitors ++
  new WithSerialAdapter ++
  new rocketchip.DefaultConfig)

class RoccExampleConfig extends Config(
  new WithRoccExample ++ new DefaultExampleConfig)
