package chipyard.config

import org.chipsalliance.cde.config.{Config}
import freechips.rocketchip.subsystem.{SystemBusKey, BankedL2Key, CoherenceManagerWrapper}
import freechips.rocketchip.diplomacy.{DTSTimebase}

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
