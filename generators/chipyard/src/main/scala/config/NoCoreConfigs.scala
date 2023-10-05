package chipyard

import org.chipsalliance.cde.config.{Config}

// A empty config with no cores. Useful for testing
class NoCoresConfig extends Config(
  new testchipip.WithNoBootAddrReg ++
  new testchipip.WithNoCustomBootPin ++
  new chipyard.config.WithNoCLINT ++
  new chipyard.config.WithNoBootROM ++
  new chipyard.config.WithBroadcastManager ++
  new chipyard.config.WithNoUART ++
  new chipyard.config.WithNoTileClockGaters ++
  new chipyard.config.WithNoTileResetSetters ++
  new chipyard.config.WithNoBusErrorDevices ++
  new chipyard.config.WithNoDebug ++
  new chipyard.config.WithNoPLIC ++
  new chipyard.config.AbstractConfig)
