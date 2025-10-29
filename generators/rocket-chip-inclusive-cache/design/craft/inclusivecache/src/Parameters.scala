/*
 * Copyright 2019 SiFive, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You should have received a copy of LICENSE.Apache2 along with
 * this software. If not, you may obtain a copy at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package sifive.blocks.inclusivecache

import chisel3._
import chisel3.util._
import chisel3.experimental.SourceInfo
import org.chipsalliance.cde.config._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.util._
import freechips.rocketchip.util.property.cover
import scala.math.{min,max}

case class CacheParameters(
  level:       Int,
  ways:        Int,
  sets:        Int,
  blockBytes:  Int,
  beatBytes:   Int, // inner
  hintsSkipProbe: Boolean)
{
  require (ways > 0)
  require (sets > 0)
  require (blockBytes > 0 && isPow2(blockBytes))
  require (beatBytes > 0 && isPow2(beatBytes))
  require (blockBytes >= beatBytes)

  val blocks = ways * sets
  val sizeBytes = blocks * blockBytes
  val blockBeats = blockBytes/beatBytes
}

case class InclusiveCachePortParameters(
  a: BufferParams,
  b: BufferParams,
  c: BufferParams,
  d: BufferParams,
  e: BufferParams)
{
  def apply()(implicit p: Parameters, valName: ValName) = LazyModule(new TLBuffer(a, b, c, d, e))
}

object InclusiveCachePortParameters
{
  val none = InclusiveCachePortParameters(
    a = BufferParams.none,
    b = BufferParams.none,
    c = BufferParams.none,
    d = BufferParams.none,
    e = BufferParams.none)

  val full = InclusiveCachePortParameters(
    a = BufferParams.default,
    b = BufferParams.default,
    c = BufferParams.default,
    d = BufferParams.default,
    e = BufferParams.default)

  // This removes feed-through paths from C=>A and A=>C
  val fullC = InclusiveCachePortParameters(
    a = BufferParams.none,
    b = BufferParams.none,
    c = BufferParams.default,
    d = BufferParams.none,
    e = BufferParams.none)

  val flowAD = InclusiveCachePortParameters(
    a = BufferParams.flow,
    b = BufferParams.none,
    c = BufferParams.none,
    d = BufferParams.flow,
    e = BufferParams.none)

  val flowAE = InclusiveCachePortParameters(
    a = BufferParams.flow,
    b = BufferParams.none,
    c = BufferParams.none,
    d = BufferParams.none,
    e = BufferParams.flow)

  // For innerBuf:
  //   SinkA:   no restrictions, flows into   scheduler+putbuffer
  //   SourceB: no restrictions, flows out of scheduler
  //   sinkC:   no restrictions, flows into   scheduler+putbuffer & buffered to bankedStore
  //   SourceD: no restrictions, flows out of bankedStore/regout
  //   SinkE:   no restrictions, flows into   scheduler
  //
  // ... so while none is possible, you probably want at least flowAC to cut ready
  //     from the scheduler delay and flowD to ease SourceD back-pressure

  // For outerBufer:
  //   SourceA: must not be pipe, flows out of scheduler
  //   SinkB:   no restrictions,  flows into   scheduler
  //   SourceC: pipe is useless,  flows out of bankedStore/regout, parameter depth ignored
  //   SinkD:   no restrictions,  flows into   scheduler & bankedStore
  //   SourceE: must not be pipe, flows out of scheduler
  //
  // ... AE take the channel ready into the scheduler, so you need at least flowAE
}

case class InclusiveCacheMicroParameters(
  writeBytes: Int, // backing store update granularity
  memCycles:  Int = 40, // # of L2 clock cycles for a memory round-trip (50ns @ 800MHz)
  portFactor: Int = 4,  // numSubBanks = (widest TL port * portFactor) / writeBytes
  dirReg:     Boolean = false,
  innerBuf:   InclusiveCachePortParameters = InclusiveCachePortParameters.fullC, // or none
  outerBuf:   InclusiveCachePortParameters = InclusiveCachePortParameters.full)   // or flowAE
{
  require (writeBytes > 0 && isPow2(writeBytes))
  require (memCycles > 0)
  require (portFactor >= 2) // for inner RMW and concurrent outer Relase + Grant
}

case class InclusiveCacheControlParameters(
  address:   BigInt,
  beatBytes: Int,
  bankedControl: Boolean)

case class InclusiveCacheParameters(
  cache:  CacheParameters,
  micro:  InclusiveCacheMicroParameters,
  control: Boolean,
  inner:  TLEdgeIn,
  outer:  TLEdgeOut)(implicit val p: Parameters)
{
  require (cache.ways > 1)
  require (cache.sets > 1 && isPow2(cache.sets))
  require (micro.writeBytes <= inner.manager.beatBytes)
  require (micro.writeBytes <= outer.manager.beatBytes)
  require (inner.manager.beatBytes <= cache.blockBytes)
  require (outer.manager.beatBytes <= cache.blockBytes)

  // Require that all cached address ranges have contiguous blocks
  outer.manager.managers.flatMap(_.address).foreach { a =>
    require (a.alignment >= cache.blockBytes)
  }

  // If we are the first level cache, we do not need to support inner-BCE
  val firstLevel = !inner.client.clients.exists(_.supports.probe)
  // If we are the last level cache, we do not need to support outer-B
  val lastLevel = !outer.manager.managers.exists(_.regionType > RegionType.UNCACHED)
  require (lastLevel)

  // Provision enough resources to achieve full throughput with missing single-beat accesses
  val mshrs = InclusiveCacheParameters.all_mshrs(cache, micro)
  val secondary = max(mshrs, micro.memCycles - mshrs)
  val putLists = micro.memCycles // allow every request to be single beat
  val putBeats = max(2*cache.blockBeats, micro.memCycles)
  val relLists = 2
  val relBeats = relLists*cache.blockBeats

  val flatAddresses = AddressSet.unify(outer.manager.managers.flatMap(_.address))
  val pickMask = AddressDecoder(flatAddresses.map(Seq(_)), flatAddresses.map(_.mask).reduce(_|_))

  def bitOffsets(x: BigInt, offset: Int = 0, tail: List[Int] = List.empty[Int]): List[Int] =
    if (x == 0) tail.reverse else bitOffsets(x >> 1, offset + 1, if ((x & 1) == 1) offset :: tail else tail)
  val addressMapping = bitOffsets(pickMask)
  val addressBits = addressMapping.size

  // println(s"addresses: ${flatAddresses} => ${pickMask} => ${addressBits}")

  val allClients = inner.client.clients.size
  val clientBitsRaw = inner.client.clients.filter(_.supports.probe).size
  val clientBits = max(1, clientBitsRaw)
  val stateBits = 2

  val wayBits    = log2Ceil(cache.ways)
  val setBits    = log2Ceil(cache.sets)
  val offsetBits = log2Ceil(cache.blockBytes)
  val tagBits    = addressBits - setBits - offsetBits
  val putBits    = log2Ceil(max(putLists, relLists))

  require (tagBits > 0)
  require (offsetBits > 0)

  val innerBeatBits = (offsetBits - log2Ceil(inner.manager.beatBytes)) max 1
  val outerBeatBits = (offsetBits - log2Ceil(outer.manager.beatBytes)) max 1
  val innerMaskBits = inner.manager.beatBytes / micro.writeBytes
  val outerMaskBits = outer.manager.beatBytes / micro.writeBytes

  def clientBit(source: UInt): UInt = {
    if (clientBitsRaw == 0) {
      0.U
    } else {
      Cat(inner.client.clients.filter(_.supports.probe).map(_.sourceId.contains(source)).reverse)
    }
  }

  def clientSource(bit: UInt): UInt = {
    if (clientBitsRaw == 0) {
      0.U
    } else {
      Mux1H(bit, inner.client.clients.filter(_.supports.probe).map(c => c.sourceId.start.U))
    }
  }

  def parseAddress(x: UInt): (UInt, UInt, UInt) = {
    val offset = Cat(addressMapping.map(o => x(o,o)).reverse)
    val set = offset >> offsetBits
    val tag = set >> setBits
    (tag(tagBits-1, 0), set(setBits-1, 0), offset(offsetBits-1, 0))
  }

  def widen(x: UInt, width: Int): UInt = {
    val y = x | 0.U(width.W)
    assert (y >> width === 0.U)
    y(width-1, 0)
  }

  def expandAddress(tag: UInt, set: UInt, offset: UInt): UInt = {
    val base = Cat(widen(tag, tagBits), widen(set, setBits), widen(offset, offsetBits))
    val bits = Array.fill(outer.bundle.addressBits) { 0.U(1.W) }
    addressMapping.zipWithIndex.foreach { case (a, i) => bits(a) = base(i,i) }
    Cat(bits.reverse)
  }

  def restoreAddress(expanded: UInt): UInt = {
    val missingBits = flatAddresses
      .map { a => (a.widen(pickMask).base, a.widen(~pickMask)) } // key is the bits to restore on match
      .groupBy(_._1)
      .view
      .mapValues(_.map(_._2))
    val muxMask = AddressDecoder(missingBits.values.toList)
    val mux = missingBits.toList.map { case (bits, addrs) =>
      val widen = addrs.map(_.widen(~muxMask))
      val matches = AddressSet
        .unify(widen.distinct)
        .map(_.contains(expanded))
        .reduce(_ || _)
      (matches, bits.U)
    }
    expanded | Mux1H(mux)
  }

  def dirReg[T <: Data](x: T, en: Bool = true.B): T = {
    if (micro.dirReg) RegEnable(x, en) else x
  }

  def ccover(cond: Bool, label: String, desc: String)(implicit sourceInfo: SourceInfo) =
    cover(cond, "CCACHE_L" + cache.level + "_" + label, "MemorySystem;;" + desc)
}

object MetaData
{
  val stateBits = 2
  def INVALID: UInt = 0.U(stateBits.W) // way is empty
  def BRANCH:  UInt = 1.U(stateBits.W) // outer slave cache is trunk
  def TRUNK:   UInt = 2.U(stateBits.W) // unique inner master cache is trunk
  def TIP:     UInt = 3.U(stateBits.W) // we are trunk, inner masters are branch

  // Does a request need trunk?
  def needT(opcode: UInt, param: UInt): Bool = {
    !opcode(2) ||
    (opcode === TLMessages.Hint && param === TLHints.PREFETCH_WRITE) ||
    ((opcode === TLMessages.AcquireBlock || opcode === TLMessages.AcquirePerm) && param =/= TLPermissions.NtoB)
  }
  // Does a request prove the client need not be probed?
  def skipProbeN(opcode: UInt, hintsSkipProbe: Boolean): Bool = {
    // Acquire(toB) and Get => is N, so no probe
    // Acquire(*toT) => is N or B, but need T, so no probe
    // Hint => could be anything, so probe IS needed, if hintsSkipProbe is enabled, skip probe the same client
    // Put* => is N or B, so probe IS needed
    opcode === TLMessages.AcquireBlock || opcode === TLMessages.AcquirePerm || opcode === TLMessages.Get || (opcode === TLMessages.Hint && hintsSkipProbe.B)
  }
  def isToN(param: UInt): Bool = {
    param === TLPermissions.TtoN || param === TLPermissions.BtoN || param === TLPermissions.NtoN
  }
  def isToB(param: UInt): Bool = {
    param === TLPermissions.TtoB || param === TLPermissions.BtoB
  }
}

object InclusiveCacheParameters
{
  val lfsrBits = 10
  val L2ControlAddress = 0x2010000
  val L2ControlSize = 0x1000
  def out_mshrs(cache: CacheParameters, micro: InclusiveCacheMicroParameters): Int = {
    // We need 2-3 normal MSHRs to cover the Directory latency
    // To fully exploit memory bandwidth-delay-product, we need memCyles/blockBeats MSHRs
    max(if (micro.dirReg) 3 else 2, (micro.memCycles + cache.blockBeats - 1) / cache.blockBeats)
  }
  def all_mshrs(cache: CacheParameters, micro: InclusiveCacheMicroParameters): Int =
    // We need a dedicated MSHR for B+C each
    2 + out_mshrs(cache, micro)
}

class InclusiveCacheBundle(params: InclusiveCacheParameters) extends Bundle
