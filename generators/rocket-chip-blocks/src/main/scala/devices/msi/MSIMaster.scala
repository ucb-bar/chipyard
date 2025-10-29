
package sifive.blocks.devices.msi

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config.Parameters
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.interrupts._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.util.leftOR

case class MSITarget(address: BigInt, spacing: Int, number: Int)
{
  require (number >= 0)
  require (address >= 0)
}

class MSIMaster(targets: Seq[MSITarget])(implicit p: Parameters) extends LazyModule
{
  val masterNode = TLClientNode(Seq(TLMasterPortParameters.v1(Seq(TLMasterParameters.v1("MSI Master", sourceId = IdRange(0,2))))))

  // A terminal interrupt node of flexible number
  val intNode = IntNexusNode(
    sourceFn = { _ => IntSourcePortParameters(Seq(IntSourceParameters(1, Nil))) },
    sinkFn   = { _ => IntSinkPortParameters(Seq(IntSinkParameters())) },
    inputRequiresOutput = false)

  lazy val module = new LazyModuleImp(this) {
    val (io, masterEdge) = masterNode.out(0)
    val interrupts = intNode.in.flatMap { case (i, e) => i.take(e.source.num) }

    // Construct a map of the addresses to update for interrupts
    val targetMap = targets.flatMap { case MSITarget(address, spacing, number) =>
      address until (address+spacing*number) by spacing
    } .map { addr =>
      val m = masterEdge.manager.find(addr)
      require (m.isDefined, s"MSIMaster ${name} was pointed at address 0x${addr}%x which does not exist")
      require (m.get.supportsPutFull.contains(1), s"MSIMaster ${name} requires device ${m.get.name} supportPutFull of 1 byte (${m.get.supportsPutFull})")
      addr.U
    }.take(interrupts.size max 1)

    require (interrupts.size <= targetMap.size, s"MSIMaster ${name} has more interrupts (${interrupts.size}) than addresses to use (${targetMap.size})")
    require (intNode.out.isEmpty, s"MSIMaster ${name} intNode is not a source!")

    val busy    = RegInit(false.B)
    val remote  = RegInit(0.U((interrupts.size max 1).W))
    val local   = if (interrupts.isEmpty) 0.U else Cat(interrupts.reverse)
    val pending = remote ^ local
    val select  = ~(leftOR(pending) << 1) & pending
    val address = Mux1H(select, targetMap)
    val lowBits = log2Ceil(masterEdge.manager.beatBytes)
    val shift   = if (lowBits > 0) address(lowBits-1, 0) else 0.U
    val data    = (select & local).orR

    io.a.valid := pending.orR && !busy
    io.a.bits := masterEdge.Put(
      fromSource = 0.U,
      toAddress  = address,
      lgSize     = 0.U,
      data       = data << (shift << 3))._2

    // When A is sent, toggle our model of the remote state
    when (io.a.fire) {
      remote := remote ^ select
      busy   := true.B
    }

    // Sink D messages to clear busy
    io.d.ready := true.B
    when (io.d.fire) {
      busy := false.B
    }

    // Tie off unused channels
    io.b.ready := false.B
    io.c.valid := false.B
    io.e.valid := false.B
  }
}

/*
   Copyright 2016 SiFive, Inc.

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/
