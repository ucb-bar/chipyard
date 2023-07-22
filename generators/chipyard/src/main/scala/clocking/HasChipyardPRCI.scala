package chipyard.clocking

import chisel3._

import scala.collection.mutable.{ArrayBuffer}

import org.chipsalliance.cde.config.{Parameters, Field, Config}
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.devices.tilelink._
import freechips.rocketchip.regmapper._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.util._
import freechips.rocketchip.tile._
import freechips.rocketchip.prci._

import testchipip.{TLTileResetCtrl, ClockGroupFakeResetSynchronizer}

case class ChipyardPRCIControlParams(
  slaveWhere: TLBusWrapperLocation = CBUS,
  baseAddress: BigInt = 0x100000,
  enableTileClockGating: Boolean = true,
  enableTileResetSetting: Boolean = true,
  enableResetSynchronizers: Boolean = true // this should only be disabled to work around verilator async-reset initialization problems
) {
  def generatePRCIXBar = enableTileClockGating || enableTileResetSetting
}


case object ChipyardPRCIControlKey extends Field[ChipyardPRCIControlParams](ChipyardPRCIControlParams())

trait HasChipyardPRCI { this: BaseSubsystem with InstantiatesTiles =>
  require(p(SubsystemDriveAsyncClockGroupsKey).isEmpty, "Subsystem asyncClockGroups must be undriven")

  val prciParams = p(ChipyardPRCIControlKey)

  // Set up clock domain
  private val tlbus = locateTLBusWrapper(prciParams.slaveWhere)
  val prci_ctrl_domain = LazyModule(new ClockSinkDomain(name=Some("chipyard-prci-control")))
  prci_ctrl_domain.clockNode := tlbus.fixedClockNode

  val prci_ctrl_bus = Option.when(prciParams.generatePRCIXBar) { prci_ctrl_domain { TLXbar() } }
  prci_ctrl_bus.foreach(xbar => tlbus.coupleTo("prci_ctrl") { (xbar
    := TLFIFOFixer(TLFIFOFixer.all)
    := TLBuffer()
    := _)
  })

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
  val frequencySpecifier = ClockGroupFrequencySpecifier(p(ClockFrequencyAssignersKey))
  val clockGroupCombiner = ClockGroupCombiner()
  val resetSynchronizer  = prci_ctrl_domain {
    if (prciParams.enableResetSynchronizers) ClockGroupResetSynchronizer() else ClockGroupFakeResetSynchronizer()
  }
  val tileClockGater     = Option.when(prciParams.enableTileClockGating) { prci_ctrl_domain {
    val clock_gater = LazyModule(new TileClockGater(prciParams.baseAddress + 0x00000, tlbus.beatBytes))
    clock_gater.tlNode := TLFragmenter(tlbus.beatBytes, tlbus.blockBytes) := prci_ctrl_bus.get
    clock_gater
  } }
  val tileResetSetter    = Option.when(prciParams.enableTileResetSetting) { prci_ctrl_domain {
    val reset_setter = LazyModule(new TileResetSetter(prciParams.baseAddress + 0x10000, tlbus.beatBytes,
      tile_prci_domains.map(_.tile_reset_domain.clockNode.portParams(0).name.get), Nil))
    reset_setter.tlNode := TLFragmenter(tlbus.beatBytes, tlbus.blockBytes) := prci_ctrl_bus.get
    reset_setter
  } }

  if (!prciParams.enableResetSynchronizers) {
    println(Console.RED + s"""

!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!

WARNING:

DISABLING THE RESET SYNCHRONIZERS RESULTS IN
A BROKEN DESIGN THAT WILL NOT BEHAVE
PROPERLY AS ASIC OR FPGA.

THESE SHOULD ONLY BE DISABLED TO WORK AROUND
LIMITATIONS IN ASYNC RESET INITIALIZATION IN
RTL SIMULATORS, NAMELY VERILATOR.

!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
""" + Console.RESET)
  }

  (aggregator
    := frequencySpecifier
    := clockGroupCombiner
    := resetSynchronizer
    := tileClockGater.map(_.clockNode).getOrElse(ClockGroupEphemeralNode()(ValName("temp")))
    := tileResetSetter.map(_.clockNode).getOrElse(ClockGroupEphemeralNode()(ValName("temp")))
    := allClockGroupsNode)
}
