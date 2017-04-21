package pwm

import chisel3._
import chisel3.util._
import config.{Parameters, Field}
import uncore.tilelink._
import uncore.tilelink2._
import junctions._
import diplomacy._
import rocketchip._
import _root_.util.UIntIsOneOf

class PWMBase(w: Int) extends Module {
  val io = IO(new Bundle {
    val pwmout = Output(Bool())
    val period = Input(UInt(w.W))
    val duty = Input(UInt(w.W))
    val enable = Input(Bool())
  })

  // The counter should count up until period is reached
  val counter = Reg(UInt(w.W))

  when (counter >= (io.period - 1.U)) {
    counter := 0.U
  } .otherwise {
    counter := counter + 1.U
  }

  // If PWM is enabled, pwmout is high when counter < duty
  // If PWM is not enabled, it will always be low
  io.pwmout := io.enable && (counter < io.duty)
}

class PWMTL(address: AddressSet, beatBytes: Int)(implicit p: Parameters) extends LazyModule {
  val node = TLManagerNode(Seq(TLManagerPortParameters(
    Seq(TLManagerParameters(
      address            = List(address),
      regionType         = RegionType.PUT_EFFECTS,
      executable         = false,
      supportsGet        = TransferSizes(1, beatBytes),
      supportsPutFull    = TransferSizes(1, beatBytes),
      fifoId             = Some(0))), // requests are handled in order
    beatBytes = beatBytes,
    minLatency = 1)))

  lazy val module = new PWMTLModule(this, beatBytes)
}

class PWMTLModule(outer: PWMTL, beatBytes: Int) extends LazyModuleImp(outer) {
  val io = IO(new Bundle {
    val pwmout = Output(Bool())
    val tl = outer.node.bundleIn
  })

  val w = beatBytes * 8
  // How many clock cycles in a PWM cycle?
  val period = Reg(UInt(w.W))
  // For how many cycles should the clock be high?
  val duty = Reg(UInt(w.W))
  // Is the PWM even running at all?
  val enable = Reg(init = false.B)

  val base = Module(new PWMBase(w))
  io.pwmout := base.io.pwmout
  base.io.period := period
  base.io.duty := duty
  base.io.enable := enable

  val tl = io.tl.head

  tl.b.valid := false.B
  tl.c.ready := false.B
  tl.e.ready := false.B

  // One entry queue to hold the acquire message
  val acq = Queue(tl.a, 1)

  // We have 3 32-bit registers
  val index = acq.bits.address(3, 2)

  val edge = outer.node.edgesIn(0)
  val hasData = edge.hasData(acq.bits)

  // Base the grant on the stored acquire
  tl.d.valid := acq.valid
  acq.ready := tl.d.ready
  tl.d.bits := edge.AccessAck(acq.bits, 0.U)
  tl.d.bits.opcode := Mux(hasData, TLMessages.AccessAck, TLMessages.AccessAckData)
  tl.d.bits.data := MuxLookup(index, 0.U,
    Seq(
      0.U -> period,
      1.U -> duty,
      2.U -> enable))
  tl.d.bits.error := index > 2.U

  // If this is a put, update the registers according to the index
  when (acq.fire() && acq.bits.opcode === TLMessages.PutFullData) {
    switch (index) {
      is (0.U) { period := acq.bits.data }
      is (1.U) { duty   := acq.bits.data }
      is (2.U) { enable := acq.bits.data(0) }
    }
  }
}

trait PeripheryPWM extends LazyModule with HasPeripheryParameters {
  implicit val p: Parameters
  val peripheryBus: TLXbar

  private val address = AddressSet(0x2000, 0xfff)
  val pwm = LazyModule(new PWMTL(address, peripheryBusConfig.beatBytes)(p))
  pwm.node := TLFragmenter(
    peripheryBusConfig.beatBytes, cacheBlockBytes)(peripheryBus.node)
}

trait PeripheryPWMBundle {
  val pwmout = Output(Bool())
}

trait PeripheryPWMModule extends HasPeripheryParameters {
  implicit val p: Parameters
  val io: PeripheryPWMBundle
  val outer: PeripheryPWM

  io.pwmout := outer.pwm.module.io.pwmout
}
