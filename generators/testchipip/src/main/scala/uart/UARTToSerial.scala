package testchipip.uart

import chisel3._
import chisel3.util._
import sifive.blocks.devices.uart._
import testchipip.serdes.{SerialIO}

class UARTToSerial(freqHz: BigInt, uartParams: UARTParams) extends Module {
  val io = IO(new Bundle {
    val uart = new UARTPortIO(uartParams)
    val serial = new SerialIO(8)
    val dropped = Output(Bool()) // No flow control, so dropping a beat means we're screwed
  })

  val rxm = Module(new UARTRx(uartParams))
  val rxq = Module(new Queue(UInt(uartParams.dataBits.W), uartParams.nRxEntries))
  val txm = Module(new UARTTx(uartParams))
  val txq = Module(new Queue(UInt(uartParams.dataBits.W), uartParams.nTxEntries))

  val div = (freqHz / uartParams.initBaudRate).toInt

  val dropped = RegInit(false.B)
  io.dropped := dropped
  rxm.io.en := true.B
  rxm.io.in := io.uart.rxd
  rxm.io.div := div.U
  rxq.io.enq.valid := rxm.io.out.valid
  rxq.io.enq.bits := rxm.io.out.bits
  when (rxq.io.enq.valid) { assert(rxq.io.enq.ready) }
  when (rxq.io.enq.valid && !rxq.io.enq.ready) { dropped := true.B } // no flow control
  //dontTouch(rxm.io)

  txm.io.en := true.B
  txm.io.in <> txq.io.deq
  txm.io.div := div.U
  txm.io.nstop := 0.U
  io.uart.txd := txm.io.out
  //dontTouch(txm.io)

  io.serial.out <> rxq.io.deq
  txq.io.enq <> io.serial.in
}
