package chipyard

import freechips.rocketchip.config.{Config}

// --------------
// Chip Configs
// --------------

class RocketChipConfig extends Config(
  new chipyard.config.WithChipTop ++
  new chipyard.RocketConfig)
