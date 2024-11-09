package chipyard

import org.chipsalliance.cde.config.{Config}

// A empty config with no cores. Useful for testing
class NoCoresConfig extends Config(
  new testchipip.soc.WithNoScratchpads ++
  new testchipip.boot.WithNoBootAddrReg ++
  new testchipip.boot.WithNoCustomBootPin ++
  new chipyard.config.WithNoCLINT ++
  new chipyard.config.WithNoBootROM ++
  new chipyard.config.WithBroadcastManager ++
  new chipyard.config.WithNoUART ++
  new chipyard.config.WithNoTileClockGaters ++
  new chipyard.config.WithNoTileResetSetters ++
  new chipyard.config.WithNoDebug ++
  new chipyard.config.WithNoPLIC ++
  new chipyard.config.WithNoBusErrorDevices ++
  new chipyard.config.AbstractConfig)

// A config that uses a empty chiptop module with no rocket-chip soc components
class EmptyChipTopConfig extends Config(
  new chipyard.example.WithEmptyChipTop ++
  new chipyard.config.AbstractConfig             // since we aren't using rocket-chip, this doesn't do anything
)
