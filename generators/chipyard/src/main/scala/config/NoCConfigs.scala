package chipyard

import freechips.rocketchip.config.{Config}
import freechips.rocketchip.diplomacy.{AsynchronousCrossing}

import constellation.{VirtualChannelParams}
import constellation.topology.{MasterAllocTables}


class DualRocketNoCConfig extends Config(
  new constellation.WithConstellationNoCSystemBus(Seq(0, 0, 1), Seq(0, 2, 3, 4, 5)) ++
  new constellation.WithNBlockingVirtualNetworks(5) ++
  new constellation.WithUniformVirtualChannels(6, VirtualChannelParams(3)) ++
  new constellation.Mesh2DConfig(2, 3, MasterAllocTables.mesh2DDimensionOrderedHighest) ++
  new freechips.rocketchip.subsystem.WithNonblockingL1(4) ++
  new freechips.rocketchip.subsystem.WithNBigCores(2) ++
  new freechips.rocketchip.subsystem.WithNBanks(4) ++
  new chipyard.config.AbstractConfig)

class QuadRocketNoCConfig extends Config(
  new constellation.WithConstellationNoCSystemBus(Seq(0, 0, 1, 2, 3), Seq(0) ++ Seq.tabulate(16) { _ + 4 }) ++
  new constellation.WithNBlockingVirtualNetworks(5) ++
  new constellation.WithUniformVirtualChannels(6, VirtualChannelParams(3)) ++
  new constellation.Mesh2DConfig(4, 5, MasterAllocTables.mesh2DDimensionOrderedHighest) ++
  new freechips.rocketchip.subsystem.WithNonblockingL1(4) ++
  new freechips.rocketchip.subsystem.WithNBigCores(4) ++
  new freechips.rocketchip.subsystem.WithInclusiveCache(nBanks=16, capacityKB=1024) ++
  new chipyard.config.AbstractConfig)


class HexaRocketNoCConfig extends Config(
  new constellation.WithConstellationNoCSystemBus(Seq(4, 0, 2, 5, 6, 9, 11), Seq(7, 1, 3, 8, 10)) ++
  new constellation.WithNBlockingVirtualNetworks(5) ++
  new constellation.WithCombineRCVA ++
  new constellation.WithCombineSAST ++
  new constellation.WithUniformVirtualChannels(6, VirtualChannelParams(3)) ++
  new constellation.Mesh2DConfig(4, 3, MasterAllocTables.mesh2DDimensionOrderedHighest) ++
  new freechips.rocketchip.subsystem.WithNonblockingL1(4) ++
  new freechips.rocketchip.subsystem.WithNBigCores(6) ++
  new freechips.rocketchip.subsystem.WithNBanks(4) ++
  new chipyard.config.AbstractConfig)
