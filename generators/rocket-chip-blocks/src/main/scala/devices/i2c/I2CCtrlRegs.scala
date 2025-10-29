package sifive.blocks.devices.i2c

// matching Open Cores I2C to re-use Linux driver
// http://lxr.free-electrons.com/source/drivers/i2c/busses/i2c-ocores.c?v=4.6

object I2CCtrlRegs {
  val prescaler_lo = 0x00  // low byte clock prescaler register
  val prescaler_hi = 0x04  // high byte clock prescaler register
  val control      = 0x08  // control register
  val data         = 0x0c  // write: transmit byte, read: receive byte
  val cmd_status   = 0x10  // write: command, read: status
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
