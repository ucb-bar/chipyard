package firesim.firesim

import freechips.rocketchip.config.{Parameters, Config}
import freechips.rocketchip.subsystem._

import firesim.bridges._
import firesim.configs._


//**********************************************************************************
//* EE290-2 FireSim Gemmini Configurations
//*********************************************************************************/

class FireSimGemminiEE290Lab2RocketConfig extends Config(
  new WithInclusiveCache ++
  new gemmini.GemminiEE290Lab2Config ++
  new WithNBigCores(1) ++
  new FireSimRocketChipConfig)


class FireSimGemminiEE290Lab2BigSPRocketConfig extends Config(
  new WithInclusiveCache ++
  new gemmini.GemminiEE290Lab2LargeSPConfig ++
  new WithNBigCores(1) ++
  new FireSimRocketChipConfig)

