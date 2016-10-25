package pwm

import chisel3._
import chisel3.util._
import cde.Parameters
import uncore.tilelink._
import junctions._
import diplomacy._
import rocketchip._

class PWM(implicit p: Parameters) extends Module {
  val io = new Bundle {
    val pwmout = Bool(OUTPUT)
    val tl = (new ClientUncachedTileLinkIO).flip
  }

  // How many clock cycles in a PWM cycle?
  val period = Reg(UInt(width = 64))
  // For how many cycles should the clock be high?
  val duty = Reg(UInt(width = 64))
  // Is the PWM even running at all?
  val enable = Reg(init = Bool(false))

  // The counter should count up until period is reached
  val counter = Reg(UInt(width = 64))

  when (counter >= period) {
    counter := UInt(0)
  } .otherwise {
    counter := counter + UInt(1)
  }

  // If PWM is enabled, pwmout is high when counter < duty
  // If PWM is not enabled, it will always be low
  io.pwmout := enable && (counter < duty)

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
    acq.bits.isBuiltInType(Acquire.putType),
    "PWM: unexpected acquire type")

  // Base the grant on the stored acquire
  io.tl.grant.valid := acq.valid
  acq.ready := io.tl.grant.ready
  io.tl.grant.bits := Grant(
    is_builtin_type = Bool(true),
    g_type = acq.bits.getBuiltInGrantType(),
    client_xact_id = acq.bits.client_xact_id,
    manager_xact_id = UInt(0),
    addr_beat = acq.bits.addr_beat,
    // For gets, map the index to the three registers
    data = MuxLookup(index, UInt(0), Seq(
      UInt(0) -> period,
      UInt(1) -> duty,
      UInt(2) -> enable)))

  // If this is a put, update the registers according to the index
  when (acq.fire() && acq.bits.hasData()) {
    switch (index) {
      is (UInt(0)) { period := acq.bits.data }
      is (UInt(1)) { duty   := acq.bits.data }
      is (UInt(2)) { enable := acq.bits.data(0) }
    }
  }
}

trait PeripheryPWM extends LazyModule {
  val pDevices: ResourceManager[AddrMapEntry]

  pDevices.add(AddrMapEntry("pwm", MemSize(4096, MemAttr(AddrMapProt.RW))))
}

trait PeripheryPWMBundle {
  val pwmout = Bool(OUTPUT)
}

trait PeripheryPWMModule extends HasPeripheryParameters {
  implicit val p: Parameters
  val pBus: TileLinkRecursiveInterconnect
  val io: PeripheryPWMBundle

  val pwm = Module(new PWM()(outerMMIOParams))
  pwm.io.tl <> pBus.port("pwm")
  io.pwmout := pwm.io.pwmout
}
