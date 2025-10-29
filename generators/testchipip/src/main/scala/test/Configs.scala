package testchipip.test

import chisel3._
import org.chipsalliance.cde.config.{Parameters, Config}
import freechips.rocketchip.unittest.UnitTests
import freechips.rocketchip.system.BaseConfig

class WithTestChipUnitTests extends Config((site, here, up) => {
  case UnitTests => (testParams: Parameters) =>
    TestChipUnitTests(testParams)
})

class WithClockUtilTests extends Config((site, here, up) => {
  case UnitTests => (testParams: Parameters) => ClockUtilTests()
})

class TestChipUnitTestConfig extends Config(
  new WithTestChipUnitTests ++ new BaseConfig)

class ClockUtilTestConfig extends Config(
  new WithClockUtilTests ++ new BaseConfig)
