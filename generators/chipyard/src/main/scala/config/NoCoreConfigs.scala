package chipyard

import freechips.rocketchip.config.{Config}

// A empty config with no cores. Useful for testing
class NoCoresConfig extends Config(
  new chipyard.config.WithNoDebug ++
  new chipyard.config.WithNoPLIC ++
  new chipyard.config.AbstractConfig)
