package chipyard.config

import org.chipsalliance.cde.config.{Config}
import freechips.rocketchip.subsystem.{SystemBusKey, BankedL2Key, CoherenceManagerWrapper, InclusiveCacheKey}
import freechips.rocketchip.diplomacy.{DTSTimebase}
import sifive.blocks.inclusivecache.{InclusiveCachePortParameters}

// Replaces the L2 with a broadcast manager for maintaining coherence
class WithBroadcastManager extends Config((site, here, up) => {
  case BankedL2Key => up(BankedL2Key, site).copy(coherenceManager = CoherenceManagerWrapper.broadcastManager)
})

class WithSystemBusWidth(bitWidth: Int) extends Config((site, here, up) => {
  case SystemBusKey => up(SystemBusKey, site).copy(beatBytes=bitWidth/8)
})

class WithDTSTimebase(freqMHz: BigInt) extends Config((site, here, up) => {
  case DTSTimebase => freqMHz
})

// Adds buffers on the interior of the inclusive L2, to improve PD
class WithInclusiveCacheInteriorBuffer(buffer: InclusiveCachePortParameters = InclusiveCachePortParameters.full) extends Config((site, here, up) => {
  case InclusiveCacheKey => up(InclusiveCacheKey).copy(bufInnerInterior=buffer, bufOuterInterior=buffer)
})

