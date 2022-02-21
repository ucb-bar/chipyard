package chipyard

import freechips.rocketchip.config.{Config}

// --------------
// Gemmini FP Chipyard Configs
// --------------

class GemminiFP32RocketConfig extends Config(
  new gemmini.GemminiFP32DefaultConfig ++                        // use Gemmini systolic array GEMM accelerator with floating point
  new freechips.rocketchip.subsystem.WithInclusiveCache(nBanks=4) ++
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new chipyard.config.AbstractConfig)

class GemminiFP32BoomConfig extends Config(
  new gemmini.GemminiFP32DefaultConfig ++                        // use Gemmini systolic array GEMM accelerator with floating point
  new freechips.rocketchip.subsystem.WithInclusiveCache(nBanks=4) ++
  new boom.common.WithNLargeBooms(1) ++
  new chipyard.config.AbstractConfig)

class GemminiBF16RocketConfig extends Config(
  new gemmini.GemminiBF16DefaultConfig ++                        // use Gemmini systolic array GEMM accelerator with floating point
  new freechips.rocketchip.subsystem.WithInclusiveCache(nBanks=4) ++
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new chipyard.config.AbstractConfig)

class GemminiBF16BoomConfig extends Config(
  new gemmini.GemminiBF16DefaultConfig ++                        // use Gemmini systolic array GEMM accelerator with floating point
  new freechips.rocketchip.subsystem.WithInclusiveCache(nBanks=4, outerLatencyCycles=80) ++
  new boom.common.WithNLargeBooms(1) ++
  new chipyard.config.AbstractConfig)

class Gemmini8BF16RocketConfig extends Config(
  new gemmini.GemminiBF16Default8Config ++                        // use Gemmini systolic array GEMM accelerator with floating point
  new freechips.rocketchip.subsystem.WithInclusiveCache(nBanks=4) ++
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new chipyard.config.AbstractConfig)

class Gemmini8BF16BoomConfig extends Config(
  new gemmini.GemminiBF16Default8Config ++                        // use Gemmini systolic array GEMM accelerator with floating point
  new freechips.rocketchip.subsystem.WithInclusiveCache(nBanks=4) ++
  new boom.common.WithNLargeBooms(1) ++
  new chipyard.config.AbstractConfig)

class GemminiBF16DualRocketConfig extends Config(
  new gemmini.GemminiBF16DefaultConfig ++                        // use Gemmini systolic array GEMM accelerator with floating point
  new freechips.rocketchip.subsystem.WithInclusiveCache(nBanks=4) ++
  new freechips.rocketchip.subsystem.WithNBigCores(2) ++
  new chipyard.config.AbstractConfig)

class GemminiBF16DualBoomConfig extends Config(
  new gemmini.GemminiBF16DefaultConfig ++                        // use Gemmini systolic array GEMM accelerator with floating point
  new freechips.rocketchip.subsystem.WithInclusiveCache(nBanks=4) ++
  new boom.common.WithNLargeBooms(2) ++
  new chipyard.config.AbstractConfig)

class GemminiFP32DualRocketConfig extends Config(
  new gemmini.GemminiFP32DefaultConfig ++                        // use Gemmini systolic array GEMM accelerator with floating point
  new freechips.rocketchip.subsystem.WithInclusiveCache(nBanks=4) ++
  new freechips.rocketchip.subsystem.WithNBigCores(2) ++
  new chipyard.config.AbstractConfig)

class Gemmini8BF16DualRocketConfig extends Config(
  new gemmini.GemminiBF16Default8Config ++                        // use Gemmini systolic array GEMM accelerator with floating point
  new freechips.rocketchip.subsystem.WithInclusiveCache(nBanks=4) ++
  new freechips.rocketchip.subsystem.WithNBigCores(2) ++
  new chipyard.config.AbstractConfig)

class Gemmini8BF16DualBoomConfig extends Config(
  new gemmini.GemminiBF16Default8Config ++                        // use Gemmini systolic array GEMM accelerator with floating point
  new freechips.rocketchip.subsystem.WithInclusiveCache(nBanks=4) ++
  new boom.common.WithNLargeBooms(2) ++
  new chipyard.config.AbstractConfig)
