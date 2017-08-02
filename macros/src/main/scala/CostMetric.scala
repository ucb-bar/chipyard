// See LICENSE for license details.

package barstools.macros

/**
 * Trait which can calculate the cost of compiling a memory against a certain
 * library memory macro using a cost function.
 */
// TODO: eventually explore compiling a single target memory using multiple
// different kinds of target memory.
trait CostMetric extends Serializable {
  /**
   * Cost function that returns the cost of compiling a memory using a certain
   * macro.
   *
   * @param mem Memory macro to compile (target memory)
   * @param lib Library memory macro to use (library memory)
   * @return The cost of this compile, defined by this cost metric, or None if
   *         it cannot be compiled.
   */
  def cost(mem: Macro, lib: Macro): Option[BigInt]

  /**
   * Helper function to return the map of argments (or an empty map if there are none).
   */
  def commandLineParams(): Map[String, String]

  // We also want this to show up for the class itself.
  def name(): String
}

// Is there a better way to do this? (static method associated to CostMetric)
trait CostMetricCompanion {
  def name(): String

  /** Construct this cost metric from a command line mapping. */
  def construct(m: Map[String, String]): CostMetric
}

// Some default cost functions.

/** Palmer's old metric. */
object PalmerMetric extends CostMetric with CostMetricCompanion {
  override def cost(mem: Macro, lib: Macro): Option[BigInt] = {
    /* Palmer: A quick cost function (that must be kept in sync with
     * memory_cost()) that attempts to avoid compiling unncessary
     * memories.  This is a lower bound on the cost of compiling a
     * memory: it assumes 100% bit-cell utilization when mapping. */
    // val cost = 100 * (mem.depth * mem.width) / (lib.depth * lib.width) +
    //                  (mem.depth * mem.width)
    ???
  }

  override def commandLineParams = Map()
  override def name = "PalmerMetric"
  override def construct(m: Map[String, String]) = PalmerMetric
}

/**
 * An external cost function.
 * Calls the specified path with paths to the JSON MDF representation of the mem
 * and lib macros. The external executable should return a BigInt.
 * None will be returned if the external executable does not return a valid
 * BigInt.
 */
class ExternalMetric(path: String) extends CostMetric {
  import mdf.macrolib.Utils.writeMacroToPath
  import java.io._
  import scala.language.postfixOps // for !! postfix op
  import sys.process._

  override def cost(mem: Macro, lib: Macro): Option[BigInt] = {
    // Create temporary files.
    val memFile = File.createTempFile("_macrocompiler_mem_", ".json")
    val libFile = File.createTempFile("_macrocompiler_lib_", ".json")

    writeMacroToPath(Some(memFile.getAbsolutePath), mem.src)
    writeMacroToPath(Some(libFile.getAbsolutePath), lib.src)

    // !! executes the given command
    val result: String = (s"${path} ${memFile.getAbsolutePath} ${libFile.getAbsolutePath}" !!).trim

    // Remove temporary files.
    memFile.delete()
    libFile.delete()

    try {
      Some(BigInt(result))
    } catch {
      case e: NumberFormatException => None
    }
  }

  override def commandLineParams = Map("path" -> path)
  override def name = ExternalMetric.name
}

object ExternalMetric extends CostMetricCompanion {
  override def name = "ExternalMetric"

  /** Construct this cost metric from a command line mapping. */
  override def construct(m: Map[String, String]) = {
    val pathOption = m.get("path")
    pathOption match {
      case Some(path:String) => new ExternalMetric(path)
      case _ => throw new IllegalArgumentException("ExternalMetric missing option 'path'")
    }
  }
}

/** The current default metric in barstools, re-defined by Donggyu. */
// TODO: write tests for this function to make sure it selects the right things
object NewDefaultMetric extends CostMetric with CostMetricCompanion {
  override def cost(mem: Macro, lib: Macro): Option[BigInt] = {
    val memMask = mem.src.ports map (_.maskGran) find (_.isDefined) map (_.get)
    val libMask = lib.src.ports map (_.maskGran) find (_.isDefined) map (_.get)
    val memWidth = (memMask, libMask) match {
      case (Some(1), Some(1)) | (None, _) => mem.src.width
      case (Some(p), _) => p // assume that the memory consists of smaller chunks
    }
    return Some(
      (((mem.src.depth - 1) / lib.src.depth) + 1) *
      (((memWidth - 1) / lib.src.width) + 1) *
      (lib.src.depth * lib.src.width + 1) // weights on # cells
    )
  }

  override def commandLineParams = Map()
  override def name = "NewDefaultMetric"
  override def construct(m: Map[String, String]) = NewDefaultMetric
}

object MacroCompilerUtil {
  import java.io._
  import java.util.Base64

  // Adapted from https://stackoverflow.com/a/134918

  /** Serialize an arbitrary object to String.
   *  Used to pass structured values through as an annotation. */
  def objToString(o: Serializable): String = {
    val baos: ByteArrayOutputStream = new ByteArrayOutputStream
    val oos: ObjectOutputStream = new ObjectOutputStream(baos)
    oos.writeObject(o)
    oos.close()
    return Base64.getEncoder.encodeToString(baos.toByteArray)
  }

  /** Deserialize an arbitrary object from String. */
  def objFromString(s: String): AnyRef = {
    val data = Base64.getDecoder.decode(s)
    val ois: ObjectInputStream = new ObjectInputStream(new ByteArrayInputStream(data))
    val o = ois.readObject
    ois.close()
    return o
  }
}

object CostMetric {
  /** Define some default metric. */
  val default: CostMetric = NewDefaultMetric

  val costMetricCreators: scala.collection.mutable.Map[String, CostMetricCompanion] = scala.collection.mutable.Map()

  // Register some default metrics
  registerCostMetric(PalmerMetric)
  registerCostMetric(ExternalMetric)
  registerCostMetric(NewDefaultMetric)

  /**
   * Register a cost metric.
   * @param createFuncHelper Companion object to fetch the name and construct
   *                         the metric.
   */
  def registerCostMetric(createFuncHelper: CostMetricCompanion): Unit = {
    costMetricCreators.update(createFuncHelper.name, createFuncHelper)
  }

  /** Select a cost metric from string. */
  def getCostMetric(m: String, params: Map[String, String]): CostMetric = {
    if (m == "default") {
      CostMetric.default
    } else if (!costMetricCreators.contains(m)) {
      throw new IllegalArgumentException("Invalid cost metric " + m)
    } else {
      costMetricCreators.get(m).get.construct(params)
    }
  }
}
