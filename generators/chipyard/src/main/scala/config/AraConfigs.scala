package chipyard

import org.chipsalliance.cde.config.{Config}
import saturn.common.{VectorParams}

// Rocket-integrated configs
class Ara2LaneRocketConfig extends Config(
  new ara.WithAraRocketVectorUnit(2) ++
  new freechips.rocketchip.rocket.WithNBigCores(1) ++
  new chipyard.config.AbstractConfig)


class Ara4LaneRocketConfig extends Config(
  new ara.WithAraRocketVectorUnit(4) ++
  new chipyard.config.WithSystemBusWidth(128) ++
  new freechips.rocketchip.rocket.WithNBigCores(1) ++
  new chipyard.config.AbstractConfig)
