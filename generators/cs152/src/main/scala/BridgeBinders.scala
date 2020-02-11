package firesim.firesim

import chisel3._

import freechips.rocketchip.config.Config
import freechips.rocketchip.subsystem.HasTilesModuleImp
import firesim.util.RegisterBridgeBinder

// This ties off the top-level external interrupt port when the PLIC is absent
class WithTiedOffMEIP extends RegisterBridgeBinder({ case target: HasTilesModuleImp =>
  target.meip.foreach { m =>
    m.foreach { _  := false.B }
  }
  Seq()
})

class WithCS152FireSimBridges extends Config(
  new WithTiedOffMEIP ++
  new WithTiedOffDebug ++
  new WithSerialBridge ++
  new WithFASEDBridge
)
