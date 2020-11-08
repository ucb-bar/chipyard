
package chipyard

import freechips.rocketchip.config.{Field, Config, Parameters}
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.util.{Location, Symmetric}
import freechips.rocketchip.subsystem._

// I'm putting this code here temporarily as I think it should be a candidate
// for upstreaming based on input from Henry Cook, but don't wnat to deal with
// an RC branch just yet.

// For subsystem/BusTopology.scala

/**
  * Keys that serve as a means to define crossing types from a Parameters instance
  */
case object SbusToMbusXTypeKey extends Field[ClockCrossingType](NoCrossing)
case object SbusToCbusXTypeKey extends Field[ClockCrossingType](NoCrossing)
case object CbusToPbusXTypeKey extends Field[ClockCrossingType](SynchronousCrossing())
case object FbusToSbusXTypeKey extends Field[ClockCrossingType](SynchronousCrossing())

// Biancolin: This, modified from Henry's email
/** Parameterization of a topology containing a banked coherence manager and a bus for attaching memory devices. */
case class CoherentMulticlockBusTopologyParams(
  sbus: SystemBusParams, // TODO remove this after better width propagation
  mbus: MemoryBusParams,
  l2: BankedL2Params,
  sbusToMbusXType: ClockCrossingType = NoCrossing
) extends TLBusWrapperTopology(
  instantiations = (if (l2.nBanks == 0) Nil else List(
    (MBUS, mbus),
    (L2, CoherenceManagerWrapperParams(mbus.blockBytes, mbus.beatBytes, l2.nBanks, L2.name)(l2.coherenceManager)))),
  connections = if (l2.nBanks == 0) Nil else List(
    (SBUS, L2,   TLBusWrapperConnection(xType = NoCrossing, driveClockFromMaster = Some(true), nodeBinding = BIND_STAR)()),
    (L2,  MBUS,  TLBusWrapperConnection.crossTo(
      xType = sbusToMbusXType,
      driveClockFromMaster = None,
      nodeBinding = BIND_QUERY))
  )
)

// This differs from upstream only in that it does not use the legacy crossTo
// and crossFrom functions to ensure driveClockFromMaster = None
case class HierarchicalMulticlockBusTopologyParams(
  pbus: PeripheryBusParams,
  fbus: FrontBusParams,
  cbus: PeripheryBusParams,
  xTypes: SubsystemCrossingParams
) extends TLBusWrapperTopology(
  instantiations = List(
    (PBUS, pbus),
    (FBUS, fbus),
    (CBUS, cbus)),
  connections = List(
    (SBUS, CBUS, TLBusWrapperConnection(xType = xTypes.sbusToCbusXType, nodeBinding = BIND_STAR)()),
    (CBUS, PBUS, TLBusWrapperConnection(xType = xTypes.cbusToPbusXType, nodeBinding = BIND_STAR)()),
    (FBUS, SBUS, TLBusWrapperConnection(xType = xTypes.fbusToSbusXType, nodeBinding = BIND_QUERY, flipRendering = true)()))
)

// For subsystem/Configs.scala

class WithMulticlockCoherentBusTopology extends Config((site, here, up) => {
  case TLNetworkTopologyLocated(InSubsystem) => List(
    JustOneBusTopologyParams(sbus = site(SystemBusKey)),
    HierarchicalMulticlockBusTopologyParams(
      pbus = site(PeripheryBusKey),
      fbus = site(FrontBusKey),
      cbus = site(ControlBusKey),
      xTypes = SubsystemCrossingParams(
        sbusToCbusXType = site(SbusToCbusXTypeKey),
        cbusToPbusXType = site(CbusToPbusXTypeKey),
        fbusToSbusXType = site(FbusToSbusXTypeKey))),
    CoherentMulticlockBusTopologyParams(
      sbus = site(SystemBusKey),
      mbus = site(MemoryBusKey),
      l2 = site(BankedL2Key),
      sbusToMbusXType = site(SbusToMbusXTypeKey)))
})
