package testchipip.soc

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config.{Parameters, Field}
import freechips.rocketchip.subsystem._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.devices.debug.HasPeripheryDebug
import freechips.rocketchip.devices.tilelink._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.util._
import freechips.rocketchip.prci._
import scala.math.min
import testchipip.util.{TLSwitch}

// "off-chip" bus, TL bus which connects off-chip tilelink memories/devices
case object OBUS extends TLBusWrapperLocation("subsystem_obus")
case object OffchipBusKey extends Field[OffchipBusParams](OffchipBusParams(1, 1)) // default settings are non-sensical

case class OffchipBusTopologyParams(
  obus: OffchipBusParams
) extends TLBusWrapperTopology(
  instantiations = List((OBUS, obus)),
  connections = Nil
)

// location: bus which masters this bus
// offchipOffset: add this to the address before sending it off-chip
case class OffchipBusTopologyConnectionParams(
  location: TLBusWrapperLocation,
  blockRange: Seq[AddressSet] = Nil, // offchip addresses which will not be accessible through this port
  replicationBase: Option[BigInt] = None // the offchip address region will be replicated above this address
) extends TLBusWrapperTopology(
  instantiations = Nil,
  connections = List((location, OBUS, TLBusWrapperConnection(driveClockFromMaster = Some(true))(
    inject = (q: Parameters) => {
      implicit val p: Parameters = q
      val filter = if (blockRange.isEmpty) { TLTempNode() } else {
        TLFilter(TLFilter.mSubtract(blockRange))(p)
      }
      val replicator = replicationBase.map { base =>
        val baseRegion = AddressSet(0, base-1)
        val offsetRegion = AddressSet(base, base-1)
        val replicator = LazyModule(new RegionReplicator(ReplicatedRegion(baseRegion, baseRegion.widen(base))))
        val prefixSource = BundleBridgeSource[UInt](() => UInt(1.W))
        replicator.prefix := prefixSource
        // prefix is unused for TL uncached, so this is ok
        InModuleBody { prefixSource.bundle := 0.U(1.W) }
        replicator.node
      }.getOrElse { TLTempNode() }
      replicator := filter
    }
  )))
)

case class OffchipBusParams(
  beatBytes: Int,
  blockBytes: Int,
  dtsFrequency: Option[BigInt] = None
)
  extends HasTLBusParams
  with TLBusWrapperInstantiationLike
{
  def instantiate(context: HasTileLinkLocations, loc: Location[TLBusWrapper])(implicit p: Parameters): OffchipBus = {
    val obus = LazyModule(new OffchipBus(this, loc.name))
    obus.suggestName(loc.name)
    context.tlBusWrapperLocationMap += (loc -> obus)
    obus
  }
}

class OffchipBus(params: OffchipBusParams, name: String = "offchip_bus")(implicit p: Parameters)
    extends TLBusWrapper(params, name)
{
  private val offchip_bus_switch = LazyModule(new TLSwitch)
  val inwardNode: TLInwardNode = offchip_bus_switch.node :=* TLFIFOFixer(TLFIFOFixer.allVolatile)
  val outwardNode: TLOutwardNode = offchip_bus_switch.node
  def busView: TLEdge = offchip_bus_switch.node.edges.in.head
  val builtInDevices = BuiltInDevices.none
  val prefixNode = None
  val io_sel = InModuleBody {
    val io_sel = offchip_bus_switch.module.io.sel.map(s => IO(Input(s.cloneType)))
    offchip_bus_switch.module.io.sel.foreach(_ := io_sel.get)
    io_sel
  }
}

trait CanHaveSwitchableOffchipBus { this: BaseSubsystem =>
  val io_obus_sel = InModuleBody {
    tlBusWrapperLocationMap.lift(OBUS).map(_.asInstanceOf[OffchipBus].io_sel.getWrappedValue).flatten.map(s => {
      val io_obus_sel = IO(Input(s.cloneType))
      s := io_obus_sel
      io_obus_sel
    })
  }
}
