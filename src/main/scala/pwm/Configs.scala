package pwm

import cde.{Parameters, Config, CDEMatchError}
import testchipip.WithSerialAdapter
import uncore.tilelink.ClientUncachedTileLinkIO
import rocketchip.PeripheryUtils
import chisel3._

class WithPWMAXI extends Config(
  (pname, site, here) => pname match {
    case BuildPWM => (port: ClientUncachedTileLinkIO, p: Parameters) => {
      val pwm = Module(new PWMAXI()(p))
      pwm.io.axi <> PeripheryUtils.convertTLtoAXI(port)
      pwm.io.pwmout
    }
    case _ => throw new CDEMatchError
  })

class WithPWMTL extends Config(
  (pname, site, here) => pname match {
    case BuildPWM => (port: ClientUncachedTileLinkIO, p: Parameters) => {
      val pwm = Module(new PWMTL()(p))
      pwm.io.tl <> port
      pwm.io.pwmout
    }
  })

class PWMAXIConfig extends Config(new WithPWMAXI ++ new example.DefaultExampleConfig)
class PWMTLConfig extends Config(new WithPWMTL ++ new example.DefaultExampleConfig)
