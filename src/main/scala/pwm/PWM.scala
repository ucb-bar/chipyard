package pwm

import chisel3._
import chisel3.util._
import cde.{Parameters, Field}
import uncore.tilelink._
import junctions._
import diplomacy._
import rocketchip._

class PWMBase extends Module {
  val io = IO(new Bundle {
    val pwmout = Output(Bool())
    val period = Input(UInt(64.W))
    val duty = Input(UInt(64.W))
    val enable = Input(Bool())
  })

  // The counter should count up until period is reached
  val counter = Reg(UInt(64.W))

  when (counter >= (io.period - 1.U)) {
    counter := 0.U
  } .otherwise {
    counter := counter + 1.U
  }

  // If PWM is enabled, pwmout is high when counter < duty
  // If PWM is not enabled, it will always be low
  io.pwmout := io.enable && (counter < io.duty)
}

class PWMTL(implicit p: Parameters) extends Module {
  val io = IO(new Bundle {
    val pwmout = Output(Bool())
    val tl = Flipped(new ClientUncachedTileLinkIO())
  })

  // How many clock cycles in a PWM cycle?
  val period = Reg(UInt(64.W))
  // For how many cycles should the clock be high?
  val duty = Reg(UInt(64.W))
  // Is the PWM even running at all?
  val enable = Reg(init = false.B)

  val base = Module(new PWMBase)
  io.pwmout := base.io.pwmout
  base.io.period := period
  base.io.duty := duty
  base.io.enable := enable

  // One entry queue to hold the acquire message
  val acq = Queue(io.tl.acquire, 1)

  // We assume that the TileLink interface is 64 bits wide
  require(io.tl.tlDataBits == 64)

  // Then addr_block and addr_beat together form the word address
  val full_addr = Cat(acq.bits.addr_block, acq.bits.addr_beat)
  // Since we have 3 registers, we only need the lower two bits to distinguish them
  val index = full_addr(1, 0)

  // Make sure the acquires we get are only the types we expect
  assert(!acq.valid ||
    acq.bits.isBuiltInType(Acquire.getType) ||
    acq.bits.isBuiltInType(Acquire.putType))

  // Make sure write masks are full
  assert(!acq.valid || !acq.bits.hasData() ||
    acq.bits.wmask() === Acquire.fullWriteMask)

  // Base the grant on the stored acquire
  io.tl.grant.valid := acq.valid
  acq.ready := io.tl.grant.ready
  io.tl.grant.bits := Grant(
    is_builtin_type = true.B,
    g_type = acq.bits.getBuiltInGrantType(),
    client_xact_id = acq.bits.client_xact_id,
    manager_xact_id = 0.U,
    addr_beat = acq.bits.addr_beat,
    // For gets, map the index to the three registers
    data = MuxLookup(index, 0.U, Seq(
      0.U -> period,
      1.U -> duty,
      2.U -> enable)))

  // If this is a put, update the registers according to the index
  when (acq.fire() && acq.bits.hasData()) {
    switch (index) {
      is (0.U) { period := acq.bits.data }
      is (1.U) { duty   := acq.bits.data }
      is (2.U) { enable := acq.bits.data(0) }
    }
  }
}

class PWMAXI(implicit p: Parameters) extends Module {
  val io = IO(new Bundle {
    val pwmout = Output(Bool())
    val axi = Flipped(new NastiIO())
  })

  // How many clock cycles in a PWM cycle?
  val period = Reg(UInt(64.W))
  // For how many cycles should the clock be high?
  val duty = Reg(UInt(64.W))
  // Is the PWM even running at all?
  val enable = Reg(init = false.B)

  val base = Module(new PWMBase)
  io.pwmout := base.io.pwmout
  base.io.period := period
  base.io.duty := duty
  base.io.enable := enable

  val ar = Queue(io.axi.ar, 1)
  val aw = Queue(io.axi.aw, 1)
  val w = Queue(io.axi.w, 1)

  // Start from 3rd bit since 64-bit words
  // Only need 2 bits, since 3 registers
  val read_index = ar.bits.addr(4, 3)
  val write_index = aw.bits.addr(4, 3)

  io.axi.r.valid := ar.valid
  ar.ready := io.axi.r.ready
  io.axi.r.bits := NastiReadDataChannel(
    id = ar.bits.id,
    data = MuxLookup(read_index, 0.U, Seq(
      0.U -> period,
      1.U -> duty,
      2.U -> enable)))

  io.axi.b.valid := aw.valid && w.valid
  aw.ready := io.axi.b.ready && w.valid
  w.ready := io.axi.b.ready && aw.valid
  io.axi.b.bits := NastiWriteResponseChannel(id = aw.bits.id)

  when (io.axi.b.fire()) {
    switch (write_index) {
      is (0.U) { period := w.bits.data }
      is (1.U) { duty   := w.bits.data }
      is (2.U) { enable := w.bits.data(0) }
    }
  }

  require(io.axi.w.bits.nastiXDataBits == 64)

  assert(!io.axi.ar.valid || (io.axi.ar.bits.len === 0.U && io.axi.ar.bits.size === 3.U))
  assert(!io.axi.aw.valid || (io.axi.aw.bits.len === 0.U && io.axi.aw.bits.size === 3.U))
  assert(!io.axi.w.valid || PopCount(io.axi.w.bits.strb) === 8.U)
}

trait PeripheryPWM extends LazyModule {
  val pDevices: ResourceManager[AddrMapEntry]

  pDevices.add(AddrMapEntry("pwm", MemSize(4096, MemAttr(AddrMapProt.RW))))
}

trait PeripheryPWMBundle {
  val pwmout = Output(Bool())
}

case object BuildPWM extends Field[(ClientUncachedTileLinkIO, Parameters) => Bool]

trait PeripheryPWMModule extends HasPeripheryParameters {
  implicit val p: Parameters
  val pBus: TileLinkRecursiveInterconnect
  val io: PeripheryPWMBundle

  io.pwmout := p(BuildPWM)(pBus.port("pwm"), outerMMIOParams)
}
