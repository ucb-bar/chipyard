package sifive.blocks.util

import chisel3._ 

import org.chipsalliance.cde.config.{Field, Parameters}

import freechips.rocketchip.tilelink._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.prci._
import freechips.rocketchip.regmapper.RegisterRouter
import freechips.rocketchip.subsystem._

case class DevicesLocated(loc: HierarchicalLocation) extends Field[Seq[DeviceAttachParams]](Nil)

trait CanHaveDevices { this: Attachable =>
  def location: HierarchicalLocation
  def devicesSubhierarchies: Option[Seq[CanHaveDevices]]

  val devicesConfigs: Seq[DeviceAttachParams] = p(DevicesLocated(location)) ++
    devicesSubhierarchies.map(_.map(_.devicesConfigs)).getOrElse(Nil).flatten

  val devices: Seq[LazyModule] = p(DevicesLocated(location)).map(_.attachTo(this)) ++
    devicesSubhierarchies.map(_.map(_.devices)).getOrElse(Nil).flatten
}

trait DeviceParams

trait DeviceAttachParams {
  val device: DeviceParams
  val controlWhere: TLBusWrapperLocation
  val blockerAddr: Option[BigInt]
  val controlXType: ClockCrossingType

  def attachTo(where: Attachable)(implicit p: Parameters): LazyModule
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
