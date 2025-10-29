package testchipip.boot

import chisel3._
import org.chipsalliance.cde.config.{Parameters, Field}
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.devices.tilelink._
import freechips.rocketchip.regmapper._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.util._
import freechips.rocketchip.tile._
import freechips.rocketchip.prci._

// initResetHarts: list of hartids which will stay in reset until its reset-ctrl register is cleared
case class TileResetCtrlParams(initResetHarts: Seq[Int] = Nil, address: BigInt=0x100000, slaveWhere: TLBusWrapperLocation = PBUS)
case object TileResetCtrlKey extends Field[TileResetCtrlParams](TileResetCtrlParams())

object TLTileResetCtrl {
  def apply(sys: BaseSubsystem with InstantiatesHierarchicalElements)(implicit p: Parameters) = {
    val resetCtrlParams = p(TileResetCtrlKey)
    val tlbus = sys.locateTLBusWrapper(resetCtrlParams.slaveWhere)
    val domain = sys { tlbus.generateSynchronousDomain.suggestName("tile_reset_domain") }
    val resetCtrl = domain {
      LazyModule(new TLTileResetCtrl(tlbus.beatBytes, resetCtrlParams, sys.element_prci_domains))
    }
    tlbus.coupleTo("tile-reset-ctrl") { resetCtrl.node := TLBuffer() := _ }
    resetCtrl
  }
}

class TLTileResetCtrl(w: Int, params: TileResetCtrlParams, element_prci_domains: Seq[HierarchicalElementPRCIDomain[_]])(implicit p: Parameters) extends LazyModule {
  val device = new SimpleDevice("tile-reset-ctrl", Nil)
  val node = TLRegisterNode(Seq(AddressSet(params.address, 4096-1)), device, "reg/control", beatBytes=w)
  val tileResetProviderNode = ClockGroupIdentityNode()
  val asyncResetSinkNode = ClockSinkNode(Seq(ClockSinkParameters()))

  lazy val module = new LazyModuleImp(this) {
    val nTiles = p(TilesLocated(InSubsystem)).size
    require (nTiles <= 4096 / 4)
    val r_tile_resets = (0 until nTiles).map({ i =>
      withReset (asyncResetSinkNode.in.head._1.reset) {
        Module(new AsyncResetRegVec(w=1, init=(if (params.initResetHarts.contains(i)) 1 else 0)))
      }
    })
    node.regmap((0 until nTiles).map({ i =>
      i * 4 -> Seq(RegField.rwReg(1, r_tile_resets(i).io))
    }): _*)

    val tileMap = element_prci_domains.zipWithIndex.map({ case (d, i) =>
        d.element_reset_domain.clockNode.portParams(0).name.get -> r_tile_resets(i).io.q
    })
    (tileResetProviderNode.out zip tileResetProviderNode.in).map { case ((o, _), (i, _)) =>
      (o.member.elements zip i.member.elements).foreach { case ((name, oD), (_, iD)) =>
        oD.clock := iD.clock
        oD.reset := iD.reset
        for ((n, r) <- tileMap) {
          if (name.contains(n)) {
            // Async because the reset coming out of the AsyncResetRegVec is
            // clocked to the bus this is attached to, not the clock in this
            // clock bundle. We expect a ClockGroupResetSynchronizer downstream
            // to synchronize the resets
            // Also, this or enforces that the tiles come out of reset after the reset of the system
            oD.reset := (r.asBool || asyncResetSinkNode.in.head._1.reset.asBool).asAsyncReset
          }
        }
      }
    }
  }
}

