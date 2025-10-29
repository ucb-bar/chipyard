package sifive.blocks.devices.chiplink

import chisel3._ 
import chisel3.util.{log2Ceil, log2Up, UIntToOH, Cat, DecoupledIO}
import org.chipsalliance.cde.config.{Field, Parameters}
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.util.AsyncQueueParams

case class ChipLinkParams(TLUH: Seq[AddressSet], TLC: Seq[AddressSet], sourceBits: Int = 6, sinkBits: Int = 5, syncTX: Boolean = false, fpgaReset: Boolean = false)
{
  val domains = 8 // hard-wired into chiplink protocol
  require (sourceBits >= log2Ceil(domains))
  require (sinkBits >= 0)
  val sources = 1 << sourceBits
  val sinks = 1 << sinkBits
  val sourcesPerDomain = sources / domains
  val latency = 8 // ChipLink has at least 4 cycles of synchronization per side
  val dataBytes = 4
  val dataBits = dataBytes*8
  val clSourceBits = 16
  val clSinkBits = 16
  val crossing = AsyncQueueParams()
  val Qdepth = 8192 / dataBytes
  val maxXfer = 4096
  val xferBits = log2Ceil(maxXfer)
  val creditBits = 20 // use saturating addition => we can exploit at most 1MB of buffers
  val addressBits = 64
  require (log2Ceil(Qdepth + 1) <= creditBits)

  // Protocol supported operations:
  val noXfer = TransferSizes.none
  val fullXfer = TransferSizes(1, 64) // !!! 4096)
  val acqXfer = TransferSizes(64, 64)
  val atomicXfer = TransferSizes(1, 8)

}

case object ChipLinkKey extends Field[Seq[ChipLinkParams]](Nil)

case class TXN(domain: Int, source: Int)
case class ChipLinkInfo(params: ChipLinkParams, edgeIn: TLEdge, edgeOut: TLEdge, errorDev: AddressSet)
{
  // TL source => CL TXN
  val sourceMap: Map[Int, TXN] = {
    var alloc = 1
    val domains = Array.fill(params.domains) { 0 }
    println("ChipLink source mapping CLdomain CLsource <= TLsource:")
    val out = Map() ++ edgeIn.client.clients.flatMap { c =>
      // If the client needs order, pick a domain for it
      val domain = if (c.requestFifo) alloc else 0
      val offset = domains(domain)
      println(s"\t${domain} [${offset}, ${offset + c.sourceId.size}) <= [${c.sourceId.start}, ${c.sourceId.end}):\t${c.name}")
      if (c.requestFifo) {
        alloc = alloc + 1
        if (alloc == params.domains) alloc = 1
      }
      c.sourceId.range.map { id =>
        val source = domains(domain)
        domains(domain) = source + 1
        (id, TXN(domain, source))
      }
    }
    println("")
    out
  }

  def mux(m: Map[Int, Int]): Vec[UInt] = {
    val maxKey = m.keys.max
    val maxVal = m.values.max
    val valBits = log2Up(maxVal + 1)
    val out = Wire(Vec(maxKey + 1, UInt(valBits.W)))
    m.foreach { case (k, v) => out(k) := v.U(valBits.W) }
    out
  }

  // Packet format; little-endian
  def encode(format: UInt, opcode: UInt, param: UInt, size: UInt, domain: UInt, source: UInt): UInt = {
    def fmt(x: UInt, w: Int) = (x | 0.U(w.W))(w-1, 0)
    Cat(
      fmt(source, 16),
      fmt(domain, 3),
      fmt(size,   4),
      fmt(param,  3),
      fmt(opcode, 3),
      fmt(format, 3))
  }

  def decode(x: UInt): Seq[UInt] = {
    val format = x( 2,  0)
    val opcode = x( 5,  3)
    val param  = x( 8,  6)
    val size   = x(12,  9)
    val domain = x(15, 13)
    val source = x(31, 16)
    Seq(format, opcode, param, size, domain, source)
  }

  def size2beats(size: UInt): UInt = {
    val shift = log2Ceil(params.dataBytes)
    Cat(UIntToOH(size|0.U(4.W), params.xferBits + 1) >> (shift + 1), size <= shift.U)
  }

  def mask2beats(size: UInt): UInt = {
    val shift = log2Ceil(params.dataBytes*8)
    Cat(UIntToOH(size|0.U(4.W), params.xferBits + 1) >> (shift + 1), size <= shift.U)
  }

  def beats1(x: UInt, forceFormat: Option[UInt] = None): UInt = {
    val Seq(format, opcode, _, size, _, _) = decode(x)
    val beats = size2beats(size)
    val masks = mask2beats(size)
    val grant = opcode === TLMessages.Grant || opcode === TLMessages.GrantData
    val partial = opcode === TLMessages.PutPartialData
    val a = Mux(opcode(2), 0.U, beats) + 2.U + Mux(partial, masks, 0.U)
    val b = Mux(opcode(2), 0.U, beats) + 2.U + Mux(partial, masks, 0.U)
    val c = Mux(opcode(0), beats, 0.U) + 2.U
    val d = Mux(opcode(0), beats, 0.U) + grant.asUInt
    val e = 0.U
    val f = 0.U
    VecInit(a, b, c, d, e, f)(forceFormat.getOrElse(format))
  }

  def firstlast(x: DecoupledIO[UInt], forceFormat: Option[UInt] = None): (Bool, Bool) = {
    val count = RegInit(0.U)
    val beats = beats1(x.bits, forceFormat)
    val first = count === 0.U
    val last  = count === 1.U || (first && beats === 0.U)
    when (x.fire) { count := Mux(first, beats, count - 1.U) }
    (first, last)
  }

  // You can't just unilaterally use error, because this would misalign the mask
  def makeError(legal: Bool, address: UInt): UInt = {
    val alignBits = log2Ceil(errorDev.alignment)
    Cat(
      Mux(legal, address, errorDev.base.U)(params.addressBits-1, alignBits),
      address(alignBits-1, 0))
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
