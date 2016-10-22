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

  val period = Reg(UInt(width = 64))
  val duty = Reg(UInt(width = 64))
  val enable = Reg(init = Bool(false))
  val counter = Reg(UInt(width = 64))

  when (counter >= period) {
    counter := UInt(0)
  } .otherwise {
    counter := counter + UInt(1)
  }

  io.pwmout := enable && (counter < duty)

  val acq = Queue(io.tl.acquire, 1)
  val addr = Cat(acq.bits.addr_block, acq.bits.addr_beat)

  io.tl.grant.valid := acq.valid
  acq.ready := io.tl.grant.ready
  io.tl.grant.bits := Grant(
    is_builtin_type = Bool(true),
    g_type = acq.bits.getBuiltInGrantType(),
    client_xact_id = acq.bits.client_xact_id,
    manager_xact_id = UInt(0),
    addr_beat = acq.bits.addr_beat,
    data = MuxLookup(addr, UInt(0), Seq(
      UInt(0) -> period,
      UInt(1) -> duty,
      UInt(2) -> enable)))

  when (acq.fire() && acq.bits.hasData()) {
    switch (addr) {
      is (UInt(0)) { period := acq.bits.data }
      is (UInt(1)) { duty   := acq.bits.data }
      is (UInt(2)) { enable := acq.bits.data(0) }
    }
  }

  require(io.tl.tlDataBits == 64)
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
