package sifive.blocks.devices.spi

import chisel3._
import chisel3.util._
import chisel3.experimental.dataview._
import org.chipsalliance.cde.config.Parameters
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.regmapper._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.interrupts._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.util.HeterogeneousBag
import sifive.blocks.util.{NonBlockingEnqueue, NonBlockingDequeue}



import sifive.blocks.util._

trait SPIParamsBase {
  val rAddress: BigInt
  val rSize: BigInt
  val rxDepth: Int
  val txDepth: Int
  val csWidth: Int
  val frameBits: Int
  val delayBits: Int
  val divisorBits: Int
  val fineDelayBits: Int
  val sampleDelayBits: Int
  val defaultSampleDel:Int
  val oeDisableDummy: Boolean

  lazy val csIdBits = log2Up(csWidth)
  lazy val lengthBits = log2Floor(frameBits) + 1
  lazy val countBits = math.max(lengthBits, delayBits)

  lazy val txDepthBits = log2Floor(txDepth) + 1
  lazy val rxDepthBits = log2Floor(rxDepth) + 1

}

case class SPIParams(
    rAddress: BigInt,
    rSize: BigInt = 0x1000,
    rxDepth: Int = 8,
    txDepth: Int = 8,
    csWidth: Int = 1,
    frameBits: Int = 8,
    delayBits: Int = 8,
    divisorBits: Int = 12,
    fineDelayBits: Int = 0,
    sampleDelayBits: Int = 5,
    defaultSampleDel: Int = 3,
    oeDisableDummy: Boolean = false
    )
  extends SPIParamsBase with DeviceParams {

  require(frameBits >= 4)
  require((fineDelayBits == 0) | (fineDelayBits == 5), s"Require fine delay bits to be 0 or 5 and not $fineDelayBits")
  require(sampleDelayBits >= 0)
  require(defaultSampleDel >= 0)
  require(!oeDisableDummy)
}

class SPITopModule(c: SPIParamsBase, outer: TLSPIBase)
    extends LazyModuleImp(outer) {

  val ctrl = RegInit(SPIControl.init(c))
  val fifo = Module(new SPIFIFO(c))
  val mac = Module(new SPIMedia(c))

  outer.port <> mac.io.port
  fifo.io.ctrl.fmt.viewAsSupertype(new SPIFormat(c) with HasSPILength) := ctrl.fmt
  fifo.io.ctrl.cs <> ctrl.cs.viewAsSupertype(new Bundle with HasSPICSMode)
  fifo.io.ctrl.wm := ctrl.wm
  mac.io.ctrl.sck := ctrl.sck
  mac.io.ctrl.extradel := ctrl.extradel
  mac.io.ctrl.sampledel := ctrl.sampledel
  mac.io.ctrl.dla := ctrl.dla
  mac.io.ctrl.cs <> ctrl.cs.viewAsSupertype(new SPIChipSelect(c))

  val ie = RegInit(0.U.asTypeOf(new SPIInterrupts())) 
  val ip = fifo.io.ip
  outer.interrupts(0) := (ip.txwm && ie.txwm) || (ip.rxwm && ie.rxwm)

  val regmapBase = Seq(
    SPICRs.sckdiv -> Seq(RegField(c.divisorBits, ctrl.sck.div,
                         RegFieldDesc("sckdiv", "Serial clock divisor", reset=Some(3)))),
    SPICRs.sckmode ->  RegFieldGroup("sckmode", Some("Serial clock mode"), Seq(
      RegField(1, ctrl.sck.pha,
               RegFieldDesc("sckmode_pha", "Serial clock phase", reset=Some(0))),
      RegField(1, ctrl.sck.pol,
               RegFieldDesc("sckmode_pol", "Serial clock polarity", reset=Some(0))))),
    SPICRs.csid -> Seq(RegField(c.csIdBits, ctrl.cs.id,
                       RegFieldDesc("csid", "Chip select id", reset=Some(0)))),
    SPICRs.csdef -> ctrl.cs.dflt.zipWithIndex.map{ case (x, i) => RegField(1, x,
                    RegFieldDesc(s"csdef$i", s"Chip select ${i} default", group = Some("csdef"), groupDesc = Some("Chip select default"), reset=Some(1)))},
    SPICRs.csmode -> Seq(RegField(SPICSMode.width, ctrl.cs.mode,
                         RegFieldDesc("csmode", "Chip select mode", reset=Some(SPICSMode.Auto.litValue)))),
    SPICRs.dcssck -> Seq(RegField(c.delayBits, ctrl.dla.cssck,
                         RegFieldDesc("cssck", "CS to SCK delay", reset=Some(1)))),
    SPICRs.dsckcs -> Seq(RegField(c.delayBits, ctrl.dla.sckcs,
                         RegFieldDesc("sckcs", "SCK to CS delay", reset=Some(1)))),
    SPICRs.dintercs -> Seq(RegField(c.delayBits, ctrl.dla.intercs,
                           RegFieldDesc("intercs", "Minimum CS inactive time", reset=Some(1)))),
    SPICRs.dinterxfr -> Seq(RegField(c.delayBits, ctrl.dla.interxfr,
                            RegFieldDesc("interxfr", "Minimum interframe delay", reset=Some(0)))),

    SPICRs.fmt -> RegFieldGroup("fmt",Some("Serial frame format"),Seq(
      RegField(SPIProtocol.width, ctrl.fmt.proto,
               RegFieldDesc("proto","SPI Protocol", reset=Some(SPIProtocol.Single.litValue))),
      RegField(SPIEndian.width, ctrl.fmt.endian,
               RegFieldDesc("endian","SPI Endianness", reset=Some(SPIEndian.MSB.litValue))),
      RegField(SPIDirection.width, ctrl.fmt.iodir,
               RegFieldDesc("iodir","SPI I/O Direction", reset=Some(SPIDirection.Rx.litValue))))),
    SPICRs.len -> Seq(RegField(c.lengthBits, ctrl.fmt.len,
                      RegFieldDesc("len","Number of bits per frame", reset=Some(math.min(c.frameBits, 8))))),

    SPICRs.txfifo -> RegFieldGroup("txdata",Some("Transmit data"),
                     NonBlockingEnqueue(fifo.io.tx)),
    SPICRs.rxfifo -> RegFieldGroup("rxdata",Some("Receive data"),
                     NonBlockingDequeue(fifo.io.rx)),

    SPICRs.txmark -> Seq(RegField(c.txDepthBits, ctrl.wm.tx,
                         RegFieldDesc("txmark","Transmit watermark", reset=Some(0)))),
    SPICRs.rxmark -> Seq(RegField(c.rxDepthBits, ctrl.wm.rx,
                         RegFieldDesc("rxmark","Receive watermark", reset=Some(0)))),
    SPICRs.ie -> RegFieldGroup("ie",Some("SPI interrupt enable"),Seq(
      RegField(1, ie.txwm,
      RegFieldDesc("txwm_ie","Transmit watermark interrupt enable", reset=Some(0))),
      RegField(1, ie.rxwm,
      RegFieldDesc("rxwm_ie","Receive watermark interrupt enable", reset=Some(0))))),
    SPICRs.ip -> RegFieldGroup("ip",Some("SPI interrupt pending"),Seq(
      RegField.r(1, ip.txwm,
      RegFieldDesc("txwm_ip","Transmit watermark interrupt pending", volatile=true)),
      RegField.r(1, ip.rxwm,
      RegFieldDesc("rxwm_ip","Receive watermark interrupt pending", volatile=true)))),

    SPICRs.extradel -> RegFieldGroup("extradel",Some("delay from the sck edge"),Seq(
      RegField(c.divisorBits, ctrl.extradel.coarse,
      RegFieldDesc("extradel_coarse","Coarse grain sample delay", reset=Some(0))),
      RegField(c.fineDelayBits, ctrl.extradel.fine,
      RegFieldDesc("extradel_fine","Fine grain sample delay", reset=Some(0))))),

    SPICRs.sampledel -> RegFieldGroup("sampledel",Some("Number of delay stages from slave to SPI controller"),Seq(
      RegField(c.sampleDelayBits, ctrl.sampledel.sd,
      RegFieldDesc("sampledel_sd","Number of delay stages from slave to the SPI controller", reset=Some(c.defaultSampleDel))))))

}

class MMCDevice(spi: Device, maxMHz: Double = 20) extends SimpleDevice("mmc", Seq("mmc-spi-slot")) {
  override def parent = Some(spi)
  override def describe(resources: ResourceBindings): Description = {
    val Description(name, mapping) = super.describe(resources)
    val extra = Map(
      "voltage-ranges"    -> Seq(ResourceInt(3300), ResourceInt(3300)),
      "disable-wp"        -> Nil,
      "spi-max-frequency" -> Seq(ResourceInt(maxMHz * 1000000)))
    Description(name, mapping ++ extra)
  }
}

class FlashDevice(spi: Device, bits: Int = 4, maxMHz: Double = 50, compat: Seq[String] = Nil) extends SimpleDevice("flash", compat :+ "jedec,spi-nor") {
  require (bits == 1 || bits == 2 || bits == 4)
  override def parent = Some(spi)
  override def describe(resources: ResourceBindings): Description = {
    val Description(name, mapping) = super.describe(resources)
    val extra = Map(
      "m25p,fast-read"    -> Nil,
      "spi-tx-bus-width"  -> Seq(ResourceInt(bits)),
      "spi-rx-bus-width"  -> Seq(ResourceInt(bits)),
      "spi-max-frequency" -> Seq(ResourceInt(maxMHz * 1000000)))
    Description(name, mapping ++ extra)
  }
}

abstract class TLSPIBase(w: Int, c: SPIParamsBase)(implicit p: Parameters) extends IORegisterRouter(
      RegisterRouterParams(
        name = "spi",
        compat = Seq("sifive,spi0"),
        base = c.rAddress,
        size = c.rSize,
        beatBytes = w),
      new SPIPortIO(c))
    with HasInterruptSources {
  require(isPow2(c.rSize))
  override def extraResources(resources: ResourceBindings) = Map(
        "#address-cells" -> Seq(ResourceInt(1)),
        "#size-cells" -> Seq(ResourceInt(0)))
  override def nInterrupts = 1
}

class TLSPI(w: Int, c: SPIParams)(implicit p: Parameters)
    extends TLSPIBase(w,c)(p) with HasTLControlRegMap {
  lazy val module = new SPITopModule(c, this) {
    mac.io.link <> fifo.io.link.viewAsSupertype(new SPILinkIO(c))
    val mapping = (regmapBase)
    regmap(mapping:_*)
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
