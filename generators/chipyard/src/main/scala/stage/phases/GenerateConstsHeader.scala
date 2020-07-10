// See LICENSE for license details.
// Based on Rocket Chip's stage implementation
//
package chipyard.stage.phases

import chipsalliance.rocketchip.config.Parameters
import scala.collection.mutable.StringBuilder
import firrtl.AnnotationSeq
import firrtl.options.{Phase, PreservesAll, StageOptions, Unserializable, Dependency}
import firrtl.options.Viewer.view
import freechips.rocketchip.stage.RocketChipOptions
import freechips.rocketchip.subsystem.{CacheBlockBytes, ExtMem}
import freechips.rocketchip.util.HasRocketChipStageUtils

class GenerateConstsHeader extends Phase
    with PreservesAll[Phase] with HasRocketChipStageUtils {
  override val prerequisites = Seq(Dependency[freechips.rocketchip.stage.phases.GenerateFirrtlAnnos])

  def define(name: String, value: BigInt): String =
    s"#define $name ${value}L\n"

  def define(name: String, value: Int): String =
    s"#define $name $value\n"

  def define(name: String, value: String): String =
    s"#define $name $value\n"

  override def transform(annotations: AnnotationSeq): AnnotationSeq = {
    val targetDir = view[StageOptions](annotations).targetDir
    val fileName = s"${view[RocketChipOptions](annotations).longName.get}.consts.h"
    val params: Parameters = getConfig(view[RocketChipOptions](annotations).configNames.get).toInstance
    val headerBuilder = new StringBuilder()
    val mem = params(ExtMem).get
    val lineBytes = params(CacheBlockBytes)

    headerBuilder
      .append(define("NUM_CHANNELS", mem.nMemoryChannels))
      .append(define("MEM_SIZE", mem.master.size))
      .append(define("WORD_BYTES", mem.master.beatBytes))
      .append(define("LINE_BYTES", lineBytes))
      .append(define("ID_BITS", mem.master.idBits))

    writeOutputFile(targetDir, fileName, headerBuilder.toString)
    annotations
  }
}
