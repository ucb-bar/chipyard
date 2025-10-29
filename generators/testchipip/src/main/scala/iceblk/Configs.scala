package testchipip.iceblk

import chisel3._
import org.chipsalliance.cde.config.{Parameters, Config}
import freechips.rocketchip.subsystem._

//----------------------
// Block Device Configs
//----------------------

class WithBlockDevice(enable: Boolean = true) extends Config((site, here, up) => {
  case BlockDeviceKey => enable match {
    case true => Some(BlockDeviceConfig())
    case false => None
  }
})

class WithBlockDeviceLocations(slaveWhere: TLBusWrapperLocation = PBUS, masterWhere: TLBusWrapperLocation = FBUS) extends Config((site, here, up) => {
  case BlockDeviceAttachKey => BlockDeviceAttachParams(slaveWhere, masterWhere)
})

class WithNBlockDeviceTrackers(n: Int) extends Config((site, here, up) => {
  case BlockDeviceKey => up(BlockDeviceKey, site) match {
    case Some(a) => Some(a.copy(nTrackers = n))
    case None => None
  }
})
