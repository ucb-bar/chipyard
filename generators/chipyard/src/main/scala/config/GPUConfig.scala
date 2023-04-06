package chipyard

import freechips.rocketchip.config.{Config}
import freechips.rocketchip.diplomacy.{AsynchronousCrossing}



class GPUTracerConfig extends Config(

  
  // Attaching GPU Tracer to SBus
  new freechips.rocketchip.tilelink.WithGPUTracer(4, "vecadd.core1.thread4.trace")++
  

  // Creating L2
  new freechips.rocketchip.subsystem.WithInclusiveCache(nWays=8, capacityKB=512) ++
  new freechips.rocketchip.subsystem.WithNBanks(4) ++                                   
  new chipyard.config.WithSystemBusWidth(64) ++ 
  
  new freechips.rocketchip.subsystem.WithNCustomSmallCores(1) ++
  new chipyard.config.AbstractConfig
  )


