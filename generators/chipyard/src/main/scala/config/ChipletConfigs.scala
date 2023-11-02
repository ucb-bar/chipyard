package chipyard

import org.chipsalliance.cde.config.{Config}
import freechips.rocketchip.subsystem.{SBUS, BroadcastParams}
import testchipip._

class IOChipletConfig extends Config(
  new testchipip.WithSerialTL(Seq(SerialTLParams(
    client = Some(SerialTLClientParams(
      masterWhere = SBUS,
      supportsProbe = true)),
    bundleParams = TLSerdesser.STANDARD_TLBUNDLE_PARAMS.copy(hasBCE=true)
  ))) ++

  new testchipip.WithNoBootAddrReg ++
  new testchipip.WithNoCustomBootPin ++
  new chipyard.config.WithNoBootROM ++
  new chipyard.config.WithNoCLINT ++
  new chipyard.config.WithNoTileClockGaters ++
  new chipyard.config.WithNoTileResetSetters ++
  new chipyard.config.WithNoBusErrorDevices ++
  new chipyard.config.WithNoDebug ++
  new chipyard.config.WithNoPLIC ++
  new chipyard.config.AbstractConfig)

class RocketChipletConfig extends Config(
  new testchipip.WithSerialTL(Seq(
    SerialTLParams(                                     // 0'th serial-tl masters the FBUS, for bringup
      client = Some(SerialTLClientParams(idBits = 4)),
      width = 32
    ),
    SerialTLParams(                                     // 1'st serial-tl is slave of the OBUS, communicates with IO-chiplet
      client = None,
      manager = Some(SerialTLManagerParams(
        cohParams = Seq(ManagerCOHParams(
          address = BigInt("80000000", 16),
          size = BigInt("10000000", 16))),
        slaveWhere = OBUS)),
      bundleParams = TLSerdesser.STANDARD_TLBUNDLE_PARAMS.copy(hasBCE=true),
      provideClockFreqMHz = Some(100)
    )
  )) ++

  new testchipip.WithOffchipBusClient(SBUS) ++
  new testchipip.WithOffchipBus ++

  new freechips.rocketchip.subsystem.WithNoMemPort ++
  new freechips.rocketchip.subsystem.WithIncoherentBusTopology ++
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  new chipyard.config.WithNoUART ++
  new chipyard.config.AbstractConfig)

class MultiChipletConfig extends Config(
  new chipyard.harness.WithAbsoluteFreqHarnessClockInstantiator ++
  new chipyard.harness.WithMultiChipSerialTL(0, 1, chip0portId=0, chip1portId=1) ++
  new chipyard.harness.WithMultiChip(0, new IOChipletConfig) ++
  new chipyard.harness.WithMultiChip(1, new RocketChipletConfig)
)
