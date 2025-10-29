package sifive.blocks.devices.spi

object SPICRs {
  val sckdiv    = 0x00
  val sckmode   = 0x04
  val csid      = 0x10
  val csdef     = 0x14
  val csmode    = 0x18
  val dcssck    = 0x28
  val dsckcs    = 0x2a
  val dintercs  = 0x2c
  val dinterxfr = 0x2e
  val extradel  = 0x38
  val sampledel = 0x3c

  val fmt       = 0x40
  val len       = 0x42
  val txfifo    = 0x48
  val rxfifo    = 0x4c
  val txmark    = 0x50
  val rxmark    = 0x54

  val insnmode  = 0x60
  val insnfmt   = 0x64
  val insnproto = 0x65
  val insncmd   = 0x66
  val insnpad   = 0x67

  val ie        = 0x70
  val ip        = 0x74
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
