package testchipip.serdes.old

import chisel3._
import org.chipsalliance.cde.config.{Parameters, Config}
import freechips.rocketchip.subsystem._
import freechips.rocketchip.tilelink.{TLBundleParameters}

//--------------------------
// Serial Tilelink Configs
//--------------------------

// Attach a sequence of serial-TL ports to a system
class WithSerialTL(params: Seq[SerialTLParams] = Seq(SerialTLParams())) extends Config((site, here, up) => {
  case SerialTLKey => params
})

// Modify the width of all attached serial-TL ports
class WithSerialTLWidth(width: Int) extends Config((site, here, up) => {
  case SerialTLKey => up(SerialTLKey).map(k => k.copy(phyParams = k.phyParams match {
    case p: InternalSyncSerialParams => p.copy(width=width)
    case p: ExternalSyncSerialParams => p.copy(width=width)
    case p: SourceSyncSerialParams => p.copy(width=width)
  }))
})

class WithSerialTLPHYParams(phyParams: SerialParams = ExternalSyncSerialParams()) extends Config((site, here, up) => {
  case SerialTLKey => up(SerialTLKey).map(k => k.copy(phyParams = phyParams))
})

// Set the bus which the serial-TL client will master on this system for all attached serial-TL ports
class WithSerialTLMasterLocation(masterWhere: TLBusWrapperLocation) extends Config((site, here, up) => {
  case SerialTLKey => up(SerialTLKey).map(s => s.copy(
    client=s.client.map(_.copy(masterWhere=masterWhere))
  ))
})

// Set the bus which the serial-TL manager will attach to on this system for all attached serial-TL ports
class WithSerialTLSlaveLocation(slaveWhere: TLBusWrapperLocation) extends Config((site, here, up) => {
  case SerialTLKey => up(SerialTLKey).map(s => s.copy(
    manager=s.manager.map(_.copy(slaveWhere=slaveWhere))
  ))
})

class WithSerialTLPBusManager extends WithSerialTLSlaveLocation(PBUS)

// Add a client interface to all attached serial-TL ports on this system
class WithSerialTLClient extends Config((site, here, up) => {
  case SerialTLKey => up(SerialTLKey).map(s => s.copy(client=Some(SerialTLClientParams())))
})

// Remove the client interface from all attached serial-TL ports on this system
class WithNoSerialTLClient extends Config((site, here, up) => {
  case SerialTLKey => up(SerialTLKey).map(s => s.copy(client=None))
})

// Specify a read/write memory region to all attached serial-TL ports on this system
class WithSerialTLMem(
  base: BigInt = BigInt("80000000", 16),
  size: BigInt = BigInt("10000000", 16),
  idBits: Int = 8,
  isMainMemory: Boolean = true
) extends Config((site, here, up) => {
  case SerialTLKey => {
    val memParams = ManagerRAMParams(
      address = base,
      size = size
    )
    up(SerialTLKey, site).map { k => k.copy(
      manager = Some(k.manager.getOrElse(SerialTLManagerParams()).copy(
        memParams = Seq(memParams),
        isMemoryDevice = isMainMemory,
        idBits = idBits
      ))
    )}
  }
})

// Specify the TL merged bundle parameters for all attached serial-TL ports on this system
class WithSerialTLBundleParams(params: TLBundleParameters) extends Config((site, here, up) => {
  case SerialTLKey => up(SerialTLKey).map(_.copy(bundleParams=params))
})

// Enable the TL-C protoocl for all attached serial-TL ports on this system
class WithSerialTLBCE extends Config((site, here, up) => {
  case SerialTLKey => up(SerialTLKey).map(s => s.copy(
    bundleParams=s.bundleParams.copy(hasBCE=true)
  ))
})

// Attach a read-only-memory to all serial-TL ports on this system
class WithSerialTLROM(base: BigInt = 0x20000, size: Int = 0x10000) extends Config((site, here, up) => {
  case SerialTLKey => up(SerialTLKey, site).map { k => k.copy(
    manager = k.manager.map { s => s.copy(
      romParams = Seq(ManagerROMParams(address = base, size = size))
    )}
  )}
})

// Specify the ROM contents for any read-only-memories attached to serial-TL ports on this system
// Note: This only affects simulation
class WithSerialTLROMFile(file: String) extends Config((site, here, up) => {
  case SerialTLKey => up(SerialTLKey, site).map { k => k.copy(
    manager = k.manager.map { s => s.copy(
      romParams = s.romParams.map(_.copy(contentFileName = Some(file)))
    )}
  )}
})

// Attach a coherent read/write/cacheable memory to all serial-TL ports on this system
class WithSerialTLCoherentMem(base: BigInt, size: BigInt) extends Config((site, here, up) => {
  case SerialTLKey => up(SerialTLKey).map { k => k.copy(
    manager = Some(k.manager.getOrElse(SerialTLManagerParams())).map(s => s.copy(
      cohParams = s.cohParams :+ ManagerCOHParams(base, size)
    ))
  )}
})

// Specify the number of client ID bits for serial-TL ports on this system which master this system
class WithSerialTLClientIdBits(bits: Int) extends Config((site, here, up) => {
  case SerialTLKey => up(SerialTLKey).map { k => k.copy(
    client=k.client.map(_.copy(idBits=bits))
  )}
})

// Remove all serial-TL ports from this system
class WithNoSerialTL extends Config((site, here, up) => {
  case SerialTLKey => Nil
})
