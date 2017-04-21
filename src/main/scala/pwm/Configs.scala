package pwm

import config.{Parameters, Config}
import testchipip.WithSerialAdapter
import uncore.tilelink.ClientUncachedTileLinkIO
import chisel3._

class PWMConfig extends Config(new example.DefaultExampleConfig)
