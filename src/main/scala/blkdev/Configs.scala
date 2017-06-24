package blkdev

import config.{Parameters, Config}
import example.DefaultExampleConfig
import testchipip.{WithBlockDevice, WithNBlockDeviceTrackers}

class WithSimBlockDevice extends Config((site, here, up) => {
  case UseSimBlockDevice => true
})

class WithBlockDeviceModel extends Config((site, here, up) => {
  case UseSimBlockDevice => false
})

class BlockDeviceConfig extends Config(
  new WithSimBlockDevice ++
  new WithBlockDevice ++
  new DefaultExampleConfig)

class WithTwoTrackers extends WithNBlockDeviceTrackers(2)
class WithFourTrackers extends WithNBlockDeviceTrackers(4)
