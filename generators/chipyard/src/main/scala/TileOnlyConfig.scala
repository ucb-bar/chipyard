package chipyard

import org.chipsalliance.cde.config.{Parameters, Config, Field}
import freechips.rocketchip.diplomacy.{LazyModule, LazyModuleImpLike, LazyRawModuleImp, SynchronousCrossing}
import chipyard.iobinders._
import freechips.rocketchip.tile.{RocketTileParams, RocketTile, RocketTileModuleImp, PriorityMuxHartIdFromSeq}
import freechips.rocketchip.subsystem.RocketCrossingParams

case object RocketTileOnly extends Field[RocketTileParams]

// class TileOnlyChipTop(implicit p: Parameters) extends LazyModule with HasIOBinders {
class TileOnlyChipTop(implicit p: Parameters) extends LazyModule {
  lazy val lazySystem = LazyModule(p(BuildSystem)(p)).suggestName("TileOnlySystem")
  lazy val module: LazyModuleImpLike = new LazyRawModuleImp(this) { }
}

class TileOnlyDigitalTop(implicit p: Parameters) extends LazyModule {
  val tile = LazyModule(new RocketTile(p(RocketTileOnly), RocketCrossingParams(), PriorityMuxHartIdFromSeq(Seq(p(RocketTileOnly)))))
  override lazy val module = new RocketTileModuleImp(outer=tile)
}

class WithTileOnlyTop extends Config((site, here, up) => {
  case BuildSystem => (p: Parameters) => new TileOnlyDigitalTop()(p)
})

class WithRawRocketTileConfig extends Config((site, here, up) => {
  case RocketTileOnly => RocketTileParams()
})


class TileOnlyRocketConfig extends Config(
  new chipyard.WithRawRocketTileConfig ++
  new chipyard.WithTileOnlyTop
)