
package chipyard.clocking

import chisel3._
import chisel3.experimental.{IO}
import freechips.rocketchip.config.{Parameters, Field}
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.devices.tilelink._
import freechips.rocketchip.regmapper._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.util._
import freechips.rocketchip.tile._
import freechips.rocketchip.prci.{ClockSinkDomain, ClockGroupIdentityNode}

case class TemperatureSensorParams(
  address: BigInt=0x102000,
  tempWidth: Int = 8,
  slaveWhere: TLBusWrapperLocation = PBUS) {
  def widthBytes: Int = (tempWidth + 7) / 8
}

case object TemperatureSensorParams extends Field[Option[TemperatureSensorParams]](None)

trait CanHaveTemperatureSensor { self: BaseSubsystem =>
  val tempSensorOpt = p(TemperatureSensorParams) map { params =>
    val tlbus = locateTLBusWrapper(params.slaveWhere)
    val sensor = LazyModule(new TemperatureSensor(params))
    sensor.clockNode := tlbus.fixedClockNode
    tlbus.toFixedWidthSingleBeatSlave(params.widthBytes, Some("tempsense0"))(sensor.node)
    val tempIn = BundleBridgeSource(Some(() => UInt(params.tempWidth.W)))
    sensor.tempIn := tempIn
    InModuleBody { tempIn.makeIO() }
  }
}

class TemperatureSensor(val params: TemperatureSensorParams)(implicit p: Parameters) extends ClockSinkDomain {
  val device = new SimpleDevice("temp-sensor", Seq("ucbbar,temp-sensor0"))
  val node = TLRegisterNode(Seq(AddressSet(params.address, 63)), device, "reg/control", beatBytes=params.widthBytes)
  val tempIn = BundleBridgeSink(Some(() => UInt(params.tempWidth.W)))
  InModuleBody {
    val temp = RegNext(tempIn.bundle)
    node.regmap(0 -> Seq(RegField.r(params.tempWidth, RegReadFn(temp))))
    tempIn
  }
}
