package chipyard

import freechips.rocketchip.config.Config
import freechips.rocketchip.diplomacy.AsynchronousCrossing
import freechips.rocketchip.subsystem.WithoutTLMonitors

// ------------------------------
// Configs with RoCC Accelerators
// ------------------------------

// DOC include start: GemminiRocketConfig
class GemminiRocketConfig extends Config(
  new gemmini.DefaultGemminiConfig ++                            // use Gemmini systolic array GEMM accelerator
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new chipyard.config.WithSystemBusWidth(128) ++
  new chipyard.config.AbstractConfig)
// DOC include end: GemminiRocketConfig

class FPGemminiRocketConfig extends Config(
  new gemmini.GemminiFP32DefaultConfig ++                         // use FP32Gemmini systolic array GEMM accelerator
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new chipyard.config.WithSystemBusWidth(128) ++
  new chipyard.config.AbstractConfig)

class LeanGemminiRocketConfig extends Config(
  new gemmini.LeanGemminiConfig ++                                 // use Lean Gemmini systolic array GEMM accelerator
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new chipyard.config.WithSystemBusWidth(128) ++
  new chipyard.config.AbstractConfig)

class LeanGemminiPrintfRocketConfig extends Config(
  new gemmini.LeanGemminiPrintfConfig ++                                 // use Lean Gemmini systolic array GEMM accelerator
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new chipyard.config.WithSystemBusWidth(128) ++
  new chipyard.config.AbstractConfig)

class HwachaRocketConfig extends Config(
  new chipyard.config.WithHwachaTest ++
  new hwacha.DefaultHwachaConfig ++                              // use Hwacha vector accelerator
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new chipyard.config.WithSystemBusWidth(128) ++
  new chipyard.config.AbstractConfig)

class MempressRocketConfig extends Config(
  new mempress.WithMemPress ++                                    // use Mempress (memory traffic generation) accelerator
  new chipyard.config.WithExtMemIdBits(7) ++                      // use 7 bits for tl like request id
  new chipyard.config.WithSystemBusWidth(128) ++
  new freechips.rocketchip.subsystem.WithNBanks(8) ++
  new freechips.rocketchip.subsystem.WithInclusiveCache(nWays=16, capacityKB=2048) ++
  new freechips.rocketchip.subsystem.WithNMemoryChannels(4) ++
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new chipyard.config.AbstractConfig)

class HwachaLargeBoomConfig extends Config(
  new chipyard.config.WithHwachaTest ++
  new hwacha.DefaultHwachaConfig ++                              // use Hwacha vector accelerator
  new boom.common.WithNLargeBooms(1) ++
  new chipyard.config.WithSystemBusWidth(128) ++
  new chipyard.config.AbstractConfig)

class StellarRocketConfig extends Config(
  new stellar.DefaultStellarConfig ++
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new chipyard.config.AbstractConfig)

class SmallStellarRocketConfig extends Config(
  new stellar.LargeDenseStellarConfig(size=4) ++
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new chipyard.config.AbstractConfig)

class MidStellarRocketConfig extends Config(
  new stellar.LargeDenseStellarConfig(size=8) ++
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new chipyard.config.AbstractConfig)

class LargeStellarRocketConfig extends Config(
  new stellar.LargeDenseStellarConfig(size=16) ++
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new chipyard.config.AbstractConfig)

class SparseDenseStellarRocketConfig extends Config(
  new stellar.SparseDenseStellarConfig ++
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new chipyard.config.AbstractConfig)

class SmallSparseDenseStellarRocketConfig extends Config(
  new stellar.SparseDenseStellarConfig(size = 4) ++
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new chipyard.config.AbstractConfig)

class MidSparseDenseStellarRocketConfig extends Config(
  new stellar.SparseDenseStellarConfig(size = 8) ++
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new chipyard.config.AbstractConfig)

class LargeSparseDenseStellarRocketConfig extends Config(
  new stellar.SparseDenseStellarConfig(size = 16) ++
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new chipyard.config.AbstractConfig)

class LoadBalancedSparseDenseStellarRocketConfig extends Config(
  new stellar.SparseDenseStellarConfig(isLoadBalanced = true) ++
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new chipyard.config.AbstractConfig)

class OuterSpaceMatmulStellarRocketConfig extends Config(
  new stellar.OuterSpaceStellarConfig(size = 2, hasMatmul = true, hasMerger = false) ++
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new chipyard.config.AbstractConfig)

class SmallOuterSpaceMatmulStellarRocketConfig extends Config(
  new stellar.OuterSpaceStellarConfig(size = 4, hasMatmul = true, hasMerger = false) ++
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new chipyard.config.AbstractConfig)

class MidOuterSpaceMatmulStellarRocketConfig extends Config(
  new stellar.OuterSpaceStellarConfig(size = 8, hasMatmul = true, hasMerger = false) ++
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new chipyard.config.AbstractConfig)

class LargeOuterSpaceMatmulStellarRocketConfig extends Config(
  new stellar.OuterSpaceStellarConfig(size = 16, hasMatmul = true, hasMerger = false) ++
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new chipyard.config.AbstractConfig)

class OuterSpaceMergerStellarRocketConfig extends Config(
  new stellar.OuterSpaceStellarConfig(size = 2, hasMatmul = false, hasMerger = true) ++
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new chipyard.config.AbstractConfig)

class SmallOuterSpaceMergerStellarRocketConfig extends Config(
  new stellar.OuterSpaceStellarConfig(size = 4, hasMatmul = false, hasMerger = true) ++
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new chipyard.config.AbstractConfig)

class MidOuterSpaceMergerStellarRocketConfig extends Config(
  new stellar.OuterSpaceStellarConfig(size = 8, hasMatmul = false, hasMerger = true) ++
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new chipyard.config.AbstractConfig)

class LargeOuterSpaceMergerStellarRocketConfig extends Config(
  new stellar.OuterSpaceStellarConfig(size = 16, hasMatmul = false, hasMerger = true) ++
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new chipyard.config.AbstractConfig)

class SCNNStellarRocketConfig extends Config(
  new stellar.SCNNStellarConfig() ++
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new chipyard.config.AbstractConfig)

class ExpensiveSpArchMergerStellarRocketConfig extends Config(
  new stellar.SpArchMergerStellarConfig(isCheap = false, throughput = 4, nLevels = 2, check_result = true) ++
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new chipyard.config.AbstractConfig)

class CheapSpArchMergerStellarRocketConfig extends Config(
  new stellar.SpArchMergerStellarConfig(isCheap = true, throughput = 4, nLevels = 2, check_result = true) ++
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new chipyard.config.AbstractConfig)

class ExpensiveDummySpArchMergerStellarRocketConfig extends Config(
  new stellar.SpArchMergerStellarConfig(isCheap = false, throughput = 4, nLevels = 2, check_result = false) ++
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new chipyard.config.AbstractConfig)

class CheapDummySpArchMergerStellarRocketConfig extends Config(
  new stellar.SpArchMergerStellarConfig(isCheap = true, throughput = 4, nLevels = 2, check_result = false) ++
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new chipyard.config.AbstractConfig)

class TinyExpensiveSpArchMergerStellarRocketConfig extends Config(
  new stellar.SpArchMergerStellarConfig(isCheap = false, throughput = 16, nLevels = 1, check_result = true) ++
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new chipyard.config.AbstractConfig)

class TinyCheapSpArchMergerStellarRocketConfig extends Config(
  new stellar.SpArchMergerStellarConfig(isCheap = true, throughput = 16, nLevels = 1, check_result = true) ++
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new chipyard.config.AbstractConfig)

class SmallExpensiveSpArchMergerStellarRocketConfig extends Config(
  new stellar.SpArchMergerStellarConfig(isCheap = false, throughput = 16, nLevels = 4, check_result = true) ++
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new chipyard.config.AbstractConfig)

class SmallCheapSpArchMergerStellarRocketConfig extends Config(
  new stellar.SpArchMergerStellarConfig(isCheap = true, throughput = 16, nLevels = 4, check_result = true) ++
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new chipyard.config.AbstractConfig)

class MidExpensiveSpArchMergerStellarRocketConfig extends Config(
  new stellar.SpArchMergerStellarConfig(isCheap = false, throughput = 16, nLevels = 5, check_result = true) ++
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new chipyard.config.AbstractConfig)

class MidCheapSpArchMergerStellarRocketConfig extends Config(
  new stellar.SpArchMergerStellarConfig(isCheap = true, throughput = 16, nLevels = 5, check_result = true) ++
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new chipyard.config.AbstractConfig)

class LargeExpensiveSpArchMergerStellarRocketConfig extends Config(
  new stellar.SpArchMergerStellarConfig(isCheap = false, throughput = 16, nLevels = 6, check_result = true) ++
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new freechips.rocketchip.subsystem.WithoutTLMonitors ++
  new chipyard.config.AbstractConfig)

class LargeCheapSpArchMergerStellarRocketConfig extends Config(
  new stellar.SpArchMergerStellarConfig(isCheap = true, throughput = 16, nLevels = 6, check_result = true) ++
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new freechips.rocketchip.subsystem.WithoutTLMonitors ++
  new chipyard.config.AbstractConfig)

class LargeExpensiveDummySpArchMergerStellarRocketConfig extends Config(
  new stellar.SpArchMergerStellarConfig(isCheap = false, throughput = 16, nLevels = 6, check_result = false) ++
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new freechips.rocketchip.subsystem.WithoutTLMonitors ++
  new chipyard.config.AbstractConfig)

class LargeCheapDummySpArchMergerStellarRocketConfig extends Config(
  new stellar.SpArchMergerStellarConfig(isCheap = true, throughput = 16, nLevels = 6, check_result = false) ++
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new freechips.rocketchip.subsystem.WithoutTLMonitors ++
  new chipyard.config.AbstractConfig)

class LargerCheapDummySpArchMergerStellarRocketConfig extends Config(
  new stellar.SpArchMergerStellarConfig(isCheap = true, throughput = 32, nLevels = 6, check_result = false) ++
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new freechips.rocketchip.subsystem.WithoutTLMonitors ++
  new chipyard.config.AbstractConfig)
