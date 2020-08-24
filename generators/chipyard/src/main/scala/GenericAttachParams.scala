
package chipyard

import freechips.rocketchip.diplomacy._
import freechips.rocketchip.config.{Field, Parameters}
import freechips.rocketchip.subsystem._
import freechips.rocketchip.tile.{LookupByHartIdImpl, TileParams, InstantiableTileParams, BaseTile}

import chipyard.clocking.ClockNodeInjectionUtils._

case class GenericCrossingParams(
    crossingType: ClockCrossingType = SynchronousCrossing(),
    master: TilePortParamsLike = TileMasterPortParams(),
    slave: TilePortParamsLike = TileSlavePortParams(),
    mmioBaseAddressPrefixWhere: TLBusWrapperLocation = CBUS,
    injectClockNodeFunc: InjectClockNodeFunc = injectIdentityClockNode,
    forceSeparateClockReset: Boolean = false) extends TileCrossingParamsLike {

  def injectClockNode(a: Attachable)(implicit p: Parameters) = injectClockNodeFunc(a, p)
}

object GenericCrossingParams {
  def apply(params: TileCrossingParamsLike): GenericCrossingParams = GenericCrossingParams(
    params.crossingType,
    params.master,
    params.slave,
    params.mmioBaseAddressPrefixWhere,
    (a: Attachable, p: Parameters) => params.injectClockNode(a)(p),
    params.forceSeparateClockReset)
}

case class GenericallyAttachableTile[TT <: BaseTile](
    tileParams: InstantiableTileParams[TT],
    crossingParams: GenericCrossingParams,
    lookup: LookupByHartIdImpl) extends CanAttachTile {
  type TileType = TT
}

