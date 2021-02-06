package chipyard.clocking

import chisel3._
import chisel3.experimental.{IO}
import freechips.rocketchip.config.{Parameters, Field}
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.devices.tilelink._
import freechips.rocketchip.regmapper._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.util._
import freechips.rocketchip.tile._
import freechips.rocketchip.prci.{ClockSinkDomain, ClockGroupIdentityNode}

case class TileClockMuxParams(
  address: BigInt=0x101000,
  secondaryClockName: String = "subsystem_sbus_0",
  slaveWhere: TLBusWrapperLocation = PBUS)
case object TileClockMuxKey extends Field[TileClockMuxParams](TileClockMuxParams())

object TLTileClockMuxes {
  def apply(sys: BaseSubsystem with InstantiatesTiles)(implicit p: Parameters) = {
    val tileClockMuxParams = p(TileClockMuxKey)
    val tlbus = sys.locateTLBusWrapper(tileClockMuxParams.slaveWhere)
    val domain = sys { LazyModule(new ClockSinkDomain(name=Some("tile-clock-mux"))) }
    domain.clockNode := tlbus.fixedClockNode
    val clockMux = domain {
      LazyModule(new TLTileClockMuxes(tlbus.beatBytes, tileClockMuxParams, sys.tile_prci_domains))
    }
    tlbus.toVariableWidthSlave(Some("tile-clock-mux")) { clockMux.node := TLBuffer() }
    clockMux.tileClockMuxNode
  }
}

class TLTileClockMuxes(w: Int, params: TileClockMuxParams, tile_prci_domains: Seq[TilePRCIDomain[_]])
    (implicit p: Parameters) extends LazyModule {
  val device = new SimpleDevice("tile-clock-mux", Nil)
  val node = TLRegisterNode(Seq(AddressSet(params.address, 4096-1)), device, "reg/control", beatBytes=w)
  val tileClockMuxNode = ClockGroupIdentityNode()

  lazy val module = new LazyModuleImp(this) {
    require(tileClockMuxNode.out.size == 1)

    val nTiles = p(TilesLocated(InSubsystem)).size
    require (nTiles <= 4096)

    val clock_select_regs = Seq.fill(nTiles)(RegInit(false.B))
 //Module(new AsyncResetRegVec(w=1, init=(if (params.initResetHarts.contains(i)) 1 else 0)))
    node.regmap((0 until nTiles).map { i =>
      i -> Seq(RegField.w(1, RegWriteFn(clock_select_regs(i))))
    }: _*)


    val tileMap = tile_prci_domains.zipWithIndex.map({ case (d, i) =>
        d.tile_reset_domain.clockNode.portParams(0).name.get -> clock_select_regs(i)
    })

    tileClockMuxNode.in.head._1.member.elements foreach println
    val secondaryClock = tileClockMuxNode.in.head._1.member.elements.collectFirst {
      case (name, clockB) if name.contains(params.secondaryClockName) => clockB.clock
    }.get

    (tileClockMuxNode.out zip tileClockMuxNode.in).foreach { case ((o, _), (i, _)) =>
      (o.member.elements zip i.member.elements).foreach { case ((name, oD), (_, iD)) =>
        for ((n, sel) <- tileMap) {
          if (name.contains(n)) {
            // This is not safe to use. Put in a as a placeholder.
            val clockMux = Module(new testchipip.ClockMux2)
            oD.clock := clockMux.io.clockOut
            clockMux.io.clocksIn(0) := iD.clock
            clockMux.io.clocksIn(1) := secondaryClock
            clockMux.io.sel := sel
            midas.widgets.BridgeableClockMux(
              clockMux,
              clockMux.io.clocksIn(0),
              clockMux.io.clocksIn(1),
              clockMux.io.clockOut,
              clockMux.io.sel)
          }
        }
      }
    }
  }
}

