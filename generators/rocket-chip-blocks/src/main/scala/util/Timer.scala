package sifive.blocks.util

import chisel3._
import chisel3.util._
import freechips.rocketchip.regmapper._
import freechips.rocketchip.util.WideCounter

import scala.math.{min, max}

class GenericTimerCfgReg(
  val maxcmp: Int,
  val scaleWidth: Int) extends Bundle {

  val ip = Vec(maxcmp, Bool())
  val gang = Vec(maxcmp, Bool())
  val extra = Vec(maxcmp, Bool())
  val center = Vec(maxcmp, Bool())
  val reserved0 = UInt(2.W)
  val running = Bool()
  val countAlways = Bool()
  val reserved1 = UInt(1.W)
  val deglitch = Bool()
  val zerocmp = Bool()
  val sticky = Bool()
  val reserved2 = UInt((8 - scaleWidth).W)
  val scale = UInt(scaleWidth.W)

}

case class GenericTimerCfgDescs(
  scale: RegFieldDesc,
  sticky: RegFieldDesc,
  zerocmp: RegFieldDesc,
  deglitch:RegFieldDesc,
  countAlways: RegFieldDesc,
  running: RegFieldDesc,
  center: Seq[RegFieldDesc],
  extra: Seq[RegFieldDesc],
  gang: Seq[RegFieldDesc],
  ip: Seq[RegFieldDesc]
)

object DefaultGenericTimerCfgDescs  {
  def apply(prefix: String, ncmp: Int): GenericTimerCfgDescs = GenericTimerCfgDescs(
    scale = RegFieldDesc(s"${prefix}scale", "Counter scale value."),
    sticky = RegFieldDesc(s"${prefix}sticky", "Sticky. Disallow clearing of ${prefix}cmpXip bits"),
    zerocmp = RegFieldDesc(s"${prefix}zerocmp", "Reset counter to zero after match."),
    deglitch =  RegFieldDesc(s"${prefix}deglitch", "Deglitch - latch ${prefix}cmpXip within same cycle."),
    countAlways = RegFieldDesc(s"${prefix}enalways", "Enable Always - run continuously",
      reset = Some(0)),
    running = RegFieldDesc(s"${prefix}oneshot", "Enable One Shot - run one cycle, then this bit is cleared.",
      reset=Some(0), volatile=true),
    center = Seq.tabulate(ncmp){ i => RegFieldDesc(s"${prefix}cmp${i}center", s"Comparator ${i} Center")},
    extra = Seq.tabulate(ncmp){ i => RegFieldDesc(s"${prefix}extra${i}", s"Comparator ${i} Extra")},
    gang = Seq.tabulate(ncmp){ i => RegFieldDesc(s"${prefix}gang${i}", s"Comparator ${i}/${(i+1) % ncmp} Gang")},
    ip = Seq.tabulate(ncmp){ i => RegFieldDesc(s"${prefix}ip${i}", s"Interrupt ${i} Pending", volatile=true)}
  )
}

class GenericTimerCfgRegIFC (
  val ncmp: Int,
  val maxcmp: Int,
  val scaleWidth: Int) extends Bundle {

  val write = Input(new GenericTimerCfgReg(maxcmp, scaleWidth))
  val read =  Output(new GenericTimerCfgReg(maxcmp, scaleWidth))

  val write_ip = Vec(maxcmp, Input(Bool()))
  val write_gang = Vec(maxcmp, Input(Bool()))
  val write_extra = Vec(maxcmp, Input(Bool()))
  val write_center = Vec(maxcmp, Input(Bool()))
  val write_running = Input(Bool())
  val write_countAlways = Input(Bool())
  val write_deglitch = Input(Bool())
  val write_zerocmp = Input(Bool())
  val write_sticky = Input(Bool())
  val write_scale = Input(Bool())

  def toRegFields(prefix: String, descs: GenericTimerCfgDescs): Seq[RegField] = {

    def writeFn(valid: Bool, data: UInt, wr_data: UInt, wr_notify: Bool): Bool = {
      wr_notify := valid
      wr_data   := data
      true.B
    }

    // Defaults, because only ncmp of these are assigned by the regmap below.
    write.ip     := VecInit.fill(maxcmp){false.B}
    write.gang   := VecInit.fill(maxcmp){false.B}
    write.extra  := VecInit.fill(maxcmp){false.B}
    write.center := VecInit.fill(maxcmp){false.B}
    write_ip     := VecInit.fill(maxcmp){false.B}
    write_gang   := VecInit.fill(maxcmp){false.B}
    write_extra  := VecInit.fill(maxcmp){false.B}
    write_center := VecInit.fill(maxcmp){false.B}

    // unused
    write.reserved0 := DontCare
    write.reserved1 := DontCare
    write.reserved2 := DontCare

    RegFieldGroup(s"${prefix}cfg", Some(s"${prefix} Configuration"),
      Seq(
        RegField(scaleWidth, RegReadFn(read.scale), RegWriteFn((v, d) => writeFn(v, d, write.scale, write_scale)), descs.scale),
        RegField(8-scaleWidth),
        RegField(1, RegReadFn(read.sticky), RegWriteFn((v, d) => writeFn(v, d, write.sticky, write_sticky)), descs.sticky),
        RegField(1, RegReadFn(read.zerocmp), RegWriteFn((v, d) => writeFn(v, d, write.zerocmp, write_zerocmp)), descs.zerocmp),
        RegField(1, RegReadFn(read.deglitch), RegWriteFn((v, d) => writeFn(v, d, write.deglitch, write_deglitch)), descs.deglitch),
        RegField(1),
        RegField(1, RegReadFn(read.countAlways), RegWriteFn((v, d) => writeFn(v, d, write.countAlways, write_countAlways)), descs.countAlways),
        RegField(1, RegReadFn(read.running), RegWriteFn((v, d) => writeFn(v, d, write.running, write_running)), descs.running),
        RegField(2)
      ) ++ Seq.tabulate(ncmp) { i =>
        RegField(1, RegReadFn(read.center(i)), RegWriteFn((v, d) => writeFn(v, d, write.center(i), write_center(i))), descs.center(i))
      }
      ++ (if (ncmp < maxcmp) Seq(RegField(maxcmp - ncmp)) else Nil)
      ++ Seq.tabulate(ncmp) { i =>
          RegField(1, RegReadFn(read.extra(i)),
          RegWriteFn((v, d) => writeFn(v, d, write.extra(i), write_extra(i))), descs.extra(i))
      }
      ++ (if (ncmp < maxcmp) Seq(RegField(maxcmp - ncmp)) else Nil)
      ++ Seq.tabulate(ncmp) { i =>
        RegField(1, RegReadFn(read.gang(i)),
          RegWriteFn((v, d) => writeFn(v, d, write.gang(i), write_gang(i))), descs.gang(i))
      }
      ++ (if (ncmp < maxcmp) Seq(RegField(maxcmp - ncmp)) else Nil)
      ++ Seq.tabulate(ncmp) { i =>
        RegField(1, RegReadFn(read.ip(i)),
          RegWriteFn((v, d) => writeFn(v, d, write.ip(i), write_ip(i))), descs.ip(i))
      }
      ++ (if (ncmp < maxcmp) Seq(RegField(maxcmp - ncmp)) else Nil)
    )
  }

  def anyWriteValid: Bool = (
    write_ip ++
      write_gang ++
      write_extra ++
      write_center ++
      Seq(write_running) ++
      Seq(write_countAlways) ++
      Seq(write_deglitch) ++
      Seq(write_zerocmp) ++
      Seq(write_sticky) ++
      Seq(write_scale)
  ).reduce(_||_)

}

class GenericTimerIO(
  val regWidth: Int,
  val ncmp: Int,
  val maxcmp: Int,
  val scaleWidth: Int,
  val countWidth: Int,
  val cmpWidth: Int) extends Bundle {
  val regs = new Bundle {
    val cfg = new GenericTimerCfgRegIFC(ncmp, maxcmp, scaleWidth)
    val countLo = new SlaveRegIF(min(regWidth, countWidth))
    // If countWidth < regWidth, countHi ends up as a regWidth wide reserved field.
    val countHi = new SlaveRegIF(max(regWidth, countWidth - regWidth))
    val s = new SlaveRegIF(cmpWidth)
    val cmp = Vec(ncmp, new SlaveRegIF(cmpWidth))
    val feed = new SlaveRegIF(regWidth)
    val key = new SlaveRegIF(regWidth)
  }
  val ip = Output(Vec(ncmp, Bool()))
}


trait GenericTimer {
  protected def prefix: String
  protected def countWidth: Int
  protected def cmpWidth: Int
  protected def ncmp: Int
  protected def countAlways: Bool
  protected def countEn: Bool
  protected def feed: Bool
  protected def ip: Vec[Bool]
  protected def countAwake: Bool = false.B 
  protected def unlocked: Bool = true.B
  protected def rsten: Bool = false.B
  protected def deglitch: Bool = false.B
  protected def sticky: Bool = false.B
  protected def oneShot: Bool = false.B
  protected def center: Vec[Bool] = VecInit.fill(ncmp){false.B}
  protected def extra: Vec[Bool] = VecInit.fill(ncmp){false.B}
  protected def gang: Vec[Bool] = VecInit.fill(ncmp){false.B}
  protected val scaleWidth = 4
  protected val regWidth = 32
  protected def maxcmp: Int = 4
  require(ncmp <= maxcmp)
  require(ncmp > 0)

  protected def countLo_desc: RegFieldDesc = if (countWidth > regWidth) {
    RegFieldDesc(s"${prefix}countlo", "Low bits of Counter", volatile=true)
  } else  {
    RegFieldDesc(s"${prefix}count", "Counter Register", volatile=true)
  }
  protected def countHi_desc: RegFieldDesc = if (countWidth > regWidth) RegFieldDesc(s"${prefix}counthi", "High bits of Counter", volatile=true) else RegFieldDesc.reserved
  protected def s_desc: RegFieldDesc = RegFieldDesc(s"${prefix}s", "Scaled value of Counter", access=RegFieldAccessType.R, volatile=true)
  protected def cmp_desc: Seq[RegFieldDesc] = Seq.tabulate(ncmp){ i => RegFieldDesc(s"${prefix}cmp${i}", s"Comparator ${i}")}
  protected def feed_desc: RegFieldDesc = RegFieldDesc(s"${prefix}feed", "Feed register")
  protected def key_desc: RegFieldDesc  = RegFieldDesc(s"${prefix}key", "Key Register")
  protected def cfg_desc: GenericTimerCfgDescs = DefaultGenericTimerCfgDescs(prefix, ncmp)

  val io: GenericTimerIO

  protected val scale = RegEnable(io.regs.cfg.write.scale, io.regs.cfg.write_scale && unlocked)
  protected lazy val zerocmp = RegEnable(io.regs.cfg.write.zerocmp, io.regs.cfg.write_zerocmp && unlocked)
  protected val cmp = io.regs.cmp.map(c => RegEnable(c.write.bits, c.write.valid && unlocked))

  protected val count = WideCounter(countWidth, countEn, reset = true)

  // generate periodic interrupt
  protected val s = (count >> scale)(cmpWidth-1, 0)
  // reset counter when fed or elapsed
  protected val elapsed = VecInit.tabulate(ncmp){i => Mux(s(cmpWidth-1) && center(i), ~s, s) >= cmp(i)}
  protected val countReset = feed || (zerocmp && elapsed(0))
  when (countReset) { count := 0.U }
  when (io.regs.countLo.write.valid && unlocked) { count := Cat(count >> regWidth, io.regs.countLo.write.bits) }
  if (countWidth > regWidth) when (io.regs.countHi.write.valid && unlocked) { count := Cat(io.regs.countHi.write.bits, count(regWidth-1, 0)) }

  io.regs.cfg.read := 0.U.asTypeOf(new GenericTimerCfgReg(maxcmp, scaleWidth))
  for (i <- 0 until ncmp) { io.regs.cfg.read.ip(i) := ip(i) }
  io.regs.cfg.read.gang := gang
  io.regs.cfg.read.extra := extra
  io.regs.cfg.read.center := center
  io.regs.cfg.read.running := countAwake || oneShot
  io.regs.cfg.read.countAlways := countAlways
  io.regs.cfg.read.deglitch := deglitch
  io.regs.cfg.read.zerocmp := zerocmp
  io.regs.cfg.read.sticky := rsten || sticky
  io.regs.cfg.read.scale := scale

  io.regs.countLo.read := count
  io.regs.countHi.read := count >> regWidth
  io.regs.s.read := s
  (io.regs.cmp zip cmp) map { case (r, c) => r.read := c }
  io.regs.feed.read := 0.U
  io.regs.key.read := unlocked
  io.ip := ip
}

object GenericTimer {
  def timerRegMap(t: GenericTimer, offset: Int, regBytes: Int): Seq[(Int, Seq[RegField])] = {
    val cfgRegs = Seq(offset -> t.io.regs.cfg.toRegFields(t.prefix, t.cfg_desc))
    val regs = Seq(
        2 -> (t.io.regs.countLo, t.countLo_desc),
        3 -> (t.io.regs.countHi, t.countHi_desc),
        4 -> (t.io.regs.s, t.s_desc),
        6 -> (t.io.regs.feed, t.feed_desc),
        7 -> (t.io.regs.key, t.key_desc)
    )
    val cmpRegs = (t.io.regs.cmp zip t.cmp_desc).zipWithIndex map { case ((r, d), i) => (8 + i) -> (r, d) }
    val otherRegs = for ((i, (r, d)) <- (regs ++ cmpRegs)) yield (offset + regBytes*i) -> Seq(r.toRegField(Some(d)))
    cfgRegs ++ otherRegs
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
