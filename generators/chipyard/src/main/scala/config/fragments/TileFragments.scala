package chipyard.config

import chisel3._

import org.chipsalliance.cde.config.{Field, Parameters, Config}
import freechips.rocketchip.tile._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.rocket.{RocketCoreParams, MulDivParams, DCacheParams, ICacheParams}
import freechips.rocketchip.diplomacy._

import testchipip.cosim.{TracePortKey, TracePortParams}
import barf.{TilePrefetchingMasterPortParams}
import freechips.rocketchip.trace.{TraceEncoderParams, TraceCoreParams}
import shuttle.common.{ShuttleTileAttachParams}

// Static plugin discovery for optional generators via Java ServiceLoader.
// Optional generators can implement TilePluginProvider.
import scala.jdk.CollectionConverters._
import org.reflections.Reflections
import freechips.rocketchip.subsystem.HierarchicalElementPortParamsLike

trait TilePluginProvider {
  def tileTraceEnableInjectors: Seq[PartialFunction[Any, Any]] = Nil
  def tileTraceDisableInjectors: Seq[PartialFunction[Any, Any]] = Nil
  def tilePrefetchInjectors(make: (Int, HierarchicalElementPortParamsLike) => HierarchicalElementPortParamsLike): Seq[PartialFunction[Any, Any]] = Nil
}

private object TilePlugins {
  private lazy val providers: Seq[TilePluginProvider] = {
    val reflections = new Reflections("chipyard")
    val subs = reflections.getSubTypesOf(classOf[TilePluginProvider]).asScala.toSeq.distinct
    subs.flatMap { cls =>
      try Some(cls.getDeclaredConstructor().newInstance())
      catch { case _: Throwable => None }
    }
  }

  def traceEnableInjectors: Seq[PartialFunction[Any, Any]] =
    providers.flatMap(_.tileTraceEnableInjectors)
  def traceDisableInjectors: Seq[PartialFunction[Any, Any]] =
    providers.flatMap(_.tileTraceDisableInjectors)
  def prefetchInjectors(make: (Int, HierarchicalElementPortParamsLike) => HierarchicalElementPortParamsLike): Seq[PartialFunction[Any, Any]] =
    providers.flatMap(_.tilePrefetchInjectors(make))

  def applyInjectors[A](tp: A, injectors: Seq[PartialFunction[Any, Any]]): A = {
    var acc: Any = tp
    injectors.foreach { pf => if (pf.isDefinedAt(acc)) acc = pf(acc) }
    acc.asInstanceOf[A]
  }
}

class WithL2TLBs(entries: Int) extends Config((site, here, up) => {
  case TilesLocated(InSubsystem) => up(TilesLocated(InSubsystem), site) map {
    case tp: RocketTileAttachParams => tp.copy(tileParams = tp.tileParams.copy(
      core = tp.tileParams.core.copy(nL2TLBEntries = entries)))
    case tp: boom.v3.common.BoomTileAttachParams => tp.copy(tileParams = tp.tileParams.copy(
      core = tp.tileParams.core.copy(nL2TLBEntries = entries)))
    case tp: boom.v4.common.BoomTileAttachParams => tp.copy(tileParams = tp.tileParams.copy(
      core = tp.tileParams.core.copy(nL2TLBEntries = entries)))
    case other => other
  }
})

class WithTraceIO extends Config((site, here, up) => {
  case TilesLocated(InSubsystem) => up(TilesLocated(InSubsystem), site) map { tp =>
    val updated = tp match {
      case tp: boom.v3.common.BoomTileAttachParams => tp.copy(tileParams = tp.tileParams.copy(
        core = tp.tileParams.core.copy(trace = true)))
      case tp: boom.v4.common.BoomTileAttachParams => tp.copy(tileParams = tp.tileParams.copy(
        core = tp.tileParams.core.copy(trace = true)))
      case other => other
    }
    TilePlugins.applyInjectors(updated, TilePlugins.traceEnableInjectors)
  }
  case TracePortKey => Some(TracePortParams())
})

class WithNoTraceIO extends Config((site, here, up) => {
  case TilesLocated(InSubsystem) => up(TilesLocated(InSubsystem), site) map { tp =>
    val updated = tp match {
      case tp: boom.v3.common.BoomTileAttachParams => tp.copy(tileParams = tp.tileParams.copy(
        core = tp.tileParams.core.copy(trace = false)))
      case tp: boom.v4.common.BoomTileAttachParams => tp.copy(tileParams = tp.tileParams.copy(
        core = tp.tileParams.core.copy(trace = false)))
      case other => other
    }
    TilePlugins.applyInjectors(updated, TilePlugins.traceDisableInjectors)
  }
  case TracePortKey => None
})

class WithNPerfCounters(n: Int = 29) extends Config((site, here, up) => {
  case TilesLocated(InSubsystem) => up(TilesLocated(InSubsystem), site) map {
    case tp: RocketTileAttachParams => tp.copy(tileParams = tp.tileParams.copy(
      core = tp.tileParams.core.copy(nPerfCounters = n)))
    case tp: boom.v3.common.BoomTileAttachParams => tp.copy(tileParams = tp.tileParams.copy(
      core = tp.tileParams.core.copy(nPerfCounters = n)))
    case tp: boom.v4.common.BoomTileAttachParams => tp.copy(tileParams = tp.tileParams.copy(
      core = tp.tileParams.core.copy(nPerfCounters = n)))
    case other => other
  }
})

// Add a monitor to RTL print the sinked packets into a file for debugging
class WithTraceArbiterMonitor extends Config((site, here, up) => {
  case TilesLocated(InSubsystem) => up(TilesLocated(InSubsystem), site) map {
    case tp: RocketTileAttachParams => tp.copy(tileParams = tp.tileParams.copy(
      traceParams = Some(tp.tileParams.traceParams.get.copy(useArbiterMonitor = true))))
    case tp: ShuttleTileAttachParams => tp.copy(tileParams = tp.tileParams.copy(
      traceParams = Some(tp.tileParams.traceParams.get.copy(useArbiterMonitor = true))))
  }
})

class WithNPMPs(n: Int = 8) extends Config((site, here, up) => {
  case TilesLocated(InSubsystem) => up(TilesLocated(InSubsystem), site) map {
    case tp: RocketTileAttachParams => tp.copy(tileParams = tp.tileParams.copy(
      core = tp.tileParams.core.copy(nPMPs = n)))
    case tp: boom.v3.common.BoomTileAttachParams => tp.copy(tileParams = tp.tileParams.copy(
      core = tp.tileParams.core.copy(nPMPs = n)))
    case tp: boom.v4.common.BoomTileAttachParams => tp.copy(tileParams = tp.tileParams.copy(
      core = tp.tileParams.core.copy(nPMPs = n)))
    case tp: chipyard.SpikeTileAttachParams => tp.copy(tileParams = tp.tileParams.copy(
      core = tp.tileParams.core.copy(nPMPs = n)))
    case other => other
  }
})


class WithRocketICacheScratchpad extends Config((site, here, up) => {
  case TilesLocated(InSubsystem) => up(TilesLocated(InSubsystem), site) map {
    case tp: RocketTileAttachParams => tp.copy(tileParams = tp.tileParams.copy(
      icache = tp.tileParams.icache.map(_.copy(itimAddr = Some(0x300000 + tp.tileParams.tileId * 0x10000)))
    ))
  }
})

class WithRocketDCacheScratchpad extends Config((site, here, up) => {
  case TilesLocated(InSubsystem) => up(TilesLocated(InSubsystem), site) map {
    case tp: RocketTileAttachParams => tp.copy(tileParams = tp.tileParams.copy(
      dcache = tp.tileParams.dcache.map(_.copy(nSets = 32, nWays = 1, scratch = Some(0x200000 + tp.tileParams.tileId * 0x10000)))
    ))
  }
})

class WithTilePrefetchers extends Config((site, here, up) => {
  case TilesLocated(InSubsystem) => up(TilesLocated(InSubsystem), site) map { tp =>
    val updated = tp match {
      case tp: RocketTileAttachParams => tp.copy(crossingParams = tp.crossingParams.copy(
        master = TilePrefetchingMasterPortParams(tp.tileParams.tileId, tp.crossingParams.master)))
      case tp: boom.v3.common.BoomTileAttachParams => tp.copy(crossingParams = tp.crossingParams.copy(
        master = TilePrefetchingMasterPortParams(tp.tileParams.tileId, tp.crossingParams.master)))
      case tp: boom.v4.common.BoomTileAttachParams => tp.copy(crossingParams = tp.crossingParams.copy(
        master = TilePrefetchingMasterPortParams(tp.tileParams.tileId, tp.crossingParams.master)))
      case other => other
    }
    val make = (tileId: Int, master: HierarchicalElementPortParamsLike) =>
      barf.TilePrefetchingMasterPortParams(tileId, master)
    TilePlugins.applyInjectors(updated, TilePlugins.prefetchInjectors(make))
  }
})

// Use SV48
class WithSV48 extends Config((site, here, up) => {
  case TilesLocated(loc) => up(TilesLocated(loc), site) map {
    case tp: RocketTileAttachParams => tp.copy(tileParams = tp.tileParams.copy(core =
      tp.tileParams.core.copy(pgLevels = 4)))
    case tp: boom.v3.common.BoomTileAttachParams => tp.copy(tileParams = tp.tileParams.copy(core =
      tp.tileParams.core.copy(pgLevels = 4)))
    case tp: boom.v4.common.BoomTileAttachParams => tp.copy(tileParams = tp.tileParams.copy(core =
      tp.tileParams.core.copy(pgLevels = 4)))
  }
})
