package chipyard.config

import org.chipsalliance.cde.config.{Config, Field, Parameters}
import tracegen.{TraceGenSystem}
import chipyard.{BuildSystem}
import chipyard.clocking.{HasChipyardPRCI}

class TraceGenTop(implicit p: Parameters) extends TraceGenSystem
  with HasChipyardPRCI
  with edu.berkeley.cs.ucie.digital.tilelink.CanHaveTLUCIAdapter // Connect UCIe stack

class WithTracegenSystem extends Config((site, here, up) => {
  case BuildSystem => (p: Parameters) => new TraceGenTop()(p)
})
