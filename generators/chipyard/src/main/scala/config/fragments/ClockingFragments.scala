package chipyard.config

import scala.util.matching.Regex
import chisel3._
import chisel3.util.{log2Up}

import org.chipsalliance.cde.config.{Field, Parameters, Config}
import freechips.rocketchip.subsystem._
import freechips.rocketchip.prci._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.util.{Symmetric}
import freechips.rocketchip.tilelink.{HasTLBusParams}

import chipyard._
import chipyard.clocking._
import testchipip.soc.{OffchipBusKey}

// The default RocketChip BaseSubsystem drives its diplomatic clock graph
// with the implicit clocks of Subsystem. Don't do that, instead we extend
// the diplomacy graph upwards into the ChipTop, where we connect it to
// our clock drivers
class WithNoSubsystemClockIO extends Config((site, here, up) => {
  case SubsystemDriveClockGroupsFromIO => false
})

/**
  * Mixins to define either a specific tile frequency for a single hart or all harts
  *
  * @param fMHz Frequency in MHz of the tile or all tiles
  * @param hartId Optional hartid to assign the frequency to (if unspecified default to all harts)
  */
class WithTileFrequency(fMHz: Double, hartId: Option[Int] = None) extends ClockNameContainsAssignment({
    hartId match {
      case Some(id) => s"tile_$id"
      case None => "tile"
    }
  },
  fMHz)

class BusFrequencyAssignment[T <: HasTLBusParams](re: Regex, key: Field[T]) extends Config((site, here, up) => {
  case ClockFrequencyAssignersKey => up(ClockFrequencyAssignersKey, site) ++
    Seq((cName: String) => site(key).dtsFrequency.flatMap { f =>
      re.findFirstIn(cName).map {_ => (f.toDouble / (1000 * 1000)) }
    })
})

/**
  * Provides a diplomatic frequency for all clock sinks with an unspecified
  * frequency bound to each bus.
  *
  * For example, the L2 cache, when bound to the sbus, receives a separate
  * clock that appears as "subsystem_sbus_<num>".  This fragment ensures that
  * clock requests the same frequency as the sbus itself.
  */

class WithInheritBusFrequencyAssignments extends Config(
  new BusFrequencyAssignment("subsystem_sbus_\\d+".r, SystemBusKey) ++
  new BusFrequencyAssignment("subsystem_pbus_\\d+".r, PeripheryBusKey) ++
  new BusFrequencyAssignment("subsystem_cbus_\\d+".r, ControlBusKey) ++
  new BusFrequencyAssignment("subsystem_fbus_\\d+".r, FrontBusKey) ++
  new BusFrequencyAssignment("subsystem_mbus_\\d+".r, MemoryBusKey)
)

/**
  * Mixins to specify crossing types between the 5 traditional TL buses
  *
  * Note: these presuppose the legacy connections between buses and set
  * parameters in SubsystemCrossingParams; they may not be resuable in custom
  * topologies (but you can specify the desired crossings in your topology).
  *
  * @param xType The clock crossing type
  *
  */

class WithSbusToMbusCrossingType(xType: ClockCrossingType) extends Config((site, here, up) => {
    case SbusToMbusXTypeKey => xType
})
class WithSbusToCbusCrossingType(xType: ClockCrossingType) extends Config((site, here, up) => {
    case SbusToCbusXTypeKey => xType
})
class WithCbusToPbusCrossingType(xType: ClockCrossingType) extends Config((site, here, up) => {
    case CbusToPbusXTypeKey => xType
})
class WithFbusToSbusCrossingType(xType: ClockCrossingType) extends Config((site, here, up) => {
    case FbusToSbusXTypeKey => xType
})

/**
  * Mixins to set the dtsFrequency field of BusParams -- these will percolate its way
  * up the diplomatic graph to the clock sources.
  */
class WithPeripheryBusFrequency(freqMHz: Double) extends Config(
  new freechips.rocketchip.subsystem.WithTimebase((freqMHz * 1e3).toLong) ++ // Match DTS timebase to PBUS (i.e. RTC) frequency. Makes RTC 'tick' at the PBUS rate.
  new Config((site, here, up) => {
    case PeripheryBusKey => up(PeripheryBusKey, site).copy(dtsFrequency = Some(BigInt((freqMHz * 1e6).toLong)))
  })
)
class WithMemoryBusFrequency(freqMHz: Double) extends Config((site, here, up) => {
  case MemoryBusKey => up(MemoryBusKey, site).copy(dtsFrequency = Some(BigInt((freqMHz * 1e6).toLong)))
})
class WithSystemBusFrequency(freqMHz: Double) extends Config((site, here, up) => {
  case SystemBusKey => up(SystemBusKey, site).copy(dtsFrequency = Some(BigInt((freqMHz * 1e6).toLong)))
})
class WithFrontBusFrequency(freqMHz: Double) extends Config((site, here, up) => {
  case FrontBusKey => up(FrontBusKey, site).copy(dtsFrequency = Some(BigInt((freqMHz * 1e6).toLong)))
})
class WithControlBusFrequency(freqMHz: Double) extends Config((site, here, up) => {
  case ControlBusKey => up(ControlBusKey, site).copy(dtsFrequency = Some(BigInt((freqMHz * 1e6).toLong)))
})
class WithOffchipBusFrequency(freqMHz: Double) extends Config((site, here, up) => {
  case OffchipBusKey => up(OffchipBusKey, site).copy(dtsFrequency = Some(BigInt((freqMHz * 1e6).toLong)))
})
class WithUniformBusFrequencies(freqMHz: Double) extends Config(
  new WithPeripheryBusFrequency(freqMHz) ++
  new WithSystemBusFrequency(freqMHz) ++
  new WithFrontBusFrequency(freqMHz) ++
  new WithControlBusFrequency(freqMHz) ++
  new WithOffchipBusFrequency(freqMHz) ++
  new WithMemoryBusFrequency(freqMHz)
)

class WithRationalMemoryBusCrossing extends WithSbusToMbusCrossingType(RationalCrossing(Symmetric))
class WithAsynchrousMemoryBusCrossing extends WithSbusToMbusCrossingType(AsynchronousCrossing())

// Remove the tile clock gaters in this system
class WithNoTileClockGaters extends Config((site, here, up) => {
  case ChipyardPRCIControlKey => up(ChipyardPRCIControlKey).copy(enableTileClockGating = false)
})

// Remove the tile reset control blocks in this system
class WithNoTileResetSetters extends Config((site, here, up) => {
  case ChipyardPRCIControlKey => up(ChipyardPRCIControlKey).copy(enableTileResetSetting = false)
})

// Remove the global reset synchronizers in this system
class WithNoResetSynchronizers extends Config((site, here, up) => {
  case ChipyardPRCIControlKey => up(ChipyardPRCIControlKey).copy(enableResetSynchronizers = false)
})

// Remove any ClockTap ports in this system
class WithNoClockTap extends Config((site, here, up) => {
  case ClockTapKey => false
})

// Adds a reset pipeline after the ResetSynchronizer in chipyard's clock/reset path
// This assists with PD and timing of sync reset
// NOTE: This will likely result in spurious early assertions when reset-assertion
// is propagating through the pipeline. You may ignore these in RTL simulators
class WithSyncResetPipeStages(stages: Int) extends Config((site, here, up) => {
  case ChipyardPRCIControlKey => up(ChipyardPRCIControlKey).copy(resetPipeStages = stages)
})
