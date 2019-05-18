package beagle

import chisel3._
import chisel3.experimental.{Analog}

class ClockReceiverIO extends Bundle
{
  val VIN = Analog(1.W)
  val VIP = Analog(1.W)
  val VOBUF = Output(Clock())
}

class ClockReceiver extends BlackBox
{
  val io = IO(new ClockReceiverIO)
  override def desiredName = "ClockReceiverVerilogModule"
}

