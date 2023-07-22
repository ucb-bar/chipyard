package chipyard

import org.chipsalliance.cde.config.{Config}
import freechips.rocketchip.diplomacy.{AsynchronousCrossing}

class MemtraceCoreConfig extends Config(
  // Memtrace
  new freechips.rocketchip.subsystem.WithMemtraceCore("vecadd.core1.thread4.trace",
    traceHasSource = false) ++
  // new freechips.rocketchip.subsystem.WithMemtraceCore("nvbit.vecadd.n100000.filter_sm0.lane32.trace",
  //   traceHasSource = false) ++
  new freechips.rocketchip.subsystem.WithCoalescer(nNewSrcIds = 2) ++
  new freechips.rocketchip.subsystem.WithSimtLanes(nLanes = 4, nSrcIds = 8) ++
  // L2
  new freechips.rocketchip.subsystem.WithInclusiveCache(nWays=8, capacityKB=512) ++
  new freechips.rocketchip.subsystem.WithNBanks(4) ++
  new chipyard.config.WithSystemBusWidth(16 * 8) ++
  // Small Rocket core that does nothing
  new freechips.rocketchip.subsystem.WithNCustomSmallCores(1) ++
  new chipyard.config.AbstractConfig
  )


/////////////////////////////////////////////////
/// Various Configs for perf testing (feel free to delete them later)
/////////////////////////////////////////////////

//8 src id section
class MemtraceCoreNV64B8IdConfig extends Config(
  new freechips.rocketchip.subsystem.WithMemtraceCore("nvbit.vecadd.n100000.filter_sm0.lane32.trace",
  traceHasSource = false) ++
  new freechips.rocketchip.subsystem.WithCoalescer(nNewSrcIds=8) ++
  new freechips.rocketchip.subsystem.WithSimtLanes(nLanes=32, nSrcIds=8) ++
  // L2
  new freechips.rocketchip.subsystem.WithInclusiveCache(nWays=8, capacityKB=512) ++
  new freechips.rocketchip.subsystem.WithNBanks(4) ++
  new chipyard.config.WithSystemBusWidth(64) ++
  // Small Rocket core that does nothing
  new freechips.rocketchip.subsystem.WithNCustomSmallCores(1) ++
  new chipyard.config.AbstractConfig
  )

class MemtraceCoreNV128B8IdConfig extends Config(
  new freechips.rocketchip.subsystem.WithMemtraceCore("nvbit.vecadd.n100000.filter_sm0.lane32.trace",
  traceHasSource = false) ++
  new freechips.rocketchip.subsystem.WithCoalescer(nNewSrcIds=8) ++
  new freechips.rocketchip.subsystem.WithSimtLanes(nLanes=32, nSrcIds=8) ++
  // L2
  new freechips.rocketchip.subsystem.WithInclusiveCache(nWays=8, capacityKB=512) ++
  new freechips.rocketchip.subsystem.WithNBanks(4) ++
  new chipyard.config.WithSystemBusWidth(128) ++
  // Small Rocket core that does nothing
  new freechips.rocketchip.subsystem.WithNCustomSmallCores(1) ++
  new chipyard.config.AbstractConfig
  )

class MemtraceCoreNV256B8IdConfig extends Config(
  new freechips.rocketchip.subsystem.WithMemtraceCore("nvbit.vecadd.n100000.filter_sm0.lane32.trace",
  traceHasSource = false) ++
  new freechips.rocketchip.subsystem.WithCoalescer(nNewSrcIds=8) ++
  new freechips.rocketchip.subsystem.WithSimtLanes(nLanes=32, nSrcIds=8) ++
  // L2
  new freechips.rocketchip.subsystem.WithInclusiveCache(nWays=8, capacityKB=512) ++
  new freechips.rocketchip.subsystem.WithNBanks(4) ++
  new chipyard.config.WithSystemBusWidth(256) ++
  // Small Rocket core that does nothing
  new freechips.rocketchip.subsystem.WithNCustomSmallCores(1) ++
  new chipyard.config.AbstractConfig
  )

class MemtraceCoreNV512B8IdConfig extends Config(
  new freechips.rocketchip.subsystem.WithMemtraceCore("nvbit.vecadd.n100000.filter_sm0.lane32.trace",
  traceHasSource = false) ++
  new freechips.rocketchip.subsystem.WithCoalescer(nNewSrcIds=8) ++
  new freechips.rocketchip.subsystem.WithSimtLanes(nLanes=32, nSrcIds=8) ++
  // L2
  new freechips.rocketchip.subsystem.WithInclusiveCache(nWays=8, capacityKB=512) ++
  new freechips.rocketchip.subsystem.WithNBanks(4) ++
  new chipyard.config.WithSystemBusWidth(512) ++
  // Small Rocket core that does nothing
  new freechips.rocketchip.subsystem.WithNCustomSmallCores(1) ++
  new chipyard.config.AbstractConfig
  )

//16 src id section
class MemtraceCoreNV64B16IdConfig extends Config(
  new freechips.rocketchip.subsystem.WithMemtraceCore("nvbit.vecadd.n100000.filter_sm0.lane32.trace",
  traceHasSource = false) ++
  new freechips.rocketchip.subsystem.WithCoalescer(nNewSrcIds=16) ++
  new freechips.rocketchip.subsystem.WithSimtLanes(nLanes=32, nSrcIds=16) ++
  // L2
  new freechips.rocketchip.subsystem.WithInclusiveCache(nWays=8, capacityKB=512) ++
  new freechips.rocketchip.subsystem.WithNBanks(4) ++
  new chipyard.config.WithSystemBusWidth(64) ++
  // Small Rocket core that does nothing
  new freechips.rocketchip.subsystem.WithNCustomSmallCores(1) ++
  new chipyard.config.AbstractConfig
  )

class MemtraceCoreNV128B16IdConfig extends Config(
  new freechips.rocketchip.subsystem.WithMemtraceCore("nvbit.vecadd.n100000.filter_sm0.lane32.trace",
  traceHasSource = false) ++
  new freechips.rocketchip.subsystem.WithCoalescer(nNewSrcIds=16) ++
  new freechips.rocketchip.subsystem.WithSimtLanes(nLanes=32, nSrcIds=16) ++
  // L2
  new freechips.rocketchip.subsystem.WithInclusiveCache(nWays=8, capacityKB=512) ++
  new freechips.rocketchip.subsystem.WithNBanks(4) ++
  new chipyard.config.WithSystemBusWidth(128) ++
  // Small Rocket core that does nothing
  new freechips.rocketchip.subsystem.WithNCustomSmallCores(1) ++
  new chipyard.config.AbstractConfig
  )

class MemtraceCoreNV256B16IdConfig extends Config(
  new freechips.rocketchip.subsystem.WithMemtraceCore("nvbit.vecadd.n100000.filter_sm0.lane32.trace",
  traceHasSource = false) ++
  new freechips.rocketchip.subsystem.WithCoalescer(nNewSrcIds=16) ++
  new freechips.rocketchip.subsystem.WithSimtLanes(nLanes=32, nSrcIds=16) ++
  // L2
  new freechips.rocketchip.subsystem.WithInclusiveCache(nWays=8, capacityKB=512) ++
  new freechips.rocketchip.subsystem.WithNBanks(4) ++
  new chipyard.config.WithSystemBusWidth(256) ++
  // Small Rocket core that does nothing
  new freechips.rocketchip.subsystem.WithNCustomSmallCores(1) ++
  new chipyard.config.AbstractConfig
  )

class MemtraceCoreNV512B16IdConfig extends Config(
  new freechips.rocketchip.subsystem.WithMemtraceCore("nvbit.vecadd.n100000.filter_sm0.lane32.trace",
  traceHasSource = false) ++
  new freechips.rocketchip.subsystem.WithCoalescer(nNewSrcIds=16) ++
  new freechips.rocketchip.subsystem.WithSimtLanes(nLanes=32, nSrcIds=16) ++
  // L2
  new freechips.rocketchip.subsystem.WithInclusiveCache(nWays=8, capacityKB=512) ++
  new freechips.rocketchip.subsystem.WithNBanks(4) ++
  new chipyard.config.WithSystemBusWidth(512) ++
  // Small Rocket core that does nothing
  new freechips.rocketchip.subsystem.WithNCustomSmallCores(1) ++
  new chipyard.config.AbstractConfig
  )

// 32 ids sections
class MemtraceCoreNV64B32IdConfig extends Config(
  new freechips.rocketchip.subsystem.WithMemtraceCore("nvbit.vecadd.n100000.filter_sm0.lane32.trace",
  traceHasSource = false) ++
  new freechips.rocketchip.subsystem.WithCoalescer(nNewSrcIds=32) ++
  new freechips.rocketchip.subsystem.WithSimtLanes(nLanes=32, nSrcIds=32) ++
  // L2
  new freechips.rocketchip.subsystem.WithInclusiveCache(nWays=8, capacityKB=512) ++
  new freechips.rocketchip.subsystem.WithNBanks(4) ++
  new chipyard.config.WithSystemBusWidth(64) ++
  // Small Rocket core that does nothing
  new freechips.rocketchip.subsystem.WithNCustomSmallCores(1) ++
  new chipyard.config.AbstractConfig
  )

class MemtraceCoreNV128B32IdConfig extends Config(
  new freechips.rocketchip.subsystem.WithMemtraceCore("nvbit.vecadd.n100000.filter_sm0.lane32.trace",
  traceHasSource = false) ++
  new freechips.rocketchip.subsystem.WithCoalescer(nNewSrcIds=32) ++
  new freechips.rocketchip.subsystem.WithSimtLanes(nLanes=32, nSrcIds=32) ++
  // L2
  new freechips.rocketchip.subsystem.WithInclusiveCache(nWays=8, capacityKB=512) ++
  new freechips.rocketchip.subsystem.WithNBanks(4) ++
  new chipyard.config.WithSystemBusWidth(128) ++
  // Small Rocket core that does nothing
  new freechips.rocketchip.subsystem.WithNCustomSmallCores(1) ++
  new chipyard.config.AbstractConfig
  )

class MemtraceCoreNV256B32IdConfig extends Config(
  new freechips.rocketchip.subsystem.WithMemtraceCore("nvbit.vecadd.n100000.filter_sm0.lane32.trace",
  traceHasSource = false) ++
  new freechips.rocketchip.subsystem.WithCoalescer(nNewSrcIds=32) ++
  new freechips.rocketchip.subsystem.WithSimtLanes(nLanes=32, nSrcIds=32) ++
  // L2
  new freechips.rocketchip.subsystem.WithInclusiveCache(nWays=8, capacityKB=512) ++
  new freechips.rocketchip.subsystem.WithNBanks(4) ++
  new chipyard.config.WithSystemBusWidth(256) ++
  // Small Rocket core that does nothing
  new freechips.rocketchip.subsystem.WithNCustomSmallCores(1) ++
  new chipyard.config.AbstractConfig
  )

class MemtraceCoreNV512B32IdConfig extends Config(
  new freechips.rocketchip.subsystem.WithMemtraceCore("nvbit.vecadd.n100000.filter_sm0.lane32.trace",
  traceHasSource = false) ++
  new freechips.rocketchip.subsystem.WithCoalescer(nNewSrcIds=32) ++
  new freechips.rocketchip.subsystem.WithSimtLanes(nLanes=32, nSrcIds=32) ++
  // L2
  new freechips.rocketchip.subsystem.WithInclusiveCache(nWays=8, capacityKB=512) ++
  new freechips.rocketchip.subsystem.WithNBanks(4) ++
  new chipyard.config.WithSystemBusWidth(512) ++
  // Small Rocket core that does nothing
  new freechips.rocketchip.subsystem.WithNCustomSmallCores(1) ++
  new chipyard.config.AbstractConfig
  )
