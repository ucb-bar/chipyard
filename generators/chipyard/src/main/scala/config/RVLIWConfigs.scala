package chipyard

import chisel3._

import org.chipsalliance.cde.config.{Config}

class RVLIWFixed4Config extends Config(
  new rvliw.common.WithRVLIWCore(rvliw.common.Fixed4Params()) ++
  new testchipip.soc.WithNoScratchpads ++                      // No scratchpads
  new freechips.rocketchip.subsystem.WithNoMemPort ++          // use no external memory
  new freechips.rocketchip.subsystem.WithNBanks(0) ++
  new chipyard.config.AbstractConfig
)
