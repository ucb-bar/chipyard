package testchipip.boot

import chisel3._
import org.chipsalliance.cde.config.{Parameters, Field}
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.devices.tilelink._
import freechips.rocketchip.regmapper._
import freechips.rocketchip.subsystem._
import testchipip.soc.{SubsystemInjector}

case class BootAddrRegParams(
  defaultBootAddress: BigInt = 0x80000000L, // This should be DRAM_BASE
  bootRegAddress: BigInt = 0x1000,
  slaveWhere: TLBusWrapperLocation = PBUS
)
case object BootAddrRegKey extends Field[Option[BootAddrRegParams]](None)

case object BootAddrRegInjector extends SubsystemInjector((p, baseSubsystem) => {
  p(BootAddrRegKey).map { params =>
    implicit val q: Parameters = p
    val tlbus = baseSubsystem.locateTLBusWrapper(params.slaveWhere)
    val device = new SimpleDevice("boot-address-reg", Nil)

    tlbus {
      val node = TLRegisterNode(Seq(AddressSet(params.bootRegAddress, 4096-1)), device, "reg/control", beatBytes=tlbus.beatBytes)
      tlbus.coupleTo("boot-address-reg") { node := TLFragmenter(tlbus, Some("BootAddrReg")) := _ }
      InModuleBody {
        val bootAddrReg = RegInit(params.defaultBootAddress.U(64.W))
        node.regmap(0 -> RegField.bytes(bootAddrReg))
      }
    }
  }
})
