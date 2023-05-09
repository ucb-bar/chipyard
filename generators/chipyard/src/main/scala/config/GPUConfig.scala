package chipyard

import org.chipsalliance.cde.config.{Config}
import freechips.rocketchip.diplomacy.{AsynchronousCrossing}

class MemtraceCoreConfig extends Config(
  // Memtrace core
  new freechips.rocketchip.tilelink.WithMemtraceCore("vecadd.core1.thread4.trace")++
  new freechips.rocketchip.subsystem.WithNLanes(4) ++
  // L2
  new freechips.rocketchip.subsystem.WithInclusiveCache(nWays=8, capacityKB=512) ++
  new freechips.rocketchip.subsystem.WithNBanks(4) ++                                   
  new chipyard.config.WithSystemBusWidth(64) ++ 
  // Small Rocket core that does nothing
  new freechips.rocketchip.subsystem.WithNCustomSmallCores(1) ++
  new chipyard.config.AbstractConfig
  )


