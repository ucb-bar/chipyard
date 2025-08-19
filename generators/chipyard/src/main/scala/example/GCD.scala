package chipyard.example

import sys.process._

import chisel3._
import chisel3.util._
import chisel3.experimental.{IntParam, BaseModule}
import freechips.rocketchip.amba.axi4._
import freechips.rocketchip.prci._
import freechips.rocketchip.subsystem.{BaseSubsystem, PBUS}
import org.chipsalliance.cde.config.{Parameters, Field, Config}
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.regmapper.{HasRegMap, RegField}
import freechips.rocketchip.tilelink._
import freechips.rocketchip.util._

// DOC include start: GCD params
case class GCDParams(
  address: BigInt = 0x4000,
  width: Int = 32,
  useAXI4: Boolean = false,
  useBlackBox: Boolean = true,
  useHLS: Boolean = false,
  externallyClocked: Boolean = false
) {
  require(!(useAXI4 && useHLS))
}
// DOC include end: GCD params

// DOC include start: GCD key
case object GCDKey extends Field[Option[GCDParams]](None)
// DOC include end: GCD key

class GCDIO(val w: Int) extends Bundle {
  val clock = Input(Clock())
  val reset = Input(Bool())
  val input_ready = Output(Bool())
  val input_valid = Input(Bool())
  val x = Input(UInt(w.W))
  val y = Input(UInt(w.W))
  val output_ready = Input(Bool())
  val output_valid = Output(Bool())
  val gcd = Output(UInt(w.W))
  val busy = Output(Bool())
}

class HLSGCDAccelIO(val w: Int) extends Bundle {
  val ap_clk = Input(Clock())
  val ap_rst = Input(Reset())
  val ap_start = Input(Bool())
  val ap_done = Output(Bool())
  val ap_idle = Output(Bool())
  val ap_ready = Output(Bool())
  val x = Input(UInt(w.W))
  val y = Input(UInt(w.W))
  val ap_return = Output(UInt(w.W))
}

class GCDTopIO extends Bundle {
  val gcd_busy = Output(Bool())
}

trait HasGCDTopIO {
  def io: GCDTopIO
}

// DOC include start: GCD blackbox
class GCDMMIOBlackBox(val w: Int) extends BlackBox(Map("WIDTH" -> IntParam(w))) with HasBlackBoxResource {
  val io = IO(new GCDIO(w))
  addResource("/vsrc/GCDMMIOBlackBox.v")
}
// DOC include end: GCD blackbox

// DOC include start: GCD chisel
class GCDMMIOChiselModule(val w: Int) extends Module {
  val io = IO(new GCDIO(w))
  val s_idle :: s_run :: s_done :: Nil = Enum(3)

  val state = RegInit(s_idle)
  val tmp   = Reg(UInt(w.W))
  val gcd   = Reg(UInt(w.W))

  io.input_ready := state === s_idle
  io.output_valid := state === s_done
  io.gcd := gcd

  when (state === s_idle && io.input_valid) {
    state := s_run
  } .elsewhen (state === s_run && tmp === 0.U) {
    state := s_done
  } .elsewhen (state === s_done && io.output_ready) {
    state := s_idle
  }

  when (state === s_idle && io.input_valid) {
    gcd := io.x
    tmp := io.y
  } .elsewhen (state === s_run) {
    when (gcd > tmp) {
      gcd := gcd - tmp
    } .otherwise {
      tmp := tmp - gcd
    }
  }

  io.busy := state =/= s_idle
}
// DOC include end: GCD chisel

// DOC include start: HLS blackbox
class HLSGCDAccelBlackBox(val w: Int) extends BlackBox with HasBlackBoxPath {
  val io = IO(new HLSGCDAccelIO(w))

  val chipyardDir = System.getProperty("user.dir")
  val hlsDir = s"$chipyardDir/generators/chipyard"
  
  // Run HLS command
  val make = s"make -C ${hlsDir}/src/main/resources/hls default"
  require (make.! == 0, "Failed to run HLS")

  // Add each vlog file
  addPath(s"$hlsDir/src/main/resources/vsrc/HLSGCDAccelBlackBox.v")
  addPath(s"$hlsDir/src/main/resources/vsrc/HLSGCDAccelBlackBox_flow_control_loop_pipe.v")
}
// DOC include end: HLS blackbox

// DOC include start: GCD router
class GCDTL(params: GCDParams, beatBytes: Int)(implicit p: Parameters) extends ClockSinkDomain(ClockSinkParameters())(p) {
  val device = new SimpleDevice("gcd", Seq("ucbbar,gcd")) 
  val node = TLRegisterNode(Seq(AddressSet(params.address, 4096-1)), device, "reg/control", beatBytes=beatBytes)

  override lazy val module = new GCDImpl
  class GCDImpl extends Impl with HasGCDTopIO {
    val io = IO(new GCDTopIO)
    withClockAndReset(clock, reset) {
      // How many clock cycles in a PWM cycle?
      val x = Reg(UInt(params.width.W))
      val y = Wire(new DecoupledIO(UInt(params.width.W)))
      val gcd = Wire(new DecoupledIO(UInt(params.width.W)))
      val status = Wire(UInt(2.W))

      val impl_io = if (params.useBlackBox) {
        val impl = Module(new GCDMMIOBlackBox(params.width))
        impl.io
      } else {
        val impl = Module(new GCDMMIOChiselModule(params.width))
        impl.io
      }

      impl_io.clock := clock
      impl_io.reset := reset.asBool

      impl_io.x := x
      impl_io.y := y.bits
      impl_io.input_valid := y.valid
      y.ready := impl_io.input_ready

      gcd.bits := impl_io.gcd
      gcd.valid := impl_io.output_valid
      impl_io.output_ready := gcd.ready

      status := Cat(impl_io.input_ready, impl_io.output_valid)
      io.gcd_busy := impl_io.busy

// DOC include start: GCD instance regmap
      node.regmap(
        0x00 -> Seq(
          RegField.r(2, status)), // a read-only register capturing current status
        0x04 -> Seq(
          RegField.w(params.width, x)), // a plain, write-only register
        0x08 -> Seq(
          RegField.w(params.width, y)), // write-only, y.valid is set on write
        0x0C -> Seq(
          RegField.r(params.width, gcd))) // read-only, gcd.ready is set on read
// DOC include end: GCD instance regmap
    }
  }
}

class GCDAXI4(params: GCDParams, beatBytes: Int)(implicit p: Parameters) extends ClockSinkDomain(ClockSinkParameters())(p) {
  val node = AXI4RegisterNode(AddressSet(params.address, 4096-1), beatBytes=beatBytes)
  override lazy val module = new GCDImpl
  class GCDImpl extends Impl with HasGCDTopIO {
    val io = IO(new GCDTopIO)
    withClockAndReset(clock, reset) {
      // How many clock cycles in a PWM cycle?
      val x = Reg(UInt(params.width.W))
      val y = Wire(new DecoupledIO(UInt(params.width.W)))
      val gcd = Wire(new DecoupledIO(UInt(params.width.W)))
      val status = Wire(UInt(2.W))

      val impl_io = if (params.useBlackBox) {
        val impl = Module(new GCDMMIOBlackBox(params.width))
        impl.io
      } else {
        val impl = Module(new GCDMMIOChiselModule(params.width))
        impl.io
      }

      impl_io.clock := clock
      impl_io.reset := reset.asBool

      impl_io.x := x
      impl_io.y := y.bits
      impl_io.input_valid := y.valid
      y.ready := impl_io.input_ready

      gcd.bits := impl_io.gcd
      gcd.valid := impl_io.output_valid
      impl_io.output_ready := gcd.ready

      status := Cat(impl_io.input_ready, impl_io.output_valid)
      io.gcd_busy := impl_io.busy

      node.regmap(
        0x00 -> Seq(
          RegField.r(2, status)), // a read-only register capturing current status
        0x04 -> Seq(
          RegField.w(params.width, x)), // a plain, write-only register
        0x08 -> Seq(
          RegField.w(params.width, y)), // write-only, y.valid is set on write
        0x0C -> Seq(
          RegField.r(params.width, gcd))) // read-only, gcd.ready is set on read
    }
  }
}
// DOC include end: GCD router

class HLSGCDAccel(params: GCDParams, beatBytes: Int)(implicit p: Parameters) extends ClockSinkDomain(ClockSinkParameters())(p) {
  val device = new SimpleDevice("hlsgcdaccel", Seq("ucbbar,hlsgcdaccel")) 
  val node = TLRegisterNode(Seq(AddressSet(params.address, 4096-1)), device, "reg/control", beatBytes=beatBytes)

  override lazy val module = new HLSGCDAccelImpl
  class HLSGCDAccelImpl extends Impl with HasGCDTopIO {
    val io = IO(new GCDTopIO)
    withClockAndReset(clock, reset) {
      val x = Reg(UInt(params.width.W))
      val y = Wire(new DecoupledIO(UInt(params.width.W)))
      val y_reg = Reg(UInt(params.width.W))
      val gcd = Wire(new DecoupledIO(UInt(params.width.W)))
      val gcd_reg = Reg(UInt(params.width.W))
      val status = Wire(UInt(2.W))

      val impl = Module(new HLSGCDAccelBlackBox(params.width))

      impl.io.ap_clk := clock
      impl.io.ap_rst := reset

      val s_idle :: s_busy :: Nil = Enum(2)
      val state = RegInit(s_idle)
      val result_valid = RegInit(false.B)
      when (state === s_idle && y.valid) { 
        state := s_busy
        result_valid := false.B
        y_reg := y.bits 
      } .elsewhen (state === s_busy && impl.io.ap_done) {
        state := s_idle
        result_valid := true.B
        gcd_reg := impl.io.ap_return
      }

      impl.io.ap_start := state === s_busy

      gcd.valid := result_valid
      status := Cat(impl.io.ap_idle, result_valid)
      
      impl.io.x := x
      impl.io.y := y_reg
      y.ready := impl.io.ap_idle
      gcd.bits := gcd_reg

      io.gcd_busy := !impl.io.ap_idle

      node.regmap(
        0x00 -> Seq(
          RegField.r(2, status)), // a read-only register capturing current status
        0x04 -> Seq(
          RegField.w(params.width, x)), // a plain, write-only register
        0x08 -> Seq(
          RegField.w(params.width, y)), // write-only, y.valid is set on write
        0x0C -> Seq(
          RegField.r(params.width, gcd))) // read-only, gcd.ready is set on read
    }
  }
}

// DOC include start: GCD lazy trait
trait CanHavePeripheryGCD { this: BaseSubsystem =>
  private val portName = "gcd"

  private val pbus = locateTLBusWrapper(PBUS)

  // Only build if we are using the TL (nonAXI4) version
  val (gcd_busy, gcd_clock) = p(GCDKey) match {
    case Some(params) => {

      // If externallyClocked is true, create an input port for the GCD clock.
      // This clock is distinct from the pbus clock or other internal clocks.
      // It's defined within InModuleBody as it's a hardware port.
      val gcd_clock = Option.when(params.externallyClocked) {
        InModuleBody { IO(Input(Clock())).suggestName("gcd_clock_in") }
      }
      // Define the clock source node for the GCD module.
      val gcdClockNode = if (params.externallyClocked) {
        // If externally clocked, create a new ClockSourceNode. 
        // This node acts as the root of the GCD's independent clock domain.
        val gcdSourceClockNode = ClockSourceNode(Seq(ClockSourceParameters()))
        InModuleBody {
          // Connect the ClockSourceNode's output clock to the external gcd_clock input.
          gcdSourceClockNode.out(0)._1.clock := gcd_clock.get
          // The reset signal for the GCD's clock domain must be synchronous to the gcd_clock.
          // ResetCatchAndSync synchronizes the asynchronous pbus reset to the gcd_clock domain.
          gcdSourceClockNode.out(0)._1.reset := ResetCatchAndSync(gcd_clock.get, pbus.module.reset.asBool)
        }
        gcdSourceClockNode
      } else {
        // If not externally clocked, the GCD runs on the same clock as the pbus.
        pbus.fixedClockNode
      }
      // Define the type of clock crossing required between the pbus and the GCD module.
      val gcdCrossing = if (params.externallyClocked) {
        // If the GCD has its own clock, an AsynchronousCrossing is necessary
        // to safely transfer data between the pbus clock domain and the GCD clock domain.
        AsynchronousCrossing()
      } else {
        // If the GCD uses the pbus clock, a SynchronousCrossing can be used.
        SynchronousCrossing()
      }

      // Instantiate the GCD module (either TL, AXI4, or HLS variant)
      val gcd = if (params.useAXI4) {
        val gcd = LazyModule(new GCDAXI4(params, pbus.beatBytes)(p))
        // Connect the GCD's clock input to our determined gcdClockNode.
        gcd.clockNode := gcdClockNode
        // Couple the GCD to the pbus, inserting the necessary clock crossing logic.
        pbus.coupleTo(portName) {
          // AXI4InwardClockCrossingHelper handles crossing details for AXI4.
          AXI4InwardClockCrossingHelper("gcd_crossing", gcd, gcd.node)(gcdCrossing) :=
          AXI4Buffer () :=
          TLToAXI4 () :=
          // toVariableWidthSlave doesn't use holdFirstDeny, which TLToAXI4() needs
          TLFragmenter(pbus.beatBytes, pbus.blockBytes, holdFirstDeny = true) := _
        }
        gcd
      } else if (params.useHLS) {
        val gcd = LazyModule(new HLSGCDAccel(params, pbus.beatBytes)(p))
        // Connect the GCD's clock input to our determined gcdClockNode.
        gcd.clockNode := gcdClockNode
        // Couple the GCD to the pbus, inserting the necessary clock crossing logic.
        pbus.coupleTo(portName) {
          // TLInwardClockCrossingHelper handles crossing details for TileLink.
          TLInwardClockCrossingHelper("gcd_crossing", gcd, gcd.node)(gcdCrossing) :=
          TLFragmenter(pbus.beatBytes, pbus.blockBytes) := _
        }
        gcd
      } else {
        val gcd = LazyModule(new GCDTL(params, pbus.beatBytes)(p))
        // Connect the GCD's clock input to our determined gcdClockNode.
        gcd.clockNode := gcdClockNode
        // Couple the GCD to the pbus, inserting the necessary clock crossing logic.
        pbus.coupleTo(portName) {
          // TLInwardClockCrossingHelper handles crossing details for TileLink.
          TLInwardClockCrossingHelper("gcd_crossing", gcd, gcd.node)(gcdCrossing) :=
          TLFragmenter(pbus.beatBytes, pbus.blockBytes) := _
        }
        gcd
      }
      // Expose the GCD's busy signal.
      val gcd_busy = InModuleBody {
        val busy = IO(Output(Bool())).suggestName("gcd_busy")
        busy := gcd.module.io.gcd_busy
        busy
      }
      // Return the busy signal (always needed if GCD exists) and the optional external clock input.
      // The Option[Clock] allows the IOBinder (WithGCDIOPunchthrough) to conditionally
      // create the top-level clock input only when `externallyClocked` is true.
      // The busy signal is Some(busy) because the entire GCD peripheral itself is optional based on GCDKey.
      (Some(gcd_busy), gcd_clock)
    }
    // If GCDKey is None, the GCD peripheral is not instantiated. Return None for both signals.
    case None => (None, None)
  }
}
// DOC include end: GCD lazy trait

// DOC include start: GCD config fragment
class WithGCD(useAXI4: Boolean = false, useBlackBox: Boolean = false, useHLS: Boolean = false, externallyClocked: Boolean = false) extends Config((site, here, up) => {
  case GCDKey => {
    // useHLS cannot be used with useAXI4 and useBlackBox
    assert(!useHLS || (useHLS && !useAXI4 && !useBlackBox))
    Some(GCDParams(useAXI4 = useAXI4, useBlackBox = useBlackBox, useHLS = useHLS, externallyClocked = externallyClocked))
  }
})
// DOC include end: GCD config fragment
