package cordic
import chisel3._
import chisel3.util._
import chisel3.experimental.{IntParam, BaseModule}

import chisel3.util.HasBlackBoxResource
import freechips.rocketchip.amba.axi4._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.regmapper.{HasRegMap, RegField}
import freechips.rocketchip.tilelink._
import freechips.rocketchip.subsystem.BaseSubsystem
import org.chipsalliance.cde.config.{Parameters, Field, Config}
import freechips.rocketchip.tilelink.TLRegisterNode
import freechips.rocketchip.util.UIntIsOneOf

case class CORDICParams(
  address: BigInt = 0x4000, 
  width: Int = 32,
  useAXI4: Boolean = false,
  useBlackBox: Boolean = true)

case object CORDICKey extends Field[Option[CORDICParams]](None)

class CORDICIO(val w:Int)extends Bundle {
    val clock         = Input(Clock())
    val reset         = Input(Bool())
    val input_ready   = Output(Bool())
    val input_valid   = Input(Bool())
    val theta         = Input(UInt(w.W))
    val output_ready  = Input(Bool())
    val output_valid  = Output(Bool())
    val sin_cos_theta = Output(UInt(w.W))
    val busy          = Output(Bool())
}

trait CORDICTopIO extends Bundle {
  val cordic_busy = Output(Bool())
}

trait HasCORDICIO extends BaseModule {
  val w: Int////////***
  val io = IO(new CORDICIO(w))
}

class CORDICMMIOBlackBox(val w: Int) extends BlackBox(Map("WIDTH" -> IntParam(w))) with HasBlackBoxResource
  with HasCORDICIO
{
  addResource("/vsrc/CORDICMMIOBlackBox.v")
}
// DOC include end: cordic blackbox

trait CORDICModule extends HasRegMap {

  val io: CORDICTopIO
  implicit val p: Parameters
  
  def params: CORDICParams
  val clock: Clock
  val reset: Reset
  val theta = Wire(new DecoupledIO(UInt(params.width.W)))
  val sin_cos_theta = Wire(Flipped(DecoupledIO(UInt(params.width.W))))

  val status = Wire(UInt(2.W))
  val impl = Module(new CORDICMMIOBlackBox(params.width))

  impl.io.clock := clock
  impl.io.reset := reset.asBool
  impl.io.theta := theta.bits
  impl.io.input_valid := theta.valid
  theta.ready := impl.io.input_ready

  sin_cos_theta.bits := impl.io.sin_cos_theta

  sin_cos_theta.valid := impl.io.output_valid

  impl.io.output_ready := sin_cos_theta.ready 

  status := Cat(impl.io.input_ready, impl.io.output_valid)
  io.cordic_busy := impl.io.busy
  regmap(
    0x00 -> Seq(
       RegField.r(2, status)),
    0x04 -> Seq(
      RegField.w(params.width, theta)
    ),
    0x08 -> Seq(
      RegField.r(params.width, sin_cos_theta)
    )
  )
}

class CORDICTL(params: CORDICParams, beatBytes: Int)(implicit p: Parameters)
    extends TLRegisterRouter(
        params.address , "cordic", Seq("ucbbar,cordic"), beatBytes = beatBytes)(
        new TLRegBundle(params, _)with CORDICTopIO)(
        new TLRegModule(params, _,_) with CORDICModule)

class CORDICAXI4(params: CORDICParams, beatBytes: Int)(implicit p: Parameters)
    extends AXI4RegisterRouter(
        params.address, 
        beatBytes = beatBytes)(
            new AXI4RegBundle(params, _)with CORDICTopIO)(
            new AXI4RegModule(params, _,_) with CORDICModule)
        
trait CanHavePeripheryCORDIC { this: BaseSubsystem =>
    private val portName = "cordic"
    val cordic_busy = p(CORDICKey) match {
        case Some(params) => {
            val cordic = if(params.useAXI4){
                val cordic = pbus {LazyModule(new CORDICAXI4(params, pbus.beatBytes)(p))}
                pbus.coupleTo(portName) { 
                    cordic.node := 
                    AXI4Buffer () :=
                    TLToAXI4 () :=
                    // toVariableWidthSlave doesn't use holdFirstDeny, which TLToAXI4() needsx
                    TLFragmenter(pbus.beatBytes, pbus.blockBytes, holdFirstDeny = true) := _
                }
                cordic
            } else {
                val cordic = pbus {LazyModule(new CORDICTL(params, pbus.beatBytes)(p))}
                pbus.coupleTo(portName) { 
                    cordic.node := TLFragmenter(pbus.beatBytes, pbus.blockBytes) := _ 
                }
                cordic
            }
            val pbus_io = pbus {
                 InModuleBody {
                    val busy = IO(Output(Bool()))
                    busy := cordic.module.io.cordic_busy
                    busy
                 }
            }
            val cordic_busy = InModuleBody {
                val busy = IO(Output(Bool())).suggestName("cordic_busy")
                busy := pbus_io
                busy
            }
            Some(cordic_busy)
        }
        case None => None
    }
  
}
class WithCORDIC(useAXI4: Boolean = false, useBlackBox: Boolean = true) extends Config((site, here, up) => {
  case CORDICKey => Some(CORDICParams(useAXI4 = useAXI4, useBlackBox = useBlackBox))
})
