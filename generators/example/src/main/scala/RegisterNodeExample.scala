// DOC include start: MyDeviceController

import chisel3._
import chisel3.util._
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.regmapper._
import freechips.rocketchip.tilelink.TLRegisterNode

class MyDeviceController(implicit p: Parameters) extends LazyModule {
  val device = new SimpleDevice("my-device", Seq("tutorial,my-device0"))
  val node = TLRegisterNode(
    address = Seq(AddressSet(0x10028000, 0xfff)),
    device = device,
    beatBytes = 8,
    concurrency = 1)

  lazy val module = new LazyModuleImp(this) {
    val bigReg = RegInit(0.U(64.W))
    val mediumReg = RegInit(0.U(32.W))
    val smallReg = RegInit(0.U(16.W))

    val tinyReg0 = RegInit(0.U(4.W))
    val tinyReg1 = RegInit(0.U(4.W))

    node.regmap(
      0x00 -> Seq(RegField(64, bigReg)),
      0x08 -> Seq(RegField(32, mediumReg)),
      0x0C -> Seq(RegField(16, smallReg)),
      0x0E -> Seq(
        RegField(4, tinyReg0),
        RegField(4, tinyReg1)))
  }
}

// DOC include end: MyDeviceController

// DOC include start: MyAXI4DeviceController
import freechips.rocketchip.amba.axi4.AXI4RegisterNode

class MyAXI4DeviceController(implicit p: Parameters) extends LazyModule {
  val node = AXI4RegisterNode(
    address = AddressSet(0x10029000, 0xfff),
    beatBytes = 8,
    concurrency = 1)

  lazy val module = new LazyModuleImp(this) {
    val bigReg = RegInit(0.U(64.W))
    val mediumReg = RegInit(0.U(32.W))
    val smallReg = RegInit(0.U(16.W))

    val tinyReg0 = RegInit(0.U(4.W))
    val tinyReg1 = RegInit(0.U(4.W))

    node.regmap(
      0x00 -> Seq(RegField(64, bigReg)),
      0x08 -> Seq(RegField(32, mediumReg)),
      0x0C -> Seq(RegField(16, smallReg)),
      0x0E -> Seq(
        RegField(4, tinyReg0),
        RegField(4, tinyReg1)))
  }
}
// DOC include end: MyAXI4DeviceController

class MyQueueRegisters(implicit p: Parameters) extends LazyModule {
  val device = new SimpleDevice("my-queue", Seq("tutorial,my-queue0"))
  val node = TLRegisterNode(
    address = Seq(AddressSet(0x1002A000, 0xfff)),
    device = device,
    beatBytes = 8,
    concurrency = 1)

  lazy val module = new LazyModuleImp(this) {
// DOC include start: MyQueueRegisters
    // 4-entry 64-bit queue
    val queue = Module(new Queue(UInt(64.W), 4))

    node.regmap(
      0x00 -> Seq(RegField(64, queue.io.deq, queue.io.enq)))
// DOC include end: MyQueueRegisters
  }
}

class MySeparateQueueRegisters(implicit p: Parameters) extends LazyModule {
  val device = new SimpleDevice("my-queue", Seq("tutorial,my-queue1"))
  val node = TLRegisterNode(
    address = Seq(AddressSet(0x1002B000, 0xfff)),
    device = device,
    beatBytes = 8,
    concurrency = 1)

  lazy val module = new LazyModuleImp(this) {
    val queue = Module(new Queue(UInt(64.W), 4))

// DOC include start: MySeparateQueueRegisters
    node.regmap(
      0x00 -> Seq(RegField.r(64, queue.io.deq)),
      0x08 -> Seq(RegField.w(64, queue.io.enq)))
// DOC include end: MySeparateQueueRegisters
  }
}

class MyCounterRegisters(implicit p: Parameters) extends LazyModule {
  val device = new SimpleDevice("my-counters", Seq("tutorial,my-counters0"))
  val node = TLRegisterNode(
    address = Seq(AddressSet(0x1002C000, 0xfff)),
    device = device,
    beatBytes = 8,
    concurrency = 1)

  lazy val module = new LazyModuleImp(this) {
// DOC include start: MyCounterRegisters
    val counter = RegInit(0.U(64.W))

    def readCounter(ready: Bool): (Bool, UInt) = {
      when (ready) { counter := counter - 1.U }
      // (ready, bits)
      (true.B, counter)
    }

    def writeCounter(valid: Bool, bits: UInt): Bool = {
      when (valid) { counter := counter + 1.U }
      // Ignore bits
      // Return ready
      true.B
    }

    node.regmap(
      0x00 -> Seq(RegField.r(64, readCounter(_))),
      0x08 -> Seq(RegField.w(64, writeCounter(_, _))))
// DOC include end: MyCounterRegisters
  }
}

class MyCounterReqRespRegisters(implicit p: Parameters) extends LazyModule {
  val device = new SimpleDevice("my-counters", Seq("tutorial,my-counters1"))
  val node = TLRegisterNode(
    address = Seq(AddressSet(0x1002D000, 0xfff)),
    device = device,
    beatBytes = 8,
    concurrency = 1)

  lazy val module = new LazyModuleImp(this) {
// DOC include start: MyCounterReqRespRegisters
    val counter = RegInit(0.U(64.W))

    def readCounter(ivalid: Bool, oready: Bool): (Bool, Bool, UInt) = {
      val responding = RegInit(false.B)

      when (ivalid && !responding) { responding := true.B }

      when (responding && oready) {
        counter := counter - 1.U
        responding := false.B
      }

      // (iready, ovalid, obits)
      (!responding, responding, counter)
    }

    def writeCounter(ivalid: Bool, oready: Bool, ibits: UInt): (Bool, Bool) = {
      val responding = RegInit(false.B)

      when (ivalid && !responding) { responding := true.B }

      when (responding && oready) {
        counter := counter + 1.U
        responding := false.B
      }

      // (iready, ovalid)
      (!responding, responding)
    }

    node.regmap(
      0x00 -> Seq(RegField.r(64, readCounter(_, _))),
      0x08 -> Seq(RegField.w(64, writeCounter(_, _, _))))
// DOC include end: MyCounterReqRespRegisters
  }
}
