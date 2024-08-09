//See LICENSE for license details.
package firesim.bridges

import org.chipsalliance.cde.config.Config
import freechips.rocketchip.subsystem.{PeripheryBusKey, PeripheryBusParams}
import testchipip.iceblk.{BlockDeviceConfig, BlockDeviceKey}
import firesim.bridges.{LoopbackNIC}

// Enables NIC loopback the NIC widget
class WithNICWidgetLoopback  extends Config((site, here, up) => {
  case LoopbackNIC => true
})

class BlockDevConfig
    extends Config((site, here, up) => {
      case PeripheryBusKey =>
      case BlockDeviceKey  => Some(BlockDeviceConfig())
    })
