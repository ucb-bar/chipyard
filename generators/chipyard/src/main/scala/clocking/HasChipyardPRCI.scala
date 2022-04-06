package chipyard.clocking

import chisel3._

import scala.collection.mutable.{ArrayBuffer}

import freechips.rocketchip.config.{Parameters, Field, Config}
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.devices.tilelink._
import freechips.rocketchip.regmapper._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.util._
import freechips.rocketchip.tile._
import freechips.rocketchip.prci._

import testchipip.{TLTileResetCtrl}
import chipyard.{DefaultClockFrequencyKey}

case class ChipyardPRCIControlParams(
  slaveWhere: TLBusWrapperLocation = CBUS,
  baseAddress: BigInt = 0x100000,
  enableTileClockGating: Boolean = true
)


case object ChipyardPRCIControlKey extends Field[ChipyardPRCIControlParams](ChipyardPRCIControlParams())

trait HasChipyardPRCI { this: BaseSubsystem with InstantiatesTiles =>
  require(p(SubsystemDriveAsyncClockGroupsKey).isEmpty, "Subsystem asyncClockGroups must be undriven")

  val prciParams = p(ChipyardPRCIControlKey)

  // Set up clock domain
  private val tlbus = locateTLBusWrapper(prciParams.slaveWhere)
  val prci_ctrl_domain = LazyModule(new ClockSinkDomain(name=Some("chipyard-prci-control")))
  prci_ctrl_domain.clockNode := tlbus.fixedClockNode

  // Aggregate all the clock groups into a single node
  val aggregator = LazyModule(new ClockGroupAggregator("allClocks")).node
  val allClockGroupsNode = ClockGroupEphemeralNode()

  // There are two "sets" of clocks which must be dealt with

  // 1. The implicit clock from the subsystem. RC is moving away from depending on this
  //    clock, but some modules still use it. Since the implicit clock sink node
  //    is created in the ChipTop (the hierarchy wrapping the subsystem), this function
  //    is provided to allow connecting that clock to the clock aggregator. This function
  //    should be called in the ChipTop context
  def connectImplicitClockSinkNode(sink: ClockSinkNode) = {
    val implicitClockGrouper = this { ClockGroup() }
    (sink
      := implicitClockGrouper
      := aggregator)
  }

  // 2. The rest of the diplomatic clocks in the subsystem are routed to this asyncClockGroupsNode
  val clockNamePrefixer = ClockGroupNamePrefixer()
  (asyncClockGroupsNode
    :*= clockNamePrefixer
    :*= aggregator)


  // Once all the clocks are gathered in the aggregator node, several steps remain
  // 1. Assign frequencies to any clock groups which did not specify a frequency.
  // 2. Combine duplicated clock groups (clock groups which physically should be in the same clock domain)
  // 3. Synchronize reset to each clock group
  // 4. Clock gate the clock groups corresponding to Tiles (if desired).
  // 5. Add reset control registers to the tiles (if desired)
  // The final clock group here contains physically distinct clock domains, which some PRCI node in a
  // diplomatic IOBinder should drive
  val frequencySpecifier = ClockGroupFrequencySpecifier(p(ClockFrequencyAssignersKey), p(DefaultClockFrequencyKey))
  val clockGroupCombiner = ClockGroupCombiner()
  val resetSynchronizer  = ClockGroupResetSynchronizer()
  val tileClockGater     = prci_ctrl_domain {
    TileClockGater(prciParams.baseAddress + 0x00000, tlbus, prciParams.enableTileClockGating)
  }
  val tileResetSetter    = prci_ctrl_domain {
    TileResetSetter(prciParams.baseAddress + 0x10000, tlbus, tile_prci_domains.map(_.tile_reset_domain.clockNode.portParams(0).name.get), Nil)
  }
  (aggregator
    := frequencySpecifier
    := clockGroupCombiner
    := resetSynchronizer
    := tileClockGater
    := tileResetSetter
    := allClockGroupsNode)
}

