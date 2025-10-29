package sifive.blocks.devices.gpio

object GPIOCtrlRegs {
  val value       = 0x00
  val input_en    = 0x04
  val output_en   = 0x08
  val port        = 0x0c
  val pullup_en   = 0x10
  val drive       = 0x14
  val rise_ie     = 0x18
  val rise_ip     = 0x1c
  val fall_ie     = 0x20
  val fall_ip     = 0x24
  val high_ie     = 0x28
  val high_ip     = 0x2c
  val low_ie      = 0x30
  val low_ip      = 0x34
  val iof_en      = 0x38
  val iof_sel     = 0x3c
  val out_xor     = 0x40
  val passthru_high_ie = 0x44
  val passthru_low_ie  = 0x48
  val ps          = 0x4c
  val poe         = 0x50
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
