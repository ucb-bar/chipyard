package chipyard

import freechips.rocketchip.config.{Config}

// --------------
// Gemmini FP Chipyard Configs
// --------------

class WithConfPrec extends Config((site, here, up) => {
    case hwacha.HwachaConfPrec => false
})
class WithAtlNode extends Config((site, here, up) => {
    case hwacha.HwachaUseAtlNode => true
})  

class GemminiFP32RocketConfig extends Config(
  new gemmini.GemminiFP32DefaultConfig ++                        // use Gemmini systolic array GEMM accelerator with floating point
  //new freechips.rocketchip.subsystem.WithInclusiveCache(nBanks=4, outerLatencyCycles=80) ++
  new freechips.rocketchip.subsystem.WithInclusiveCache ++
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new chipyard.config.AbstractConfig)

class GemminiFP32BoomConfig extends Config(
  new gemmini.GemminiFP32DefaultConfig ++                        // use Gemmini systolic array GEMM accelerator with floating point
  new freechips.rocketchip.subsystem.WithInclusiveCache(nBanks=4, outerLatencyCycles=80) ++
  new boom.common.WithNLargeBooms(1) ++
  new chipyard.config.AbstractConfig)

class GemminiBF16RocketConfig extends Config(
  new gemmini.GemminiBF16DefaultConfig ++                        // use Gemmini systolic array GEMM accelerator with floating point
  new freechips.rocketchip.subsystem.WithInclusiveCache(nBanks=4, outerLatencyCycles=80) ++
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new chipyard.config.AbstractConfig)

class GemminiBF16BoomConfig extends Config(
  new gemmini.GemminiBF16DefaultConfig ++                        // use Gemmini systolic array GEMM accelerator with floating point
  new freechips.rocketchip.subsystem.WithInclusiveCache(nBanks=4, outerLatencyCycles=80) ++
  new boom.common.WithNLargeBooms(1) ++
  new chipyard.config.AbstractConfig)

class Gemmini8BF16RocketConfig extends Config(
  new gemmini.GemminiBF16Default8Config ++                        // use Gemmini systolic array GEMM accelerator with floating point
  new freechips.rocketchip.subsystem.WithInclusiveCache(nBanks=4, outerLatencyCycles=80) ++
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new chipyard.config.AbstractConfig)

class Gemmini8BF16BoomConfig extends Config(
  new gemmini.GemminiBF16Default8Config ++                        // use Gemmini systolic array GEMM accelerator with floating point
  new freechips.rocketchip.subsystem.WithInclusiveCache(nBanks=4, outerLatencyCycles=80) ++
  new boom.common.WithNLargeBooms(1) ++
  new chipyard.config.AbstractConfig)

class GemminiBF16DualRocketConfig extends Config(
  new gemmini.GemminiBF16DefaultConfig ++                        // use Gemmini systolic array GEMM accelerator with floating point
  new freechips.rocketchip.subsystem.WithInclusiveCache(nBanks=4, outerLatencyCycles=80) ++
  new freechips.rocketchip.subsystem.WithNBigCores(2) ++
  new chipyard.config.AbstractConfig)

class GemminiBF16DualBoomConfig extends Config(
  new gemmini.GemminiBF16DefaultConfig ++                        // use Gemmini systolic array GEMM accelerator with floating point
  new freechips.rocketchip.subsystem.WithInclusiveCache(nBanks=4, outerLatencyCycles=80) ++
  new boom.common.WithNLargeBooms(2) ++
  new chipyard.config.AbstractConfig)

class GemminiFP32DualRocketConfig extends Config(
  new gemmini.GemminiFP32DefaultConfig ++                        // use Gemmini systolic array GEMM accelerator with floating point
  new freechips.rocketchip.subsystem.WithInclusiveCache(nBanks=4, outerLatencyCycles=80) ++
  new freechips.rocketchip.subsystem.WithNBigCores(2) ++
  new chipyard.config.AbstractConfig)

class Gemmini8BF16DualRocketConfig extends Config(
  new gemmini.GemminiBF16Default8Config ++                        // use Gemmini systolic array GEMM accelerator with floating point
  new freechips.rocketchip.subsystem.WithInclusiveCache(nBanks=4, outerLatencyCycles=80) ++
  new freechips.rocketchip.subsystem.WithNBigCores(2) ++
  new chipyard.config.AbstractConfig)

class Gemmini8BF16DualBoomConfig extends Config(
  new gemmini.GemminiBF16Default8Config ++                        // use Gemmini systolic array GEMM accelerator with floating point
  new freechips.rocketchip.subsystem.WithInclusiveCache(nBanks=4, outerLatencyCycles=80) ++
  new boom.common.WithNLargeBooms(2) ++
  new chipyard.config.AbstractConfig)

class GemminiBF16BoomHwachaConfig extends Config(
  //new gemmini.GemminiBF16DefaultConfig ++                        // use Gemmini systolic array GEMM accelerator with floating point
  new gemmini.DefaultGemminiConfig(gemmini.GemminiFPConfigs.BF16DefaultConfig) ++
  new chipyard.config.WithHwachaTest ++
  new hwacha.DefaultHwachaConfig ++                              // use Hwacha vector accelerator
  new freechips.rocketchip.subsystem.WithInclusiveCache(nBanks=4, outerLatencyCycles=80) ++
  new boom.common.WithNLargeBooms(1) ++
  new chipyard.config.AbstractConfig)

class Gemmini8BF16BoomHwachaConfig extends Config(
  //new gemmini.GemminiBF16DefaultConfig ++                        // use Gemmini systolic array GEMM accelerator with floating point
  new gemmini.DefaultGemminiConfig(gemmini.GemminiFPConfigs.BF16Default8Config) ++
  new WithConfPrec ++
  new WithAtlNode ++
  new hwacha.WithNoFDIV ++
  new hwacha.WithNoIDIV ++
  new hwacha.WithNVectorRegs(128) ++
  new hwacha.WithNSRAMRFEntries(128) ++
  new hwacha.WithSmallPredRF ++
  new hwacha.WithFLen(32) ++
  new hwacha.DefaultHwachaConfig ++                              // use Hwacha vector accelerator
  new freechips.rocketchip.subsystem.WithInclusiveCache(nBanks=4, outerLatencyCycles=80) ++
  //new boom.common.WithNMediumBooms(1) ++
  new boom.common.WithNLargeBooms(1) ++
  new chipyard.config.AbstractConfig)

class GemminiFP32BoomHwachaConfig extends Config(
  //new gemmini.GemminiBF16DefaultConfig ++                        // use Gemmini systolic array GEMM accelerator with floating point
  new gemmini.DefaultGemminiConfig(gemmini.GemminiFPConfigs.FP32DefaultConfig) ++
  new chipyard.config.WithHwachaTest ++
  new hwacha.DefaultHwachaConfig ++                              // use Hwacha vector accelerator
  new freechips.rocketchip.subsystem.WithInclusiveCache(nBanks=4, outerLatencyCycles=80) ++
  new boom.common.WithNLargeBooms(1) ++
  new chipyard.config.AbstractConfig)

class Gemmini8FP32BoomHwachaConfig extends Config(
  //new gemmini.GemminiBF16DefaultConfig ++                        // use Gemmini systolic array GEMM accelerator with floating point
  new gemmini.DefaultGemminiConfig(gemmini.GemminiFPConfigs.FP32Default8Config) ++
  new WithConfPrec ++
  new WithAtlNode ++
  new hwacha.WithNoFDIV ++
  new hwacha.WithNoIDIV ++
  new hwacha.WithNVectorRegs(128) ++
  new hwacha.WithNSRAMRFEntries(128) ++
  new hwacha.WithSmallPredRF ++
  new hwacha.WithFLen(32) ++
  new hwacha.DefaultHwachaConfig ++                              // use Hwacha vector accelerator
  new freechips.rocketchip.subsystem.WithInclusiveCache(nBanks=4, outerLatencyCycles=80) ++
  new boom.common.WithNLargeBooms(1) ++
  new chipyard.config.AbstractConfig)


class GemminiFP32HwachaConfig extends Config(
  new gemmini.DefaultGemminiConfig(gemmini.GemminiFPConfigs.FP32DefaultConfig) ++
  new chipyard.config.WithHwachaTest ++
  new hwacha.DefaultHwachaConfig ++                              // use Hwacha vector accelerator
  new freechips.rocketchip.subsystem.WithInclusiveCache(nBanks=4, outerLatencyCycles=80) ++
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new chipyard.config.AbstractConfig)
