package tlserdes

import freechips.rocketchip.config.{Parameters, Config}
import testchipip.WithSerialAdapter

class WithTLSerdes extends Config((site, here, up) => {
  case TLSerdesWidth => 16
})

class DefaultSerdesConfig extends Config(
  new WithTLSerdes ++
  new WithSerialAdapter ++
  new freechips.rocketchip.chip.DefaultConfig)

class WithTwoMemChannels extends example.WithTwoMemChannels
class WithFourMemChannels extends example.WithFourMemChannels
