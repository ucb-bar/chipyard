package chipyard.config

import freechips.rocketchip.config.{Config, Field, Parameters}
import tracegen.{TraceGenSystem}
import chipyard.{BuildSystem}
import chipyard.clocking.{HasChipyardPRCI}

class TraceGenTop(implicit p: Parameters) extends TraceGenSystem
  with HasChipyardPRCI

class WithTracegenSystem extends Config((site, here, up) => {
  case BuildSystem => (p: Parameters) => new TraceGenTop()(p)
})

