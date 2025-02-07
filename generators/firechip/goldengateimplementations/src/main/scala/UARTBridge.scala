// See LICENSE for license details

package firechip.goldengateimplementations

import chisel3._
import chisel3.util._

import org.chipsalliance.cde.config.Parameters

import midas.widgets._
import firesim.lib.bridgeutils._

import firechip.bridgeinterfaces._

//Note: This file is heavily commented as it serves as a bridge walkthrough
//example in the FireSim docs

// DOC include start: UART Bridge Header
// Our UARTBridgeModule definition, note:
// 1) it takes one parameter, key, of type UARTKey --> the same case class we captured from the target-side
// 2) It accepts one implicit parameter of type Parameters
// 3) It extends BridgeModule passing the type of the HostInterface
//
// While the Scala type system will check if you parameterized BridgeModule
// correctly, the types of the constructor arugument (in this case UARTKey),
// don't match, you'll only find out later when Golden Gate attempts to generate your module.
class UARTBridgeModule(key: UARTKey)(implicit p: Parameters) extends BridgeModule[HostPortIO[UARTBridgeTargetIO]]()(p) {
  lazy val module = new BridgeModuleImp(this) {
    val div = key.div
    // This creates the interfaces for all of the host-side transport
    // AXI4-lite for the simulation control bus, =
    // AXI4 for DMA
    val io = IO(new WidgetIO())

    // This creates the host-side interface of your TargetIO
    val hPort = IO(HostPort(new UARTBridgeTargetIO))

    // Generate some FIFOs to capture tokens...
    val txfifo = Module(new Queue(UInt(8.W), 128))
    val rxfifo = Module(new Queue(UInt(8.W), 128))

    val target = hPort.hBits.uart
    // In general, your BridgeModule will not need to do work every host-cycle. In simple Bridges,
    // we can do everything in a single host-cycle -- fire captures all of the
    // conditions under which we can consume and input token and produce a new
    // output token
    val fire = hPort.toHost.hValid && // We have a valid input token: toHost ~= leaving the transformed RTL
               hPort.fromHost.hReady && // We have space to enqueue a new output token
               txfifo.io.enq.ready      // We have space to capture new TX data
    val targetReset = fire & hPort.hBits.reset
    rxfifo.reset := reset.asBool || targetReset
    txfifo.reset := reset.asBool || targetReset

    hPort.toHost.hReady := fire
    hPort.fromHost.hValid := fire
    // DOC include end: UART Bridge Header
    val sTxIdle :: sTxWait :: sTxData :: sTxBreak :: Nil = Enum(4)
    val txState = RegInit(sTxIdle)
    val txData = Reg(UInt(8.W))
    // iterate through bits in byte to deserialize
    val (txDataIdx, txDataWrap) = Counter(txState === sTxData && fire, 8)
    // iterate using div to convert clock rate to baud
    val (txBaudCount, txBaudWrap) = Counter(txState === sTxWait && fire, div)
    val (txSlackCount, txSlackWrap) = Counter(txState === sTxIdle && target.txd === 0.U && fire, 4)

    switch(txState) {
      is(sTxIdle) {
        when(txSlackWrap) {
          txData  := 0.U
          txState := sTxWait
        }
      }
      is(sTxWait) {
        when(txBaudWrap) {
          txState := sTxData
        }
      }
      is(sTxData) {
        when(fire) {
          txData := txData | (target.txd << txDataIdx)
        }
        when(txDataWrap) {
          txState := Mux(target.txd === 1.U, sTxIdle, sTxBreak)
        }.elsewhen(fire) {
          txState := sTxWait
        }
      }
      is(sTxBreak) {
        when(target.txd === 1.U && fire) {
          txState := sTxIdle
        }
      }
    }

    txfifo.io.enq.bits  := txData
    txfifo.io.enq.valid := txDataWrap

    val sRxIdle :: sRxStart :: sRxData :: Nil = Enum(3)
    val rxState = RegInit(sRxIdle)
    // iterate using div to convert clock rate to baud
    val (rxBaudCount, rxBaudWrap) = Counter(fire, div)
    // iterate through bits in byte to deserialize
    val (rxDataIdx, rxDataWrap) = Counter(rxState === sRxData && fire && rxBaudWrap, 8)

    target.rxd := 1.U
    switch(rxState) {
      is(sRxIdle) {
        target.rxd := 1.U
        when (rxBaudWrap && rxfifo.io.deq.valid) {
          rxState := sRxStart
        }
      }
      is(sRxStart) {
        target.rxd := 0.U
        when(rxBaudWrap) {
          rxState := sRxData
        }
      }
      is(sRxData) {
        target.rxd := (rxfifo.io.deq.bits >> rxDataIdx)(0)
        when(rxDataWrap && rxBaudWrap) {
          rxState := sRxIdle
        }
      }
    }
    rxfifo.io.deq.ready := (rxState === sRxData) && rxDataWrap && rxBaudWrap && fire

    // DOC include start: UART Bridge Footer
    // Exposed the head of the queue and the valid bit as a read-only registers
    // with name "out_bits" and out_valid respectively
    genROReg(txfifo.io.deq.bits, "out_bits")
    genROReg(txfifo.io.deq.valid, "out_valid")

    // Generate a writeable register, "out_ready", that when written to dequeues
    // a single element in the tx_fifo. Pulsify derives the register back to false
    // after pulseLength cycles to prevent multiple dequeues
    Pulsify(genWORegInit(txfifo.io.deq.ready, "out_ready", false.B), pulseLength = 1)

    // Generate regisers for the rx-side of the UART; this is eseentially the reverse of the above
    genWOReg(rxfifo.io.enq.bits, "in_bits")
    Pulsify(genWORegInit(rxfifo.io.enq.valid, "in_valid", false.B), pulseLength = 1)
    genROReg(rxfifo.io.enq.ready, "in_ready")

    // This method invocation is required to wire up all of the MMIO registers to
    // the simulation control bus (AXI4-lite)
    genCRFile()
    // DOC include end: UART Bridge Footer

    override def genHeader(base: BigInt, memoryRegions: Map[String, BigInt], sb: StringBuilder): Unit = {
      genConstructor(base, sb, "uart_t", "uart")
    }
  }
}
