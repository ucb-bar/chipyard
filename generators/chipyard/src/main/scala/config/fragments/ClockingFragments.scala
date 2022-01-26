package chipyard.config

import scala.util.matching.Regex
import chisel3._
import chisel3.util.{log2Up}

import freechips.rocketchip.config.{Field, Parameters, Config}
import freechips.rocketchip.subsystem._
import freechips.rocketchip.prci._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.util.{Symmetric}
import freechips.rocketchip.tilelink.{HasTLBusParams}

import chipyard._
import chipyard.clocking._


// The default RocketChip BaseSubsystem drives its diplomatic clock graph
// with the implicit clocks of Subsystem. Don't do that, instead we extend
// the diplomacy graph upwards into the ChipTop, where we connect it to
// our clock drivers
class WithNoSubsystemDrivenClocks extends Config((site, here, up) => {
  case SubsystemDriveAsyncClockGroupsKey => None
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

class WithPeripheryBusFrequencyAsDefault extends Config((site, here, up) => {
  case DefaultClockFrequencyKey => (site(PeripheryBusKey).dtsFrequency.get / (1000 * 1000)).toDouble
})

class WithSystemBusFrequencyAsDefault extends Config((site, here, up) => {
  case DefaultClockFrequencyKey => (site(SystemBusKey).dtsFrequency.get / (1000 * 1000)).toDouble
})

class BusFrequencyAssignment[T <: HasTLBusParams](re: Regex, key: Field[T]) extends Config((site, here, up) => {
  case ClockFrequencyAssignersKey => up(ClockFrequencyAssignersKey, site) ++
    Seq((cName: String) => site(key).dtsFrequency.flatMap { f =>
      re.findFirstIn(cName).map {_ => (f / (1000 * 1000)).toDouble }
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
class WithPeripheryBusFrequency(freqMHz: Double) extends Config((site, here, up) => {
  case PeripheryBusKey => up(PeripheryBusKey, site).copy(dtsFrequency = Some(BigInt((freqMHz * 1e6).toLong)))
})
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

class WithRationalMemoryBusCrossing extends WithSbusToMbusCrossingType(RationalCrossing(Symmetric))
class WithAsynchrousMemoryBusCrossing extends WithSbusToMbusCrossingType(AsynchronousCrossing())

class WithTestChipBusFreqs extends Config(
  // Frequency specifications
  new chipyard.config.WithTileFrequency(1600.0) ++       // Matches the maximum frequency of U540
  new chipyard.config.WithSystemBusFrequency(800.0) ++   // Put the system bus at a lower freq, representative of ncore working at a lower frequency than the tiles. Same freq as U540
  new chipyard.config.WithMemoryBusFrequency(1000.0) ++  // 2x the U540 freq (appropriate for a 128b Mbus)
  new chipyard.config.WithPeripheryBusFrequency(800.0) ++  // Match the sbus and pbus frequency
  new chipyard.config.WithSystemBusFrequencyAsDefault ++ // All unspecified clock frequencies, notably the implicit clock, will use the sbus freq (800 MHz)
  //  Crossing specifications
  new chipyard.config.WithCbusToPbusCrossingType(AsynchronousCrossing()) ++ // Add Async crossing between PBUS and CBUS
  new chipyard.config.WithSbusToMbusCrossingType(AsynchronousCrossing()) ++ // Add Async crossings between backside of L2 and MBUS
  new freechips.rocketchip.subsystem.WithRationalRocketTiles ++   // Add rational crossings between RocketTile and uncore
  new boom.common.WithRationalBoomTiles ++ // Add rational crossings between BoomTile and uncore
  new testchipip.WithAsynchronousSerialSlaveCrossing // Add Async crossing between serial and MBUS. Its master-side is tied to the FBUS
)
