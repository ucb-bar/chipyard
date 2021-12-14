package chipyard

import freechips.rocketchip.config.{Config}

class SaturnConfig extends Config(
  new saturn.common.WithNSaturnCores(1) ++
  new chipyard.config.AbstractConfig
)

class Saturn3WideConfig extends Config(
  new saturn.common.WithSaturnFetchWidth(16) ++
  new saturn.common.WithSaturnRetireWidth(3) ++
  new saturn.common.WithNSaturnCores(1) ++
  new chipyard.config.AbstractConfig
)

class Saturn4WideConfig extends Config(
  new saturn.common.WithSaturnFetchWidth(32) ++
  new saturn.common.WithSaturnRetireWidth(4) ++
  new saturn.common.WithNSaturnCores(1) ++
  new chipyard.config.AbstractConfig
)

class Saturn5WideConfig extends Config(
  new saturn.common.WithSaturnFetchWidth(64) ++
  new saturn.common.WithSaturnRetireWidth(5) ++
  new saturn.common.WithNSaturnCores(1) ++
  new chipyard.config.AbstractConfig
)
