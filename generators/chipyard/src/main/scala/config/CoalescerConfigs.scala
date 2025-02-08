package chipyard

import org.chipsalliance.cde.config.{Config}
import freechips.rocketchip.prci.AsynchronousCrossing

class MemtraceCoreConfig extends Config(
  // Memtrace
  new radiance.subsystem.WithMemtraceCore("vecadd.core1.thread4.trace",
    traceHasSource = false) ++
  // new radiance.subsystem.WithMemtraceCore("nvbit.vecadd.n100000.filter_sm0.lane32.trace",
  //   traceHasSource = false) ++
  new radiance.subsystem.WithCoalescer(nNewSrcIds = 2) ++
  new radiance.subsystem.WithSimtConfig(nMemLanes = 4, nSrcIds = 8) ++
  // L2
  new freechips.rocketchip.subsystem.WithInclusiveCache(nWays=8, capacityKB=512) ++
  new freechips.rocketchip.subsystem.WithNBanks(4) ++
  new chipyard.config.WithSystemBusWidth(16 * 8) ++
  new chipyard.NoCoresConfig
  )

