package chipyard.config

import freechips.rocketchip.config.{Config}
import freechips.rocketchip.subsystem.{BankedL2Key, CoherenceManagerWrapper}

// Replaces the L2 with a broadcast manager for maintaining coherence
class WithBroadcastManager extends Config((site, here, up) => {
  case BankedL2Key => up(BankedL2Key, site).copy(coherenceManager = CoherenceManagerWrapper.broadcastManager)
})

