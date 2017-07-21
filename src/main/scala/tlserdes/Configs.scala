package tlserdes

import freechips.rocketchip.config.{Parameters, Config}

class WithTLSerdes extends Config((site, here, up) => {
  case TLSerdesWidth => 16
})

class DefaultSerdesConfig extends Config(
  new WithTLSerdes ++
  new freechips.rocketchip.chip.DefaultConfig)

class WithTwoMemChannels extends example.WithTwoMemChannels
class WithFourMemChannels extends example.WithFourMemChannels
