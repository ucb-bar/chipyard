package chipyard

import org.chipsalliance.cde.config.{Config}
import freechips.rocketchip.diplomacy.{AsynchronousCrossing}


class MemtraceCoreConfig extends Config(
  // Memtrace
  new freechips.rocketchip.subsystem.WithMemtraceCore("vecadd.core1.thread4.trace",
    traceHasSource = false) ++
  // new freechips.rocketchip.subsystem.WithMemtraceCore("nvbit.vecadd.n100000.filter_sm0.trace",
  //   traceHasSource = false) ++
  new freechips.rocketchip.subsystem.WithCoalescer ++
  new freechips.rocketchip.subsystem.WithNLanes(4) ++
  // L2
  new freechips.rocketchip.subsystem.WithInclusiveCache(nWays=8, capacityKB=512) ++
  new freechips.rocketchip.subsystem.WithNBanks(4) ++                                   
  new chipyard.config.WithSystemBusWidth(64) ++ 
  // Small Rocket core that does nothing
  new freechips.rocketchip.subsystem.WithNCustomSmallCores(1) ++
  new chipyard.config.AbstractConfig
  )


class MemtraceCore128SbusConfig extends Config(
  // Memtrace
  new freechips.rocketchip.subsystem.WithMemtraceCore("vecadd.core1.thread4.trace",
    traceHasSource = false) ++
  // new freechips.rocketchip.subsystem.WithMemtraceCore("nvbit.vecadd.n100000.filter_sm0.trace",
  //   traceHasSource = false) ++
  new freechips.rocketchip.subsystem.WithCoalescer ++
  new freechips.rocketchip.subsystem.WithNLanes(4) ++
  // L2
  new freechips.rocketchip.subsystem.WithInclusiveCache(nWays=8, capacityKB=512) ++
  new freechips.rocketchip.subsystem.WithNBanks(4) ++                                   
  new chipyard.config.WithSystemBusWidth(128) ++ 
  // Small Rocket core that does nothing
  new freechips.rocketchip.subsystem.WithNCustomSmallCores(1) ++
  new chipyard.config.AbstractConfig
)

class MemtraceCore256SbusConfig extends Config(
  // Memtrace
  new freechips.rocketchip.subsystem.WithMemtraceCore("vecadd.core1.thread4.trace",
    traceHasSource = false) ++
  // new freechips.rocketchip.subsystem.WithMemtraceCore("nvbit.vecadd.n100000.filter_sm0.trace",
  //   traceHasSource = false) ++
  new freechips.rocketchip.subsystem.WithCoalescer ++
  new freechips.rocketchip.subsystem.WithNLanes(4) ++
  // L2
  new freechips.rocketchip.subsystem.WithInclusiveCache(nWays=8, capacityKB=512) ++
  new freechips.rocketchip.subsystem.WithNBanks(4) ++                                   
  new chipyard.config.WithSystemBusWidth(256) ++ 
  // Small Rocket core that does nothing
  new freechips.rocketchip.subsystem.WithNCustomSmallCores(1) ++
  new chipyard.config.AbstractConfig
)

class MemtraceCorePoXarConfig extends Config(
  // Memtrace
  new freechips.rocketchip.subsystem.WithMemtraceCore("vecadd.core1.thread4.trace",
    traceHasSource = false) ++
  // new freechips.rocketchip.subsystem.WithMemtraceCore("nvbit.vecadd.n100000.filter_sm0.trace",
  //   traceHasSource = false) ++
  new freechips.rocketchip.subsystem.WithCoalescer ++
  new freechips.rocketchip.subsystem.WithPriorityCoalXbar++
  new freechips.rocketchip.subsystem.WithNLanes(4) ++
  // L2
  new freechips.rocketchip.subsystem.WithInclusiveCache(nWays=8, capacityKB=512) ++
  new freechips.rocketchip.subsystem.WithNBanks(4) ++                                   
  new chipyard.config.WithSystemBusWidth(64) ++ 
  // Small Rocket core that does nothing
  new freechips.rocketchip.subsystem.WithNCustomSmallCores(1) ++
  new chipyard.config.AbstractConfig
  )