package chipyard

import org.chipsalliance.cde.config.{Config}
import saturn.common.{VectorParams}

// Rocket-integrated configs
class V4096Ara2LaneRocketConfig extends Config(
  new ara.WithAraRocketVectorUnit(4096, 2) ++
  new freechips.rocketchip.rocket.WithNHugeCores(1) ++
  new chipyard.config.AbstractConfig)


class V8192Ara4LaneRocketConfig extends Config(
  new ara.WithAraRocketVectorUnit(8192, 4) ++
  new chipyard.config.WithSystemBusWidth(128) ++
  new freechips.rocketchip.rocket.WithNHugeCores(1) ++
  new chipyard.config.AbstractConfig)


// Shuttle-integrated configs
class V4096Ara2LaneShuttleConfig extends Config(
  new ara.WithAraShuttleVectorUnit(4096, 2) ++
  new shuttle.common.WithNShuttleCores(1) ++
  new chipyard.config.AbstractConfig)


class V8192Ara4LaneShuttleConfig extends Config(
  new ara.WithAraShuttleVectorUnit(8192, 4) ++
  new chipyard.config.WithSystemBusWidth(128) ++
  new shuttle.common.WithShuttleTileBeatBytes(16) ++
  new shuttle.common.WithNShuttleCores(1) ++
  new chipyard.config.AbstractConfig)
