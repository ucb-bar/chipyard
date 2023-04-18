package chipyard

import scala.collection.mutable.{LinkedHashSet}

import freechips.rocketchip.subsystem._
import freechips.rocketchip.tile.{XLen, TileParams}
import org.chipsalliance.cde.config.{Parameters, Field, Config}
import freechips.rocketchip.system.{TestGeneration, RegressionTestSuite, RocketTestSuite}

/**
 * A set of pre-chosen regression tests
 */
object RegressionTestSuites
{
  val rv64RegrTestNames = LinkedHashSet(
    "rv64ud-v-fcvt",
    "rv64ud-p-fdiv",
    "rv64ud-v-fadd",
    "rv64uf-v-fadd",
    "rv64um-v-mul",
    "rv64mi-p-breakpoint",
    "rv64uc-v-rvc",
    "rv64ud-v-structural",
    "rv64si-p-wfi",
    "rv64um-v-divw",
    "rv64ua-v-lrsc",
    "rv64ui-v-fence_i",
    "rv64ud-v-fcvt_w",
    "rv64uf-v-fmin",
    "rv64ui-v-sb",
    "rv64ua-v-amomax_d",
    "rv64ud-v-move",
    "rv64ud-v-fclass",
    "rv64ua-v-amoand_d",
    "rv64ua-v-amoxor_d",
    "rv64si-p-sbreak",
    "rv64ud-v-fmadd",
    "rv64uf-v-ldst",
    "rv64um-v-mulh",
    "rv64si-p-dirty")

  val rv32RegrTestNames = LinkedHashSet(
    "rv32mi-p-ma_addr",
    "rv32mi-p-csr",
    "rv32ui-p-sh",
    "rv32ui-p-lh",
    "rv32uc-p-rvc",
    "rv32mi-p-sbreak",
    "rv32ui-p-sll")
}

/**
 * Helper functions to add BOOM or Rocket tests
 */
class TestSuiteHelper
{
  import freechips.rocketchip.system.DefaultTestSuites._
  import RegressionTestSuites._
  val suites = collection.mutable.ListMap[String, RocketTestSuite]()
  def addSuite(s: RocketTestSuite) { suites += (s.makeTargetName -> s) }
  def addSuites(s: Seq[RocketTestSuite]) { s.foreach(addSuite) }

  /**
  * Add generic tests (asm, bmark, regression) for all cores.
  */
  def addGenericTestSuites(tiles: Seq[TileParams])(implicit p: Parameters) = {
    val xlen = p(XLen)
    tiles.find(_.hartId == 0).map { tileParams =>
      val coreParams = tileParams.core
      val vm = coreParams.useVM
      val env = if (vm) List("p","v") else List("p")
      coreParams.fpu foreach { case cfg =>
        if (xlen == 32) {
          addSuites(env.map(rv32uf))
          if (cfg.fLen >= 64)
            addSuites(env.map(rv32ud))
          if (cfg.minFLen <= 16)
            addSuites(env.map(rv32uzfh))
        } else {
          addSuite(rv32udBenchmarks)
          addSuites(env.map(rv64uf))
          if (cfg.fLen >= 64)
            addSuites(env.map(rv64ud))
          if (cfg.minFLen <= 16)
            addSuites(env.map(rv64uzfh))
        }
      }
      if (coreParams.useAtomics) {
        if (tileParams.dcache.flatMap(_.scratch).isEmpty)
          addSuites(env.map(if (xlen == 64) rv64ua else rv32ua))
        else
          addSuites(env.map(if (xlen == 64) rv64uaSansLRSC else rv32uaSansLRSC))
      }
      if (coreParams.useCompressed) addSuites(env.map(if (xlen == 64) rv64uc else rv32uc))
      val (rvi, rvu) =
        if (xlen == 64) ((if (vm) rv64i else rv64pi), (if (coreParams.mulDiv.isDefined) rv64u else List(rv64ui)))
        else            ((if (vm) rv32i else rv32pi), (if (coreParams.mulDiv.isDefined) rv32u else List(rv32ui)))

      addSuites(rvi.map(_("p")))
      addSuites(rvu.map(_("p")))
      addSuites((if (vm) List("v") else List()).flatMap(env => rvu.map(_(env))))
      addSuite(benchmarks)
      addSuite(new RegressionTestSuite(if (xlen == 64) rv64RegrTestNames else rv32RegrTestNames))
    }
  }
}

/**
 * Config key of custom test suite.
 */
case object TestSuitesKey extends Field[(Seq[TileParams], TestSuiteHelper, Parameters) => String]((tiles, helper, p) => {
  helper.addGenericTestSuites(tiles)(p)
  // Return an empty string as makefile additional snippets
  ""
})
