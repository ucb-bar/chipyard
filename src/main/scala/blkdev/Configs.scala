package blkdev

import config.{Parameters, Config}
import example.DefaultExampleConfig
import testchipip.{WithBlockDevice, WithNBlockDeviceTrackers}

class BlockDeviceConfig extends Config(
  new WithBlockDevice ++
  new DefaultExampleConfig)
