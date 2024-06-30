package chipyard.config

import org.chipsalliance.cde.config.{Config}
import freechips.rocketchip.subsystem._
import freechips.rocketchip.resources.{DTSTimebase}
import sifive.blocks.inclusivecache.{InclusiveCachePortParameters}

// Replaces the L2 with a broadcast manager for maintaining coherence
class WithBroadcastManager extends Config((site, here, up) => {
  case SubsystemBankedCoherenceKey => up(SubsystemBankedCoherenceKey, site).copy(coherenceManager = CoherenceManagerWrapper.broadcastManager)
})

class WithBroadcastParams(params: BroadcastParams) extends Config((site, here, up) => {
  case BroadcastKey => params
})

class WithSystemBusWidth(bitWidth: Int) extends Config((site, here, up) => {
  case SystemBusKey => up(SystemBusKey, site).copy(beatBytes=bitWidth/8)
})

class WithInclusiveCacheWriteBytes(b: Int) extends Config((site, here, up) => {
  case InclusiveCacheKey => up(InclusiveCacheKey).copy(writeBytes = b)
})

// Adds buffers on the interior of the inclusive LLC, to improve PD
class WithInclusiveCacheInteriorBuffer(buffer: InclusiveCachePortParameters = InclusiveCachePortParameters.full) extends Config((site, here, up) => {
  case InclusiveCacheKey => up(InclusiveCacheKey).copy(bufInnerInterior=buffer, bufOuterInterior=buffer)
})

// Adds buffers on the exterior of the inclusive LLC, to improve PD
class WithInclusiveCacheExteriorBuffer(buffer: InclusiveCachePortParameters = InclusiveCachePortParameters.full) extends Config((site, here, up) => {
  case InclusiveCacheKey => up(InclusiveCacheKey).copy(bufInnerExterior=buffer, bufOuterExterior=buffer)
})
